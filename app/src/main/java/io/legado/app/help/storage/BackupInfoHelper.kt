package io.legado.app.help.storage

import io.legado.app.data.appDb
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.BookCover
import splitties.init.appCtx
import java.io.File

/**
 * 备份信息工具类
 * 直接统计当前会备份的数据，不需要解析ZIP文件
 */
object BackupInfoHelper {

    /**
     * 备份文件信息
     */
    data class BackupFileInfo(
        val fileName: String,
        val displayName: String,
        val size: Long
    )

    data class BackupOverview(
        val items: List<BackupFileInfo>,
        val totalSize: Long
    )

    data class CategoryInfo(
        val name: String,
        val icon: String,
        val items: List<BackupFileInfo>,
        val totalSize: Long
    )

    private val categoryConfig = listOf(
        CategoryDef("书籍相关", "📚", listOf("bookshelf", "bookmark", "bookGroup", "readRecord")),
        CategoryDef("源相关", "📡", listOf("bookSource", "rssSource", "rssStar", "sourceSub")),
        CategoryDef("规则相关", "🔧", listOf("replaceRule", "txtTocRule", "dictRule", "keyboardAssist")),
        CategoryDef("语音相关", "🔊", listOf("httpTTS")),
        CategoryDef("配置相关", "⚙️", listOf("config", "videoConfig", "readConfig", "shareConfig", "themeConfig", "coverConfig", "servers")),
        CategoryDef("其他", "📁", listOf("searchHistory", "DirectLinkUpload"))
    )

    private data class CategoryDef(
        val name: String,
        val icon: String,
        val keywords: List<String>
    )

    private val displayNameMap = mapOf(
        "bookshelf.json" to "书架书籍",
        "bookmark.json" to "书签",
        "bookGroup.json" to "书籍分组",
        "bookSource.json" to "书源",
        "rssSources.json" to "RSS源",
        "rssStar.json" to "RSS收藏",
        "replaceRule.json" to "替换规则",
        "readRecord.json" to "阅读记录",
        "searchHistory.json" to "搜索历史",
        "sourceSub.json" to "订阅源",
        "txtTocRule.json" to "TXT目录规则",
        "httpTTS.json" to "TTS配置",
        "keyboardAssists.json" to "键盘辅助",
        "dictRule.json" to "词典规则",
        "servers.json" to "服务器配置",
        ReadBookConfig.configFileName to "阅读样式配置",
        ReadBookConfig.shareConfigFileName to "共享阅读配置",
        ThemeConfig.configFileName to "主题配置",
        BookCover.configFileName to "封面规则",
        "config.xml" to "应用设置",
        "videoConfig.xml" to "视频配置"
    )

    /**
     * 获取备份信息概览
     * 直接统计当前会备份的数据
     */
    fun getBackupOverview(): BackupOverview {
        val items = mutableListOf<BackupFileInfo>()
        var totalSize = 0L

        // 数据库数据统计
        val dbItems = listOf(
            Pair("bookshelf.json") { appDb.bookDao.all.size },
            Pair("bookmark.json") { appDb.bookmarkDao.all.size },
            Pair("bookGroup.json") { appDb.bookGroupDao.all.size },
            Pair("bookSource.json") { appDb.bookSourceDao.all.size },
            Pair("rssSources.json") { appDb.rssSourceDao.all.size },
            Pair("rssStar.json") { appDb.rssStarDao.all.size },
            Pair("replaceRule.json") { appDb.replaceRuleDao.all.size },
            Pair("readRecord.json") { appDb.readRecordDao.all.size },
            Pair("searchHistory.json") { appDb.searchKeywordDao.all.size },
            Pair("sourceSub.json") { appDb.ruleSubDao.all.size },
            Pair("txtTocRule.json") { appDb.txtTocRuleDao.all.size },
            Pair("httpTTS.json") { appDb.httpTTSDao.all.size },
            Pair("keyboardAssists.json") { appDb.keyboardAssistsDao.all.size },
            Pair("dictRule.json") { appDb.dictRuleDao.all.size },
            Pair("servers.json") { appDb.serverDao.all.size }
        )

        dbItems.forEach { (fileName, countProvider) ->
            val count = countProvider()
            val displayName = displayNameMap[fileName] ?: fileName
            // 估算JSON大小：每条记录约200字节
            val estimatedSize = count * 200L
            totalSize += estimatedSize
            items.add(BackupFileInfo(fileName, displayName, estimatedSize))
        }

        // 配置文件统计
        val configFiles = listOf(
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml",
            "videoConfig.xml"
        )

        configFiles.forEach { fileName ->
            val file = File(appCtx.filesDir, fileName)
            val size = if (file.exists()) file.length() else 0L
            if (size > 0) {
                totalSize += size
                val displayName = displayNameMap[fileName] ?: fileName
                items.add(BackupFileInfo(fileName, displayName, size))
            }
        }

        // 直链上传配置
        DirectLinkUpload.getConfig()?.let {
            val fileName = DirectLinkUpload.ruleFileName
            val json = io.legado.app.utils.GSON.toJson(it)
            val size = json.length.toLong()
            totalSize += size
            items.add(BackupFileInfo(fileName, "直链上传配置", size))
        }

        return BackupOverview(items, totalSize)
    }

    /**
     * 将文件列表按分类分组
     */
    fun categorizeItems(items: List<BackupFileInfo>): List<CategoryInfo> {
        val result = mutableListOf<CategoryInfo>()
        val assigned = mutableSetOf<String>()

        for (cfg in categoryConfig) {
            val matched = items.filter { item ->
                cfg.keywords.any { kw ->
                    item.fileName.lowercase().contains(kw.lowercase())
                } && !assigned.contains(item.fileName)
            }
            if (matched.isNotEmpty()) {
                matched.forEach { assigned.add(it.fileName) }
                result.add(CategoryInfo(
                    name = cfg.name,
                    icon = cfg.icon,
                    items = matched,
                    totalSize = matched.sumOf { it.size }
                ))
            }
        }

        // 未分类的放入其他
        val remaining = items.filter { !assigned.contains(it.fileName) }
        if (remaining.isNotEmpty()) {
            result.add(CategoryInfo(
                name = "其他",
                icon = "📁",
                items = remaining,
                totalSize = remaining.sumOf { it.size }
            ))
        }

        return result
    }

    /**
     * 格式化文件大小
     */
    fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024))
        }
    }
}
