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

    suspend fun calculateBookCacheSize(): Long = withContext(Dispatchers.IO) {
        val cacheDir = appCtx.externalFiles.getFile("book_cache")
        calculateDirSize(cacheDir)
    }

    suspend fun calculateBookCacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val cacheDir = appCtx.externalFiles.getFile("book_cache")
        val details = mutableListOf<CacheDetail>()
        
        cacheDir.listFiles()?.forEach { bookDir ->
            if (bookDir.isDirectory) {
                val book = findBookByFolderName(bookDir.name)
                val size = calculateDirSize(bookDir)
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

    private fun findBookByFolderName(folderName: String): Book? {
        return try {
            appDb.bookDao.all.find { it.getFolderName() == folderName }
        } catch (e: Exception) {
            null
        }
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

    fun clearBookCache(bookUrl: String? = null) {
        if (bookUrl == null) {
            BookHelp.clearCache()
        } else {
            val book = appDb.bookDao.getBook(bookUrl) ?: return
            BookHelp.clearCache(book)
        }
    }

    suspend fun calculateTtsCacheSize(): Long = withContext(Dispatchers.IO) {
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        calculateDirSize(ttsDir)
    }

    suspend fun calculateTtsCacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        val details = mutableListOf<CacheDetail>()
        
        ttsDir.listFiles()?.forEach { engineDir ->
            if (engineDir.isDirectory) {
                val size = calculateDirSize(engineDir)
                if (size > 0) {
                    details.add(CacheDetail(
                        id = engineDir.name,
                        name = getTtsEngineName(engineDir.name),
                        meta = "最后使用: ${formatLastUsed(engineDir)}",
                        size = size,
                        formattedSize = ConvertUtils.formatFileSize(size)
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

    private fun formatLastUsed(dir: File): String {
        var lastModified = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.lastModified() > lastModified) {
                lastModified = file.lastModified()
            }
        }
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
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        if (engineId == null) {
            FileUtils.delete(ttsDir.absolutePath)
        } else {
            FileUtils.delete(ttsDir.getFile(engineId).absolutePath)
        }
    }

    suspend fun calculateACacheSize(): Long = withContext(Dispatchers.IO) {
        val aCacheDir = File(appCtx.cacheDir, "ACache")
        calculateDirSize(aCacheDir)
    }

    suspend fun calculateACacheDetails(): List<CacheDetail> = withContext(Dispatchers.IO) {
        val details = mutableListOf<CacheDetail>()
        
        val prefixes = listOf(
            "v_" to "书源变量缓存",
            "userInfo_" to "用户信息缓存",
            "loginHeader_" to "登录Header缓存",
            "sourceVariable_" to "书源扩展缓存",
            "infoMap_" to "信息映射缓存"
        )
        
        prefixes.forEach { (prefix, name) ->
            val (size, count) = calculateACacheByPrefix(prefix)
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

    private fun calculateACacheByPrefix(prefix: String): Pair<Long, Int> {
        val aCacheDir = File(appCtx.cacheDir, "ACache")
        if (!aCacheDir.exists()) return Pair(0L, 0)
        
        var totalSize = 0L
        var count = 0
        
        aCacheDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.startsWith(prefix)) {
                totalSize += file.length()
                count++
            }
        }
        
        return Pair(totalSize, count)
    }

    fun clearACache(prefix: String? = null) {
        if (prefix == null) {
            ACache.get().clear()
        } else {
            val aCacheDir = File(appCtx.cacheDir, "ACache")
            aCacheDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.startsWith(prefix)) {
                    file.delete()
                }
            }
        }
    }

    suspend fun calculateDbCacheSize(): Long = withContext(Dispatchers.IO) {
        try {
            val dbFile = appCtx.getDatabasePath("legado.db")
            if (dbFile.exists()) {
                dbFile.length()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
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
        try {
            appDb.cacheDao.deleteAllRuntimeSourceCaches()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun calculateEpubCacheSize(): Long = withContext(Dispatchers.IO) {
        val epubDir = appCtx.externalFiles.getFile("epub")
        calculateDirSize(epubDir)
    }

    fun clearEpubCache() {
        FileUtils.delete(appCtx.externalFiles.getFile("epub").absolutePath)
    }

    suspend fun calculateTempCacheSize(): Long = withContext(Dispatchers.IO) {
        calculateDirSize(appCtx.externalCache)
    }

    fun clearTempCache() {
        appCtx.externalCache.listFiles()?.forEach { 
            if (it.isDirectory) {
                FileUtils.delete(it.absolutePath)
            } else {
                it.delete()
            }
        }
    }

    suspend fun calculateLogCacheSize(): Long = withContext(Dispatchers.IO) {
        val logDir = appCtx.externalCache.getFile("log")
        calculateDirSize(logDir)
    }

    fun clearLogCache() {
        FileUtils.delete(appCtx.externalCache.getFile("log").absolutePath)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    suspend fun countCachedBooks(): Int = withContext(Dispatchers.IO) {
        val cacheDir = appCtx.externalFiles.getFile("book_cache")
        cacheDir.listFiles()?.count { it.isDirectory } ?: 0
    }

    suspend fun countTtsEngines(): Int = withContext(Dispatchers.IO) {
        val ttsDir = appCtx.cacheDir.getFile("httpTTS")
        ttsDir.listFiles()?.count { it.isDirectory } ?: 0
    }

    suspend fun countACacheItems(): Int = withContext(Dispatchers.IO) {
        val aCacheDir = File(appCtx.cacheDir, "ACache")
        if (!aCacheDir.exists()) return@withContext 0
        var count = 0
        aCacheDir.walkTopDown().forEach { file ->
            if (file.isFile) count++
        }
        count
    }
}
