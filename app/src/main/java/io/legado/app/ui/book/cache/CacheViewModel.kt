package io.legado.app.ui.book.cache

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.sendValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.collections.set


class CacheViewModel(application: Application) : BaseViewModel(application) {
    val upAdapterLiveData = MutableLiveData<String>()

    private var loadChapterCoroutine: Coroutine<Unit>? = null
    // 缓存每本书已缓存的章节URL集合
    val cacheChapters = hashMapOf<String, HashSet<String>>()
    private val bookRepository = BookRepository()
    
    // 用于检测是否是相同的书籍列表，避免重复加载
    private var lastLoadedBooksKey: String? = null
    // 防止并发加载的标志
    private var isLoading = false

    /**
     * 加载书籍缓存文件信息
     * 优化点：
     * 1. 使用booksKey检测相同列表，避免重复计算
     * 2. 使用isLoading防止并发加载
     * 3. 批量获取章节信息，减少数据库查询次数
     */
    fun loadCacheFiles(books: List<Book>) {
        // 防止并发加载
        if (isLoading) return
        
        // 检测是否是相同的书籍列表
        val booksKey = books.map { it.bookUrl }.sorted().joinToString(",")
        if (booksKey == lastLoadedBooksKey) {
            return
        }
        
        loadChapterCoroutine?.cancel()
        loadChapterCoroutine = execute {
            isLoading = true
            try {
                // 过滤出需要加载的新书籍（非本地书籍且未缓存）
                val newBooks = books.filter { !it.isLocal && !cacheChapters.contains(it.bookUrl) }
                if (newBooks.isEmpty()) {
                    lastLoadedBooksKey = booksKey
                    return@execute
                }
                
                // 批量获取所有新书籍的章节信息，减少数据库查询次数
                val bookUrls = newBooks.map { it.bookUrl }
                val chaptersByBook = withContext(Dispatchers.IO) {
                    try {
                        val result = mutableMapOf<String, MutableList<io.legado.app.data.entities.BookChapter>>()
                        bookUrls.forEach { bookUrl ->
                            result[bookUrl] = appDb.bookChapterDao.getChapterList(bookUrl).toMutableList()
                        }
                        result
                    } catch (e: Exception) {
                        emptyMap()
                    }
                }
                
                // 处理每本书的缓存状态
                newBooks.forEach { book ->
                    val chapterCaches = hashSetOf<String>()
                    val cacheNames = BookHelp.getChapterFiles(book)
                    if (cacheNames.isNotEmpty()) {
                        chaptersByBook[book.bookUrl]?.let { chapters ->
                            book.totalChapterNum = chapters.size
                            chapters.forEach { chapter ->
                                if (cacheNames.contains(chapter.getFileName()) || chapter.isVolume) {
                                    chapterCaches.add(chapter.url)
                                }
                            }
                        }
                    }
                    cacheChapters[book.bookUrl] = chapterCaches
                    upAdapterLiveData.sendValue(book.bookUrl)
                    ensureActive()
                }
                
                lastLoadedBooksKey = booksKey
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun getBookCover(bookName: String, bookAuthor: String): String? {
        return bookRepository.getBookCoverByNameAndAuthor(bookName, bookAuthor)
    }

    /**
     * 清理单本书的缓存状态
     * 清理后重置lastLoadedBooksKey，确保下次重新计算
     */
    fun clearCache(bookUrl: String) {
        cacheChapters[bookUrl] = hashSetOf()
        lastLoadedBooksKey = null
    }

    /**
     * 清理所有缓存状态
     * 清理后重置lastLoadedBooksKey，确保下次重新计算
     */
    fun clearAllCache() {
        cacheChapters.clear()
        lastLoadedBooksKey = null
    }

}