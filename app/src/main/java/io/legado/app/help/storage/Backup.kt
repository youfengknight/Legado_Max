package io.legado.app.help.storage

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import kotlinx.coroutines.currentCoroutineContext

/**
 * 备份管理类
 * 
 * 负责应用数据的备份功能，包括：
 * - 书架、书签、书源等数据库数据
 * - 阅读配置、主题配置等SharedPreferences数据
 * - 自定义配置文件（阅读样式、主题、封面规则等）
 * 
 * 备份流程：
 * 1. 将数据库数据导出为JSON文件
 * 2. 将SharedPreferences导出为XML文件
 * 3. 复制自定义配置文件
 * 4. 打包成ZIP文件
 * 5. 保存到本地目录或WebDav云端
 * 
 * 支持的备份内容：
 * - bookshelf.json: 书架书籍列表
 * - bookmark.json: 书签列表
 * - bookGroup.json: 书籍分组
 * - bookSource.json: 书源列表
 * - rssSources.json: RSS源列表
 * - rssStar.json: RSS收藏
 * - replaceRule.json: 替换规则
 * - readRecord.json: 阅读记录
 * - searchHistory.json: 搜索历史
 * - sourceSub.json: 订阅源
 * - txtTocRule.json: TXT目录规则
 * - httpTTS.json: TTS配置
 * - keyboardAssists.json: 键盘辅助
 * - dictRule.json: 词典规则
 * - servers.json: 服务器配置（加密存储）
 * - config.xml: 应用配置
 * - videoConfig.xml: 视频播放配置
 */
object Backup {

    /** 备份临时目录路径，用于存放解压/压缩前的文件 */
    val backupPath: String by lazy {
        appCtx.filesDir.getFile("backup").createFolderIfNotExist().absolutePath
    }

    /** 临时ZIP文件路径，备份完成后会删除 */
    val zipFilePath = "${appCtx.externalFiles.absolutePath}${File.separator}tmp_backup.zip"

    private const val TAG = "Backup"

    /** 互斥锁，防止并发备份操作 */
    private val mutex = Mutex()

    /** 备份文件名列表，定义所有需要备份的文件 */
    private val backupFileNames by lazy {
        arrayOf(
            "bookshelf.json",
            "bookmark.json",
            "bookGroup.json",
            "bookSource.json",
            "rssSources.json",
            "rssStar.json",
            "replaceRule.json",
            "readRecord.json",
            "searchHistory.json",
            "sourceSub.json",
            "txtTocRule.json",
            "httpTTS.json",
            "keyboardAssists.json",
            "dictRule.json",
            "servers.json",
            DirectLinkUpload.ruleFileName,
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml",
            "videoConfig.xml"
        )
    }

    fun getBackgroundImageFiles(): List<File> {
        return ReadBookConfig.getAllPicBgStr()
            .mapNotNull { bg ->
                val file = if (bg.contains(File.separator)) {
                    File(bg)
                } else {
                    appCtx.externalFiles.getFile("bg", bg)
                }
                file.takeIf { it.exists() && it.isFile }
            }
            .distinctBy { it.absolutePath }
    }

    private fun getBackupPaths(): ArrayList<String> {
        val paths = arrayListOf(*backupFileNames)
        for (i in 0 until paths.size) {
            paths[i] = backupPath + File.separator + paths[i]
        }
        getBackgroundImageFiles().forEach {
            paths.add(it.absolutePath)
        }
        return paths
    }

    /**
     * 生成备份ZIP文件名
     * 格式：backup{日期}-{设备名}.zip 或 backup{日期}.zip
     * 
     * @return 格式化的备份文件名
     */
    private fun getNowZipFileName(): String {
        val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val deviceName = AppConfig.webDavDeviceName
        return if (deviceName?.isNotBlank() == true) {
            "backup${backupDate}-${deviceName}.zip"
        } else {
            "backup${backupDate}.zip"
        }.normalizeFileName()
    }

    /**
     * 判断是否需要执行自动备份
     * 距离上次备份超过24小时才执行
     * 
     * @return true表示需要备份，false表示不需要
     */
    private fun shouldBackup(): Boolean {
        val lastBackup = LocalConfig.lastBackup
        return lastBackup + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()
    }

