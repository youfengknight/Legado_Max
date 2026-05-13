package io.legado.app.model.webBook

import android.text.TextUtils
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isWebFile
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.utils.DebugLog
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils.wordCountFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx


/**
 * 获取书籍详情
 * 书籍详情解析器
 *
 * 负责解析书籍的详情页信息，包括：
 * - 书名、作者
 * - 分类、字数
 * - 最新章节、简介
 * - 封面链接
 * - 目录链接/下载链接
 *
 * 支持详情页初始化规则（init），用于预处理页面结构。
 *
 * @see WebBook.getBookInfo 网络请求入口
 * @see BookInfoRule 详情规则定义
 */
object BookInfo {

    /**
     * 解析书籍详情
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含bookUrl）
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param canReName 是否允许重命名书名和作者
     * @throws NoStackTraceException 当内容为空时抛出
     */
    @Throws(Exception::class)
    suspend fun analyzeBookInfo(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        canReName: Boolean,
    ) {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 20)
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeBookInfo(book, body, analyzeRule, bookSource, baseUrl, redirectUrl, canReName)
    }

    /**
     * 解析书籍详情（内部方法）
     *
     * 使用已有的AnalyzeRule对象解析详情页，避免重复创建。
     * 支持详情页初始化规则（init），用于预处理页面结构。
     *
     * @param book 书籍对象
     * @param body 页面内容
     * @param analyzeRule 规则解析器
     * @param bookSource 书源
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param canReName 是否允许重命名书名和作者
     */
    suspend fun analyzeBookInfo(
        book: Book,
        body: String,
        analyzeRule: AnalyzeRule,
        bookSource: BookSource,
        baseUrl: String,
        redirectUrl: String,
        canReName: Boolean,
    ) {
        val infoRule = bookSource.getBookInfoRule()
        infoRule.init?.let {
            if (it.isNotBlank()) {
                currentCoroutineContext().ensureActive()
                Debug.log(bookSource.bookSourceUrl, "≡执行详情页初始化规则")
                analyzeRule.setContent(analyzeRule.getElement(it))
            }
        }
        val mCanReName = canReName && !infoRule.canReName.isNullOrBlank()
        
        FlowLogRecorder.logExtract(
            source = bookSource,
            message = "开始提取书籍信息字段"
        )
        
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取书名")
        BookHelp.formatBookName(analyzeRule.getString(infoRule.name)).let {
            if (it.isNotEmpty() && (mCanReName || book.name.isEmpty())) {
                book.name = it
            }
            Debug.log(bookSource.bookSourceUrl, "└${it}")
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取书名",
                rule = infoRule.name,
                result = it
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取作者")
        BookHelp.formatBookAuthor(analyzeRule.getString(infoRule.author)).let {
            if (it.isNotEmpty() && (mCanReName || book.author.isEmpty())) {
                book.author = it
            }
            Debug.log(bookSource.bookSourceUrl, "└${it}")
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取作者",
                rule = infoRule.author,
                result = it
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取分类")
        try {
            analyzeRule.getStringList(infoRule.kind)
                ?.joinToString(",")
                ?.let {
                    if (it.isNotEmpty()) book.kind = it
                    Debug.log(bookSource.bookSourceUrl, "└${it}")
                    
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取分类",
                        rule = infoRule.kind,
                        result = it
                    )
                } ?: Debug.log(bookSource.bookSourceUrl, "└")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取分类出错", e)
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取分类",
                rule = infoRule.kind,
                error = e
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取字数")
        try {
            wordCountFormat(analyzeRule.getString(infoRule.wordCount)).let {
                if (it.isNotEmpty()) book.wordCount = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
                
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取字数",
                    rule = infoRule.wordCount,
                    result = it
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取字数出错", e)
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取字数",
                rule = infoRule.wordCount,
                error = e
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取最新章节")
        try {
            analyzeRule.getString(infoRule.lastChapter).let {
                if (it.isNotEmpty()) book.latestChapterTitle = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
                
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取最新章节",
                    rule = infoRule.lastChapter,
                    result = it
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取最新章节出错", e)
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取最新章节",
                rule = infoRule.lastChapter,
                error = e
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取简介")
        try {
            val intro = analyzeRule.getString(infoRule.intro)
            val introTrimS = intro.trimStart()
            if (introTrimS.startsWith("<usehtml>") || introTrimS.startsWith("<md>") || introTrimS.startsWith("<useweb>")) {
                book.intro = introTrimS
                Debug.log(bookSource.bookSourceUrl, "└${introTrimS}")
                
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取简介",
                    rule = infoRule.intro,
                    result = introTrimS
                )
            } else {
                HtmlFormatter.format(intro).let {
                    if (it.isNotEmpty()) book.intro = it
                    Debug.log(bookSource.bookSourceUrl, "└${it}")
                    
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取简介",
                        rule = infoRule.intro,
                        result = it
                    )
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取简介出错", e)
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取简介",
                rule = infoRule.intro,
                error = e
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取封面链接")
        try {
            analyzeRule.getString(infoRule.coverUrl).let {
                if (it.isNotEmpty()) {
                    book.coverUrl =
                        NetworkUtils.getAbsoluteURL(redirectUrl, it)
                }
                Debug.log(bookSource.bookSourceUrl, "└${it}")
                
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取封面链接",
                    rule = infoRule.coverUrl,
                    result = book.coverUrl
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取封面出错", e)
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取封面链接",
                rule = infoRule.coverUrl,
                error = e
            )
        }
        currentCoroutineContext().ensureActive()
        if (!book.isWebFile) {
            Debug.log(bookSource.bookSourceUrl, "┌获取目录链接")
            book.tocUrl = analyzeRule.getString(infoRule.tocUrl, isUrl = true)
            if (book.tocUrl.isEmpty()) book.tocUrl = baseUrl
            if (book.tocUrl == baseUrl) {
                book.tocHtml = body
            }
            Debug.log(bookSource.bookSourceUrl, "└${book.tocUrl}")
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "提取目录链接",
                rule = infoRule.tocUrl,
                result = book.tocUrl
            )
        } else {
            Debug.log(bookSource.bookSourceUrl, "┌获取文件下载链接")
            book.downloadUrls = analyzeRule.getStringList(infoRule.downloadUrls, isUrl = true)
            if (book.downloadUrls.isNullOrEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "└")
                throw NoStackTraceException("下载链接为空")
            } else {
                Debug.log(
                    bookSource.bookSourceUrl,
                    "└" + TextUtils.join("，\n", book.downloadUrls!!)
                )
                
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取文件下载链接",
                    rule = infoRule.downloadUrls,
                    result = book.downloadUrls?.joinToString("\n")
                )
            }
        }
    }

}