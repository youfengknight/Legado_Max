package io.legado.app.help.storage

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.ACache
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

// ### 数据层
// 1. StorageCalculator.kt

// - 作用 ：缓存计算器，负责计算各类缓存大小和执行清理操作
// - 主要功能 ：
//   - 计算7种缓存类型的大小（书籍、Epub、临时文件、TTS、ACache、数据库、日志）
//   - 计算可展开缓存的详情列表（如每本书的缓存、每个TTS引擎的缓存）
//   - 执行缓存清理操作（支持整体清理和单独清理）

data class CacheDetail(
    val id: String,
    val name: String,
    val meta: String,
    val size: Long,
    val formattedSize: String
)

object StorageCalculator {

    // 缓存有效期：30秒内复用计算结果，避免频繁遍历目录
    private const val CACHE_EXPIRE_MS = 30_000L
    
    // 使用 AtomicLong 保证线程安全的缓存时间戳
    private var lastCacheTime = AtomicLong(0L)
    
    // 各类缓存大小的内存缓存，-1 表示未计算
    private var cachedBookSize = AtomicLong(-1L)
    private var cachedEpubSize = AtomicLong(-1L)
    private var cachedTempSize = AtomicLong(-1L)
    private var cachedTtsSize = AtomicLong(-1L)
    private var cachedACacheSize = AtomicLong(-1L)
    private var cachedDbSize = AtomicLong(-1L)
    private var cachedLogSize = AtomicLong(-1L)
    
    // 缓存数量统计
    private var cachedBookCount = -1
    private var cachedTtsCount = -1
    private var cachedACacheCount = -1
    
    // 书籍文件夹名到Book对象的映射缓存，避免每次都查询数据库
    private var bookFolderMap: Map<String, Book>? = null
    private var bookFolderMapTime = 0L
    
    /**
     * 失效所有缓存，在清理缓存后调用
     */
    fun invalidateCache() {
        lastCacheTime.set(0L)
        cachedBookSize.set(-1L)
        cachedEpubSize.set(-1L)
        cachedTempSize.set(-1L)
        cachedTtsSize.set(-1L)
        cachedACacheSize.set(-1L)
        cachedDbSize.set(-1L)
        cachedLogSize.set(-1L)
        cachedBookCount = -1
        cachedTtsCount = -1
        cachedACacheCount = -1
        bookFolderMap = null
        bookFolderMapTime = 0L
    }
    
    /**
     * 检查缓存是否在有效期内
     */
    private fun isCacheValid(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastCacheTime.get() < CACHE_EXPIRE_MS
    }
    
    /**
     * 更新缓存时间戳
     */
    private fun markCacheTime() {
        lastCacheTime.set(System.currentTimeMillis())
    }