    /**
     * 自动备份入口
     * 在满足条件时自动执行备份，包括：
     * - 距离上次备份超过24小时
     * - WebDav上不存在当日备份文件
     * 
     * @param context Android Context
     */
    fun autoBack(context: Context) {
        if (shouldBackup()) {
            Coroutine.async {
                mutex.withLock {
                    if (shouldBackup()) {
                        val backupZipFileName = getNowZipFileName()
                        if (!AppWebDav.hasBackUp(backupZipFileName)) {
                            backup(context, AppConfig.backupPath)
                        } else {
                            LocalConfig.lastBackup = System.currentTimeMillis()
                        }
                    }
                }
            }.onError {
                AppLog.put("自动备份失败\n${it.localizedMessage}")
            }
        }
    }

    /**
     * 带锁的备份方法
     * 使用互斥锁确保同一时间只有一个备份操作在执行
     * 
     * @param context Android Context
     * @param path 备份目标路径，可为null（使用默认路径）
     */
    suspend fun backupLocked(context: Context, path: String?) {
        mutex.withLock {
            withContext(IO) {
                backup(context, path)
            }
        }
    }

    /**
     * 核心备份逻辑
     * 
     * 执行步骤：
     * 1. 清理旧的临时文件
     * 2. 导出数据库数据到JSON文件
     * 3. 导出SharedPreferences配置
     * 4. 打包成ZIP文件
     * 5. 复制到目标目录
     * 6. 上传到WebDav（如果配置）
     * 7. 清理临时文件
     * 
     * @param context Android Context
     * @param path 备份目标路径
     */
    private suspend fun backup(context: Context, path: String?) {
        LogUtils.d(TAG, "开始备份 path:$path")
        LocalConfig.lastBackup = System.currentTimeMillis()
        val aes = BackupAES()
        FileUtils.delete(backupPath)

        // 导出数据库数据到JSON文件
        writeListToJson(appDb.bookDao.all, "bookshelf.json", backupPath)
        writeListToJson(appDb.bookmarkDao.all, "bookmark.json", backupPath)
        writeListToJson(appDb.bookGroupDao.all, "bookGroup.json", backupPath)
        writeListToJson(appDb.bookSourceDao.all, "bookSource.json", backupPath)
        writeListToJson(appDb.rssSourceDao.all, "rssSources.json", backupPath)
        writeListToJson(appDb.rssStarDao.all, "rssStar.json", backupPath)
        writeListToJson(appDb.replaceRuleDao.all, "replaceRule.json", backupPath)
        writeListToJson(appDb.readRecordDao.all, "readRecord.json", backupPath)
        writeListToJson(appDb.searchKeywordDao.all, "searchHistory.json", backupPath)
        writeListToJson(appDb.ruleSubDao.all, "sourceSub.json", backupPath)
        writeListToJson(appDb.txtTocRuleDao.all, "txtTocRule.json", backupPath)
        writeListToJson(appDb.httpTTSDao.all, "httpTTS.json", backupPath)
        writeListToJson(appDb.keyboardAssistsDao.all, "keyboardAssists.json", backupPath)
        writeListToJson(appDb.dictRuleDao.all, "dictRule.json", backupPath)

        // 服务器配置需要加密存储
        GSON.toJson(appDb.serverDao.all).let { json ->
            aes.runCatching {
                encryptBase64(json)
            }.getOrDefault(json).let {
                FileUtils.createFileIfNotExist(backupPath + File.separator + "servers.json")
                    .writeText(it)
            }
        }

        currentCoroutineContext().ensureActive()

        // 导出阅读配置
        GSON.toJson(ReadBookConfig.getBackupConfigList()).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.configFileName)
                .writeText(it)
        }
        GSON.toJson(ReadBookConfig.getBackupShareConfig()).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.shareConfigFileName)
                .writeText(it)
        }

        // 导出主题配置
        GSON.toJson(ThemeConfig.configList).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ThemeConfig.configFileName)
                .writeText(it)
        }

        // 导出直链上传配置
        DirectLinkUpload.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + DirectLinkUpload.ruleFileName)
                .writeText(GSON.toJson(it))
        }

        // 导出封面规则配置
        BookCover.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + BookCover.configFileName)
                .writeText(GSON.toJson(it))
        }

        currentCoroutineContext().ensureActive()

        // 导出SharedPreferences配置（应用主配置）
        appCtx.getSharedPreferences(backupPath, "config")?.let { sp ->
            val edit = sp.edit()
            appCtx.defaultSharedPreferences.all.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    when (key) {
                        // WebDav密码需要加密存储
                        PreferKey.webDavPassword -> {
                            edit.putString(key, aes.runCatching {
                                encryptBase64(value.toString())
                            }.getOrDefault(value.toString()))
                        }

                        else -> when (value) {
                            is Int -> edit.putInt(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                            is Long -> edit.putLong(key, value)
                            is Float -> edit.putFloat(key, value)
                            is String -> edit.putString(key, value)
                        }
                    }
                }
            }
            edit.commit()
        }

        currentCoroutineContext().ensureActive()

        // 导出视频播放配置
        appCtx.getSharedPreferences(backupPath, "videoConfig")?.let { sp ->
            sp.edit(commit = true) {
                appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
            }
        }

        currentCoroutineContext().ensureActive()

        // 打包成ZIP文件
        val zipFileName = getNowZipFileName()
        val paths = getBackupPaths()
        FileUtils.delete(zipFilePath)
        FileUtils.delete(zipFilePath.replace("tmp_", ""))

        // 根据配置决定使用固定文件名还是带日期的文件名
        val backupFileName = if (AppConfig.onlyLatestBackup) {
            "backup.zip"
        } else {
            zipFileName
        }

        if (ZipUtils.zipFiles(paths, zipFilePath)) {
            // 复制到目标目录
            when {
                path.isNullOrBlank() -> {
                    copyBackup(context.getExternalFilesDir(null)!!, backupFileName)
                }

                path.isContentScheme() -> {
                    copyBackup(context, path.toUri(), backupFileName)
                }

                else -> {
                    copyBackup(File(path), backupFileName)
                }
            }

            // 上传到WebDav云端
            try {
                AppWebDav.backUpWebDav(zipFileName)
            } catch (e: Exception) {
                AppLog.put("上传备份至webdav失败\n$e", e)
            }
        }

        // 清理临时文件
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)

        currentCoroutineContext().ensureActive()

        // 上传背景图片到WebDav
        AppWebDav.upBgs(getBackgroundImageFiles().toTypedArray())
    }

    /**
     * 将列表数据写入JSON文件
     * 
     * @param list 要写入的数据列表
     * @param fileName 目标文件名
     * @param path 目标目录路径
     */
    private suspend fun writeListToJson(list: List<Any>, fileName: String, path: String) {
        currentCoroutineContext().ensureActive()
        withContext(IO) {
            if (list.isNotEmpty()) {
                LogUtils.d(TAG, "阅读备份 $fileName 列表大小 ${list.size}")
                val file = FileUtils.createFileIfNotExist(path + File.separator + fileName)
                file.outputStream().buffered().use {
                    GSON.writeToOutputStream(it, list)
                }
                LogUtils.d(TAG, "阅读备份 $fileName 写入大小 ${file.length()}")
            } else {
                LogUtils.d(TAG, "阅读备份 $fileName 列表为空")
            }
        }
    }

    /**
     * 复制备份文件到SAF（Storage Access Framework）目录
     * 用于Android 10+的分区存储
     * 
     * @param context Android Context
     * @param uri 目标目录URI
     * @param fileName 备份文件名
     * @throws Exception 创建文件或写入失败时抛出异常
     */
    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(context: Context, uri: Uri, fileName: String) {
        val treeDoc = DocumentFile.fromTreeUri(context, uri)!!
        treeDoc.findFile(fileName)?.delete()
        val fileDoc = treeDoc.createFile("", fileName)
            ?: throw NoStackTraceException("创建文件失败")
        val outputS = fileDoc.openOutputStream()
            ?: throw NoStackTraceException("打开OutputStream失败")
        outputS.use {
            FileInputStream(zipFilePath).use { inputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    /**
     * 复制备份文件到普通文件目录
     * 
     * @param rootFile 目标目录
     * @param fileName 备份文件名
     * @throws Exception 写入失败时抛出异常
     */
    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(rootFile: File, fileName: String) {
        FileInputStream(File(zipFilePath)).use { inputS ->
            val file = FileUtils.createFileIfNotExist(rootFile, fileName)
            FileOutputStream(file).use { outputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    /**
     * 清理备份缓存
     * 删除临时目录和临时ZIP文件
     */
    fun clearCache() {
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
    }
}
