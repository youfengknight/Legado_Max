package io.legado.app.help.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst.androidId
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复管理类
 * 
 * 负责从备份文件恢复应用数据，包括：
 * - 解压备份ZIP文件
 * - 恢复数据库数据（书籍、书签、书源等）
 * - 恢复SharedPreferences配置
 * - 恢复自定义配置文件
 * 
 * 恢复流程：
 * 1. 解压ZIP文件到临时目录
 * 2. 读取JSON文件并导入数据库
 * 3. 恢复SharedPreferences配置
 * 4. 应用主题和阅读配置
 * 5. 清理临时文件
 * 
 * 特殊处理：
 * - 书籍数据：支持忽略本地书籍，更新已存在书籍
 * - 阅读记录：根据设备ID判断是否为本机记录，智能合并
 * - 服务器配置：需要解密
 * - WebDav密码：需要解密
 */
object Restore {

    /** 互斥锁，防止并发恢复操作 */
    private val mutex = Mutex()

    private const val TAG = "Restore"

    /**
     * 从URI恢复备份
     * 支持SAF（Storage Access Framework）和普通文件路径
     * 
     * @param context Android Context
     * @param uri 备份文件URI
     */
    suspend fun restore(context: Context, uri: Uri) {
        LogUtils.d(TAG, "开始恢复备份 uri:$uri")
        kotlin.runCatching {
            FileUtils.delete(Backup.backupPath)
            if (uri.isContentScheme()) {
                DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                    ZipUtils.unZipToPath(it, Backup.backupPath)
                }
            } else {
                ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
            }
        }.onFailure {
            AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            return
        }
        kotlin.runCatching {
            restoreLocked(Backup.backupPath)
            LocalConfig.lastBackup = System.currentTimeMillis()
        }.onFailure {
            appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
            AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
        }
    }

    /**
     * 带锁的恢复方法
     * 使用互斥锁确保同一时间只有一个恢复操作在执行
     * 
     * @param path 备份文件解压后的目录路径
     */
    suspend fun restoreLocked(path: String) {
        mutex.withLock {
            restore(path)
        }
    }

    /**
     * 核心恢复逻辑
     * 
     * 执行步骤：
     * 1. 恢复数据库数据（书籍、书签、书源等）
     * 2. 恢复自定义配置文件（主题、阅读样式等）
     * 3. 恢复SharedPreferences配置
     * 4. 应用配置变更
     * 
     * @param path 备份文件解压后的目录路径
     */
    private suspend fun restore(path: String) {
        val aes = BackupAES()

        // 恢复书架数据
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val newBooks = arrayListOf<Book>()
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            it.forEach { book ->
                if (ignoreLocalBook && book.isLocal) {
                    return@forEach
                }
                if (appDb.bookDao.has(book.bookUrl)) {
                    try {
                        appDb.bookDao.update(book)
                    } catch (_: SQLiteConstraintException) {
                        appDb.bookDao.insert(book)
                    }
                } else {
                    newBooks.add(book)
                }
            }
            appDb.bookDao.insert(*newBooks.toTypedArray())
        }

        // 恢复书签
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }

        // 恢复书籍分组
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }

        // 恢复书源（兼容旧版本格式）
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }

        // 恢复RSS源
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }

        // 恢复RSS收藏
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }

        // 恢复替换规则
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }

        // 恢复搜索历史
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }

        // 恢复订阅源
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }

        // 恢复TXT目录规则
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }

        // 恢复HTTP TTS配置
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }

        // 恢复词典规则
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }

        // 恢复键盘辅助（先删除再插入，保证与备份数据一致）
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.deleteAll()
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }

        // 恢复阅读记录（智能合并）
        fileToListT<ReadRecord>(path, "readRecord.json")?.let {
            it.forEach { readRecord ->
                // 判断是不是本机记录
                if (readRecord.deviceId != androidId) {
                    appDb.readRecordDao.insert(readRecord)
                } else {
                    // 本机记录：只更新更新的记录
                    val time = appDb.readRecordDao
                        .getReadTime(readRecord.deviceId, readRecord.bookName)
                    if (time == null || time < readRecord.readTime) {
                        appDb.readRecordDao.insert(readRecord)
                    }
                }
            }
        }

        // 恢复服务器配置（需要解密）
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }

        // 恢复直链上传配置
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }

        // 恢复主题配置
        File(path, ThemeConfig.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            FileUtils.delete(ThemeConfig.configFilePath)
            copyTo(File(ThemeConfig.configFilePath))
            ThemeConfig.upConfig()
        }?.onFailure {
            AppLog.put("恢复主题出错\n${it.localizedMessage}", it)
        }

        // 恢复封面规则配置
        File(path, BookCover.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }

        // 恢复阅读界面配置（可配置忽略）
        if (!BackupConfig.ignoreReadConfig) {
            restoreReadConfigBackgrounds(path)
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }

        // 恢复主题背景图片
        restoreThemeBackgrounds(path)

        // 恢复SharedPreferences配置（应用主配置）
        appCtx.getSharedPreferences(path, "config")?.all?.let { map ->
            val edit = appCtx.defaultSharedPreferences.edit()

            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    when (key) {
                        // WebDav密码需要解密
                        PreferKey.webDavPassword -> {
                            kotlin.runCatching {
                                aes.decryptStr(value.toString())
                            }.getOrNull()?.let {
                                edit.putString(key, it)
                            } ?: let {
                                // 解密失败时，如果本地密码为空则使用备份中的值
                                if (appCtx.getPrefString(PreferKey.webDavPassword)
                                        .isNullOrBlank()
                                ) {
                                    edit.putString(key, value.toString())
                                }
                            }
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
            edit.apply()
        }

        // 恢复视频播放配置
        appCtx.getSharedPreferences(path, "videoConfig")?.all?.let { map ->
            appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                map.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
        }

        // 应用阅读配置
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)

        // 应用主题和图标变更
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    /**
     * 从JSON文件读取列表数据
     * 
     * @param T 数据类型
     * @param path 备份目录路径
     * @param fileName JSON文件名
     * @return 解析后的列表，文件不存在或解析失败返回null
     */
    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

    private fun restoreReadConfigBackgrounds(path: String) {
        val bgNames = linkedSetOf<String>()
        File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { config ->
            collectBgNames(config, bgNames)
        }
        File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.let { config ->
            collectBgNames(config, bgNames)
        }
        if (bgNames.isEmpty()) return
        val bgDir = appCtx.externalFiles.getFile("bg")
        if (!bgDir.exists()) {
            bgDir.mkdirs()
        }
        bgNames.forEach { bgName ->
            File(path, bgName).takeIf { it.exists() && it.isFile }?.copyTo(
                File(bgDir, bgName),
                overwrite = true
            )
        }
    }

    private fun collectBgNames(
        config: ReadBookConfig.Config,
        bgNames: MutableSet<String>
    ) {
        if (config.bgType == 2) {
            bgNames.add(File(config.bgStr).name)
        }
        if (config.bgTypeNight == 2) {
            bgNames.add(File(config.bgStrNight).name)
        }
        if (config.bgTypeEInk == 2) {
            bgNames.add(File(config.bgStrEInk).name)
        }
    }

    private fun restoreThemeBackgrounds(backupPath: String) {
        // 从 config.xml 中读取主题背景图片路径
        val configSp = appCtx.getSharedPreferences(backupPath, "config") ?: return
        
        // 恢复白天主题背景
        configSp.getString(PreferKey.bgImage, null)?.let { bgPath ->
            restoreThemeBgFile(backupPath, bgPath, PreferKey.bgImage)
        }
        
        // 恢复夜间主题背景
        configSp.getString(PreferKey.bgImageN, null)?.let { bgPath ->
            restoreThemeBgFile(backupPath, bgPath, PreferKey.bgImageN)
        }
    }
    
    private fun restoreThemeBgFile(backupPath: String, bgPath: String, prefKey: String) {
        if (bgPath.isBlank()) return
        
        val bgFile = if (bgPath.startsWith("http")) {
            // 在线图片，文件名从 URL 计算
            val name = ThemeConfig.getUrlToFile(bgPath)
            appCtx.externalFiles.getFile(prefKey, name)
        } else if (bgPath.contains(File.separator)) {
            // 本地路径，提取文件名
            val name = File(bgPath).name
            appCtx.externalFiles.getFile(prefKey, name)
        } else {
            // 已经是文件名
            appCtx.externalFiles.getFile(prefKey, bgPath)
        }
        
        // 从备份目录复制文件
        val bgName = File(bgPath).name
        val backupFile = File(backupPath, bgName)
        if (backupFile.exists() && backupFile.isFile) {
            val targetDir = appCtx.externalFiles.getFile(prefKey)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            backupFile.copyTo(File(targetDir, bgName), overwrite = true)
            LogUtils.d(TAG, "恢复主题背景: $bgName -> ${bgFile.absolutePath}")
        }
    }

}