    /**
     * 计算书籍缓存总大小
     * 优先使用内存缓存，避免重复遍历目录
     */
    suspend fun calculateBookCacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedBookSize.get() >= 0) {
            return@withContext cachedBookSize.get()
        }
        val cacheDir = appCtx.externalFiles.getFile("book_cache")
        val size = calculateDirSizeFast(cacheDir)
        cachedBookSize.set(size)
        markCacheTime()
        size
    }

    /**
     * 计算每本书的缓存详情
     * 使用预构建的Map索引查找书籍，避免O(n)遍历
     */
    suspend fun calculateBookCacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val cacheDir = appCtx.externalFiles.getFile("book_cache")
        val details = mutableListOf<CacheDetail>()
        // 使用Map索引，O(1)查找替代O(n)遍历
        val folderMap = getBookFolderMap()
        
        cacheDir.listFiles()?.forEach { bookDir ->
            if (bookDir.isDirectory) {
                val book = folderMap[bookDir.name]
                val size = calculateDirSizeFast(bookDir)
                if (size > 0) {
                    val chapterCount = countCachedChapters(bookDir)
                    val name = book?.name ?: bookDir.name
                    val meta = if (book != null) {
                        "${chapterCount}章 · 最后阅读: ${formatLastRead(book)}"
                    } else {
                        "${chapterCount}章"
                    }
                    details.add(CacheDetail(
                        id = book?.bookUrl ?: bookDir.name,
                        name = name,
                        meta = meta,
                        size = size,
                        formattedSize = ConvertUtils.formatFileSize(size)
                    ))
                }
            }
        }
        
        details.sortedByDescending { it.size }
    }

    /**
     * 获取书籍文件夹名到Book对象的映射
     * 使用缓存避免每次都查询数据库全表
     */
    private fun getBookFolderMap(): Map<String, Book> {
        val now = System.currentTimeMillis()
        if (bookFolderMap != null && now - bookFolderMapTime < CACHE_EXPIRE_MS) {
            return bookFolderMap!!
        }
        // 一次性构建Map索引，后续查找为O(1)
        val map = try {
            appDb.bookDao.all.associateBy { it.getFolderName() }
        } catch (e: Exception) {
            emptyMap()
        }
        bookFolderMap = map
        bookFolderMapTime = now
        return map
    }

    private fun countCachedChapters(bookDir: File): Int {
        return bookDir.listFiles()?.count { it.isFile && it.extension == "txt" } ?: 0
    }

    private fun formatLastRead(book: Book): String {
        val time = book.durChapterTime
        if (time == 0L) return "未知"
        val now = System.currentTimeMillis()
        val diff = now - time
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> {
                val sdf = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                sdf.format(java.util.Date(time))
            }
        }
    }

    /**
     * 清理书籍缓存
     * 清理后失效缓存，确保下次计算获取最新值
     */
    fun clearBookCache(bookUrl: String? = null) {
        invalidateCache()
        if (bookUrl == null) {
            BookHelp.clearCache()
        } else {
            val book = appDb.bookDao.getBook(bookUrl) ?: return
            BookHelp.clearCache(book)
        }
    }

    suspend fun calculateTtsCacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedTtsSize.get() >= 0) {
            return@withContext cachedTtsSize.get()
        }
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        val size = calculateDirSizeFast(ttsDir)
        cachedTtsSize.set(size)
        markCacheTime()
        size
    }

    suspend fun calculateTtsCacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        val details = mutableListOf<CacheDetail>()
        
        ttsDir.listFiles()?.forEach { engineDir ->
            if (engineDir.isDirectory) {
                val sizeAndLastModified = calculateDirSizeAndLastModified(engineDir)
                if (sizeAndLastModified.first > 0) {
                    details.add(CacheDetail(
                        id = engineDir.name,
                        name = getTtsEngineName(engineDir.name),
                        meta = "最后使用: ${formatLastModified(sizeAndLastModified.second)}",
                        size = sizeAndLastModified.first,
                        formattedSize = ConvertUtils.formatFileSize(sizeAndLastModified.first)
                    ))
                }
            }
        }
        
        details.sortedByDescending { it.size }
    }

    private fun getTtsEngineName(engineId: String): String {
        return when (engineId) {
            "baidu" -> "百度TTS"
            "xunfei" -> "讯飞TTS"
            "azure" -> "Azure TTS"
            "google" -> "Google TTS"
            else -> engineId
        }
    }

    private fun formatLastModified(lastModified: Long): String {
        if (lastModified == 0L) return "未知"
        val now = System.currentTimeMillis()
        val diff = now - lastModified
        return when {
            diff < 60 * 60 * 1000 -> "刚刚"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> "${diff / (24 * 60 * 60 * 1000)}天前"
        }
    }

    fun clearTtsCache(engineId: String? = null) {
        invalidateCache()
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        if (engineId == null) {
            FileUtils.delete(ttsDir.absolutePath)
        } else {
            FileUtils.delete(ttsDir.getFile(engineId).absolutePath)
        }
    }

    suspend fun calculateACacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedACacheSize.get() >= 0) {
            return@withContext cachedACacheSize.get()
        }
        val aCacheDir = File(appCtx.cacheDir, "ACache")
        val size = calculateDirSizeFast(aCacheDir)
        cachedACacheSize.set(size)
        markCacheTime()
        size
    }

    /**
     * 计算ACache详情
     * 优化：只遍历一次目录，同时计算所有前缀的统计信息
     */
    suspend fun calculateACacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val aCacheDir = File(appCtx.cacheDir, "ACache")
        if (!aCacheDir.exists()) return@withContext emptyList()
        
        val prefixes = listOf(
            "v_" to "书源变量缓存",
            "userInfo_" to "用户信息缓存",
            "loginHeader_" to "登录Header缓存",
            "sourceVariable_" to "书源扩展缓存",
            "infoMap_" to "信息映射缓存"
        )
        
        // 初始化各前缀的统计累加器
        val prefixStats = mutableMapOf<String, Pair<Long, Int>>()
        prefixes.forEach { (prefix, _) ->
            prefixStats[prefix] = Pair(0L, 0)
        }
        
        // 单次遍历统计所有前缀，避免每个前缀都遍历一次目录
        aCacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                for ((prefix, _) in prefixes) {
                    if (file.name.startsWith(prefix)) {
                        val current = prefixStats[prefix]!!
                        prefixStats[prefix] = Pair(current.first + file.length(), current.second + 1)
                        break
                    }
                }
            }
        }
        
        val details = mutableListOf<CacheDetail>()
        prefixes.forEach { (prefix, name) ->
            val (size, count) = prefixStats[prefix] ?: Pair(0L, 0)
            if (count > 0) {
                details.add(CacheDetail(
                    id = prefix,
                    name = name,
                    meta = "${prefix}前缀 · ${count}条",
                    size = size,
                    formattedSize = ConvertUtils.formatFileSize(size)
                ))
            }
        }
        
        details.sortedByDescending { it.size }
    }

    fun clearACache(prefix: String? = null) {
        invalidateCache()
        if (prefix == null) {
            ACache.get().clear()
        } else {
            val aCacheDir = File(appCtx.cacheDir, "ACache")
            aCacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(prefix)) {
                    file.delete()
                }
            }
        }
    }

    suspend fun calculateDbCacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedDbSize.get() >= 0) {
            return@withContext cachedDbSize.get()
        }
        val size = try {
            val dbFile = appCtx.getDatabasePath("legado.db")
            if (dbFile.exists()) {
                dbFile.length()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
        cachedDbSize.set(size)
        markCacheTime()
        size
    }

    suspend fun calculateDbCacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val details = mutableListOf<CacheDetail>()
        
        try {
            val dbFile = appCtx.getDatabasePath("legado.db")
            if (dbFile.exists()) {
                val walFile = File(dbFile.parent, "legado.db-wal")
                val shmFile = File(dbFile.parent, "legado.db-shm")
                
                val mainSize = dbFile.length()
                val walSize = if (walFile.exists()) walFile.length() else 0L
                val shmSize = if (shmFile.exists()) shmFile.length() else 0L
                
                details.add(CacheDetail(
                    id = "main",
                    name = "主数据库",
                    meta = "legado.db",
                    size = mainSize,
                    formattedSize = ConvertUtils.formatFileSize(mainSize)
                ))
                
                if (walSize > 0) {
                    details.add(CacheDetail(
                        id = "wal",
                        name = "WAL日志",
                        meta = "legado.db-wal",
                        size = walSize,
                        formattedSize = ConvertUtils.formatFileSize(walSize)
                    ))
                }
                
                if (shmSize > 0) {
                    details.add(CacheDetail(
                        id = "shm",
                        name = "共享内存",
                        meta = "legado.db-shm",
                        size = shmSize,
                        formattedSize = ConvertUtils.formatFileSize(shmSize)
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        details.sortedByDescending { it.size }
    }

    suspend fun clearDbCache() = withContext(Dispatchers.IO) {
        invalidateCache()
        try {
            appDb.cacheDao.deleteAllRuntimeSourceCaches()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun calculateEpubCacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedEpubSize.get() >= 0) {
            return@withContext cachedEpubSize.get()
        }
        val epubDir = appCtx.externalFiles.getFile("epub")
        val size = calculateDirSizeFast(epubDir)
        cachedEpubSize.set(size)
        markCacheTime()
        size
    }

    fun clearEpubCache() {
        invalidateCache()
        FileUtils.delete(appCtx.externalFiles.getFile("epub").absolutePath)
    }

    suspend fun calculateTempCacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedTempSize.get() >= 0) {
            return@withContext cachedTempSize.get()
        }
        val size = calculateDirSizeFast(appCtx.externalCache)
        cachedTempSize.set(size)
        markCacheTime()
        size
    }

    fun clearTempCache() {
        invalidateCache()
        appCtx.externalCache.listFiles()?.forEach { 
            if (it.isDirectory) {
                FileUtils.delete(it.absolutePath)
            } else {
                it.delete()
            }
        }
    }

    suspend fun calculateLogCacheSize(): Long = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedLogSize.get() >= 0) {
            return@withContext cachedLogSize.get()
        }
        val logDir = appCtx.externalCache.getFile("log")
        val size = calculateDirSizeFast(logDir)
        cachedLogSize.set(size)
        markCacheTime()
        size
    }

    fun clearLogCache() {
        invalidateCache()
        FileUtils.delete(appCtx.externalCache.getFile("log").absolutePath)
    }

    /**
     * 快速计算目录大小
     * 使用栈实现的迭代遍历，避免walkTopDown()的递归调用开销
     * 性能优于walkTopDown()，尤其对深层目录结构
     */
    private fun calculateDirSizeFast(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            val stack = java.util.ArrayDeque<File>()
            stack.push(dir)
            while (stack.isNotEmpty()) {
                val current = stack.pop()
                current.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        size += file.length()
                    } else if (file.isDirectory) {
                        stack.push(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    /**
     * 计算目录大小和最后修改时间
     * 单次遍历同时获取两个值，避免遍历两次
     * @return Pair(大小, 最后修改时间)
     */
    private fun calculateDirSizeAndLastModified(dir: File): Pair<Long, Long> {
        if (!dir.exists()) return Pair(0L, 0L)
        var size = 0L
        var lastModified = 0L
        try {
            val stack = java.util.ArrayDeque<File>()
            stack.push(dir)
            while (stack.isNotEmpty()) {
                val current = stack.pop()
                current.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        size += file.length()
                        val fileTime = file.lastModified()
                        if (fileTime > lastModified) {
                            lastModified = fileTime
                        }
                    } else if (file.isDirectory) {
                        stack.push(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(size, lastModified)
    }

    suspend fun countCachedBooks(): Int = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedBookCount >= 0) {
            return@withContext cachedBookCount
        }
        val cacheDir = appCtx.externalFiles.getFile("book_cache")
        val count = cacheDir.listFiles()?.count { it.isDirectory } ?: 0
        cachedBookCount = count
        markCacheTime()
        count
    }

    suspend fun countTtsEngines(): Int = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedTtsCount >= 0) {
            return@withContext cachedTtsCount
        }
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        val count = ttsDir.listFiles()?.count { it.isDirectory } ?: 0
        cachedTtsCount = count
        markCacheTime()
        count
    }

    suspend fun countACacheItems(): Int = withContext(Dispatchers.IO) {
        if (isCacheValid() && cachedACacheCount >= 0) {
            return@withContext cachedACacheCount
        }
        val aCacheDir = File(appCtx.cacheDir, "ACache")
        val count = if (!aCacheDir.exists()) 0 else aCacheDir.listFiles()?.count { it.isFile } ?: 0
        cachedACacheCount = count
        markCacheTime()
        count
    }
}
