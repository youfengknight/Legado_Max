package io.legado.app.ui.book.explore

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentHashMap


@OptIn(ExperimentalCoroutinesApi::class)
class ExploreShowViewModel(application: Application) : BaseViewModel(application) {
    companion object {
        private val pageQueryRegex = Regex("""([?&]page=)(\d+)""", RegexOption.IGNORE_CASE)
    }

    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val upAdapterLiveData = MutableLiveData<String>()
    val booksData = MutableLiveData<List<SearchBook>>()
    val addBooksData = MutableLiveData<List<SearchBook>>()
    val errorLiveData = MutableLiveData<String>()
    val errorTopLiveData = MutableLiveData<String>()
    val pageLiveData = MutableLiveData<Int>()
    private var bookSource: BookSource? = null
    private var exploreUrl: String? = null
    private var page = 1
    private var books = linkedSetOf<SearchBook>()

    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = arrayListOf<String>()
                books.filterNot { it.isNotShelf }
                    .forEach {
                        keys.add("${it.name}-${it.author}")
                        keys.add(it.name)
                        keys.add(it.bookUrl)
                    }
                keys
            }.catch {
                AppLog.put("发现列表界面获取书籍数据失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    fun initData(intent: Intent) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            exploreUrl = intent.getStringExtra("exploreUrl")
            page = parsePageFromUrl(exploreUrl)
            if (bookSource == null && sourceUrl != null) {
                bookSource = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            pageLiveData.postValue(page)
            explore()
        }
    }

    /**
     * 上滑触发的增量更新
     */
    fun explore(page: Int) {
        val source = bookSource
        val url = buildExploreUrl(page)
        if (source == null || url == null) return
        WebBook.exploreBook(viewModelScope, source, url, page)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                val newBooks = linkedSetOf<SearchBook>()
                newBooks.addAll(searchBooks)
                newBooks.addAll(books)
                books = newBooks
                addBooksData.postValue(searchBooks)
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(page)
            }.onError {
                it.printOnDebug()
                errorTopLiveData.postValue(it.stackTraceStr)
            }
    }
    fun skipPage(page: Int) {
        if (page > 0) {
            books.clear()
            this.page = page
            pageLiveData.postValue(page)
        }
    }

    fun explore() {
        val source = bookSource
        val requestPage = page
        val url = buildExploreUrl(requestPage)
        if (source == null || url == null) return
        WebBook.exploreBook(viewModelScope, source, url, requestPage)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                books.addAll(searchBooks)
                booksData.postValue(books.toList())
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(requestPage)
                page = requestPage + 1
            }.onError {
                it.printOnDebug()
                errorLiveData.postValue(it.stackTraceStr)
            }
    }

    private fun parsePageFromUrl(url: String?): Int {
        val pageValue = url?.let {
            pageQueryRegex.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull()
        }
        return pageValue?.takeIf { it > 0 } ?: 1
    }

    private fun buildExploreUrl(page: Int): String? {
        val safePage = page.coerceAtLeast(1)
        val currentUrl = exploreUrl ?: return null
        val updatedUrl = pageQueryRegex.replace(currentUrl) {
            "${it.groupValues[1]}$safePage"
        }
        exploreUrl = updatedUrl
        return updatedUrl
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return bookshelf.contains(key) || bookshelf.contains(bookUrl)
    }

}
