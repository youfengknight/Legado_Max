package io.legado.app.help

import android.net.Uri
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.Restore
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isJson
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.removePref
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.File

/**webDav初始化会访问网络,不要放到主线程
 * WebDav云同步管理类
 * 
 * 提供WebDav协议的云端备份和同步功能，包括：
 * - 备份文件上传/下载
 * - 书籍进度同步
 * - 背景图片同步
 * 
 * WebDav配置：
 * - URL: WebDav服务器地址（默认为坚果云）
 * - 账号: WebDav账号
 * - 密码: WebDav密码
 * - 目录: 备份存储目录
 * - 设备名: 用于区分不同设备的备份
 * 
 * 目录结构：
 * - rootWebDavUrl/: 根目录
 *   - bookProgress/: 书籍进度目录
 *   - books/: 书籍导出目录
 *   - background/: 背景图片目录
 *   - backup*.zip: 备份文件
 * 
 * 注意：WebDav初始化会访问网络，不要在主线程调用
 */
object AppWebDav {

    /** 默认WebDav服务器地址（坚果云） */
    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"

    /** 书籍进度同步URL */
    private val bookProgressUrl get() = "${rootWebDavUrl}bookProgress/"

    /** 书籍导出URL */
    private val exportsWebDavUrl get() = "${rootWebDavUrl}books/"

    /** 背景图片URL */
    private val bgWebDavUrl get() = "${rootWebDavUrl}background/"

    /** WebDav授权信息 */
    var authorization: Authorization? = null
        private set

    /** 默认书籍WebDav客户端 */
    var defaultBookWebDav: RemoteBookWebDav? = null

    /** WebDav是否配置成功 */
    val isOk get() = authorization != null

    /** 是否为坚果云服务 */
    val isJianGuoYun get() = rootWebDavUrl.startsWith(defaultWebDavUrl, true)

    init {
        runBlocking {
            upConfig()
        }
    }

    /**
     * WebDav根目录URL
     * 由配置URL和目录名拼接而成
     */
    private val rootWebDavUrl: String
        get() {
            val configUrl = appCtx.getPrefString(PreferKey.webDavUrl)?.trim()
            var url = if (configUrl.isNullOrEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            AppConfig.webDavDir?.trim()?.let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    /**
     * 更新WebDav配置
     * 读取SharedPreferences中的配置并初始化WebDav连接
     * 
     * 初始化时会创建必要的目录：
     * - 根目录
     * - 书籍进度目录
     * - 书籍导出目录
     * - 背景图片目录
     */
    suspend fun upConfig() {
        kotlin.runCatching {
            authorization = null
            defaultBookWebDav = null
            val account = appCtx.getPrefString(PreferKey.webDavAccount)
            val password = appCtx.getPrefString(PreferKey.webDavPassword)
            if (!account.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val mAuthorization = Authorization(account, password)
                checkAuthorization(mAuthorization)
                WebDav(rootWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bookProgressUrl, mAuthorization).makeAsDir()
                WebDav(exportsWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bgWebDavUrl, mAuthorization).makeAsDir()
                val rootBooksUrl = "${rootWebDavUrl}books/"
                defaultBookWebDav = RemoteBookWebDav(rootBooksUrl, mAuthorization)
                authorization = mAuthorization
            }
        }
    }

    /**
     * 检查WebDav授权是否有效
     * 
     * @param authorization 授权信息
     * @throws WebDavException 授权失败时抛出异常
     */
    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(authorization: Authorization) {
        if (!WebDav(rootWebDavUrl, authorization).check()) {
            appCtx.removePref(PreferKey.webDavPassword)
            appCtx.toastOnUi(R.string.webdav_application_authorization_error)
            throw WebDavException(appCtx.getString(R.string.webdav_application_authorization_error))
        }
    }

    // ==================== 备份文件管理 ====================

    /**
     * 获取云端备份文件名列表
     * 
     * @return 备份文件名列表，按名称倒序排列
     * @throws Exception WebDav未配置或网络错误时抛出异常
     */
    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val names = arrayListOf<String>()
        authorization?.let {
            var files = WebDav(rootWebDavUrl, it).listFiles()
            files = files.sortedWith { o1, o2 ->
                AlphanumComparator.compare(o1.displayName, o2.displayName)
            }.reversed()
            files.forEach { webDav ->
                val name = webDav.displayName
                if (name.startsWith("backup")) {
                    names.add(name)
                }
            }
        } ?: throw NoStackTraceException("webDav没有配置")
        return names
    }

    /**
     * 从WebDav恢复备份
     * 
     * 执行步骤：
     * 1. 下载备份文件
     * 2. 解压到临时目录
     * 3. 执行恢复逻辑
     * 
     * @param name 备份文件名
     * @throws WebDavException 下载或恢复失败时抛出异常
     */
    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        authorization?.let {
            val webDav = WebDav(rootWebDavUrl + name, it)
            webDav.downloadTo(Backup.zipFilePath, true)
            FileUtils.delete(Backup.backupPath)
            ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
            Restore.restoreLocked(Backup.backupPath)
        }
    }

    /**
     * 检查WebDav上是否存在指定备份文件
     * 
     * @param backUpName 备份文件名
     * @return true表示存在，false表示不存在或WebDav未配置
     */
    suspend fun hasBackUp(backUpName: String): Boolean {
        authorization?.let {
            val url = "$rootWebDavUrl${backUpName}"
            return WebDav(url, it).exists()
        }
        return false
    }

    /**
     * 获取最新的备份文件信息
     * 
     * @return Result包装的最新备份文件信息，失败时返回异常
     */
    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            authorization?.let {
                var lastBackupFile: WebDavFile? = null
                WebDav(rootWebDavUrl, it).listFiles().reversed().forEach { webDavFile ->
                    if (webDavFile.displayName.startsWith("backup")) {
                        if (lastBackupFile == null
                            || webDavFile.lastModify > lastBackupFile.lastModify
                        ) {
                            lastBackupFile = webDavFile
                        }
                    }
                }
                lastBackupFile
            }
        }
    }

    /**
     * 上传备份文件到WebDav
     * 
     * @param fileName 备份文件名
     * @throws Exception 上传失败时抛出异常
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        authorization?.let {
            val putUrl = "$rootWebDavUrl$fileName"
            WebDav(putUrl, it).upload(Backup.zipFilePath)
        }
    }

    // ==================== 背景图片同步 ====================

    /**
     * 获取云端所有背景图片文件列表
     * 
     * @return Result包装的文件列表
     */
    private suspend fun getAllBgWebDavFiles(): Result<List<WebDavFile>> {
        return kotlin.runCatching {
            if (!NetworkUtils.isAvailable())
                throw NoStackTraceException("网络未连接")
            authorization.let {
                it ?: throw NoStackTraceException("webDav未配置")
                WebDav(bgWebDavUrl, it).listFiles()
            }
        }
    }

    /**
     * 上传背景图片到WebDav
     * 只上传云端不存在的图片
     * 
     * @param files 本地背景图片文件数组
     */
    suspend fun upBgs(files: Array<File>) {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
        files.forEach {
            if (!bgWebDavFiles.contains(it.name) && it.exists()) {
                WebDav("$bgWebDavUrl${it.name}", authorization)
                    .upload(it)
            }
        }
    }

    /**
     * 从WebDav下载背景图片
     * 目前未实现完整逻辑
     */
    suspend fun downBgs() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
    }

    // ==================== 书籍导出 ====================

    /**
     * 导出字节数据到WebDav
     * 
     * @param byteArray 要导出的字节数组
     * @param fileName 导出文件名
     */
    @Suppress("unused")
    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(byteArray, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    /**
     * 导出URI指向的文件到WebDav
     * 
     * @param uri 文件URI
     * @param fileName 导出文件名
     */
    suspend fun exportWebDav(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(uri, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    // ==================== 书籍进度同步 ====================

    /**
     * 上传书籍阅读进度到WebDav
     * 
     * @param book 书籍对象
     * @param toast 是否显示Toast提示
     * @param onSuccess 成功回调
     */
    suspend fun uploadBookProgress(
        book: Book,
        toast: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        val authorization = authorization ?: return
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        try {
            val bookProgress = BookProgress(book)
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(book.name, book.author)
            WebDav(url, authorization).upload(json.toByteArray(), "application/json")
            book.syncTime = System.currentTimeMillis()
            onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
        }
    }

    /**
     * 上传书籍进度对象到WebDav
     * 
     * @param bookProgress 书籍进度对象
     * @param onSuccess 成功回调
     */
    suspend fun uploadBookProgress(bookProgress: BookProgress, onSuccess: (() -> Unit)? = null) {
        try {
            val authorization = authorization ?: return
            if (!AppConfig.syncBookProgress) return
            if (!NetworkUtils.isAvailable()) return
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(bookProgress.name, bookProgress.author)
            WebDav(url, authorization).upload(json.toByteArray(), "application/json")
            onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e)
        }
    }

    /**
     * 获取书籍进度URL
     * 
     * @param name 书籍名称
     * @param author 书籍作者
     * @return WebDav URL
     */
    private fun getProgressUrl(name: String, author: String): String {
        return bookProgressUrl + getProgressFileName(name, author)
    }

    /**
     * 生成进度文件名
     * 格式：{书名}_{作者}.json
     * 
     * @param name 书籍名称
     * @param author 书籍作者
     * @return 文件名
     */
    private fun getProgressFileName(name: String, author: String): String {
        return UrlUtil.replaceReservedChar("${name}_${author}".normalizeFileName()) + ".json"
    }

    /**
     * 从WebDav获取书籍进度
     * 
     * @param book 书籍对象
     * @return 书籍进度对象，失败返回null
     */
    suspend fun getBookProgress(book: Book): BookProgress? {
        val url = getProgressUrl(book.name, book.author)
        kotlin.runCatching {
            val authorization = authorization ?: return null
            WebDav(url, authorization).download().let { byteArray ->
                val json = String(byteArray)
                if (json.isJson()) {
                    return GSON.fromJsonObject<BookProgress>(json).getOrNull()
                }
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("获取书籍进度失败\n${it.localizedMessage}", it)
        }
        return null
    }

    /**
     * 下载所有书籍进度并同步到本地
     * 
     * 同步规则：
     * - 只同步云端更新时间大于本地同步时间的进度
     * - 只更新进度更靠后的记录
     */
    suspend fun downloadAllBookProgress() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bookProgressFiles = WebDav(bookProgressUrl, authorization).listFiles()
        val map = hashMapOf<String, WebDavFile>()
        bookProgressFiles.forEach {
            map[it.displayName] = it
        }
        appDb.bookDao.all.forEach { book ->
            val progressFileName = getProgressFileName(book.name, book.author)
            val webDavFile = map[progressFileName]
            webDavFile ?: return
            if (webDavFile.lastModify <= book.syncTime) {
                //本地同步时间大于上传时间不用同步
                return
            }
            getBookProgress(book)?.let { bookProgress ->
                if (bookProgress.durChapterIndex > book.durChapterIndex
                    || (bookProgress.durChapterIndex == book.durChapterIndex
                            && bookProgress.durChapterPos > book.durChapterPos)
                ) {
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    book.syncTime = System.currentTimeMillis()
                    appDb.bookDao.update(book)
                }
            }
        }
    }

}
