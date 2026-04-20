package io.legado.app.api.controller

import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.storage.Backup
import io.legado.app.model.BookCover
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * Web端备份控制器
 * 提供一键备份功能，支持下载ZIP备份文件
 */
object BackupController {

    /**
     * 备份数据项信息
     */
    data class BackupItemInfo(
        val fileName: String,
        val displayName: String,
        val description: String,
        val count: Int,
        val size: Long
    )

    /**
     * 备份概览信息
     */
    data class BackupOverview(
        val fileName: String,
        val totalSize: Long,
        val createTime: Long,
        val items: List<BackupItemInfo>
    )

    /**
     * 备份数据项定义（带计数器）
     */
    private data class BackupItemDef(
        val fileName: String,
        val displayName: String,
        val description: String,
        val counter: () -> Int
    )

    /**
     * 配置项定义（无计数器）
     */
    private data class ConfigItemDef(
        val fileName: String,
        val displayName: String,
        val description: String
    )

    /**
     * 执行备份并返回ZIP文件
     * 
     * @return NanoHTTPD Response 包含ZIP文件流
     */
    fun backup(): NanoHTTPD.Response {
        return runBlocking {
            try {
                executeBackup()
            } catch (e: Exception) {
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "application/json",
                    GSON.toJson(ReturnData().setErrorMsg("备份失败: ${e.localizedMessage}"))
                )
            }
        }
    }

    /**
     * 获取备份内容预览
     * 返回备份ZIP中包含的文件列表和详细信息
     * 
     * @return ReturnData 包含BackupOverview
     */
    fun getBackupPreview(): ReturnData {
        val returnData = ReturnData()
        return try {
            val overview = generateBackupOverview()
            returnData.setData(overview)
        } catch (e: Exception) {
            returnData.setErrorMsg("获取备份预览失败: ${e.localizedMessage}")
        }
    }

    /**
     * 执行备份操作
     */
    private suspend fun executeBackup(): NanoHTTPD.Response {
        withContext(Dispatchers.IO) {
            Backup.backupLocked(appCtx, null)
        }

        val zipFile = File(Backup.zipFilePath)
        if (!zipFile.exists()) {
            val tempZip = File(appCtx.externalFiles.absolutePath, "tmp_backup.zip")
            if (tempZip.exists()) {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/zip",
                    FileInputStream(tempZip),
                    tempZip.length()
                ).apply {
                    addHeader("Content-Disposition", "attachment; filename=\"backup.zip\"")
                }
            }
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                GSON.toJson(ReturnData().setErrorMsg("备份文件生成失败"))
            )
        }

        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/zip",
            FileInputStream(zipFile),
            zipFile.length()
        ).apply {
            addHeader("Content-Disposition", "attachment; filename=\"backup.zip\"")
        }
    }

    /**
     * 生成备份概览信息
     */
    private fun generateBackupOverview(): BackupOverview {
        val items = mutableListOf<BackupItemInfo>()
        var totalSize = 0L

        val backupItems = listOf(
            BackupItemDef("bookshelf.json", "书架书籍", "书架上的所有书籍信息") {
                appDb.bookDao.all.size
            },
            BackupItemDef("bookmark.json", "书签", "书籍阅读书签") {
                appDb.bookmarkDao.all.size
            },
            BackupItemDef("bookGroup.json", "书籍分组", "书架分组信息") {
                appDb.bookGroupDao.all.size
            },
            BackupItemDef("bookSource.json", "书源", "网络小说书源") {
                appDb.bookSourceDao.all.size
            },
            BackupItemDef("rssSources.json", "RSS源", "RSS订阅源") {
                appDb.rssSourceDao.all.size
            },
            BackupItemDef("rssStar.json", "RSS收藏", "RSS收藏内容") {
                appDb.rssStarDao.all.size
            },
            BackupItemDef("replaceRule.json", "替换规则", "正文替换净化规则") {
                appDb.replaceRuleDao.all.size
            },
            BackupItemDef("readRecord.json", "阅读记录", "阅读时长统计记录") {
                appDb.readRecordDao.all.size
            },
            BackupItemDef("searchHistory.json", "搜索历史", "搜索关键词历史") {
                appDb.searchKeywordDao.all.size
            },
            BackupItemDef("sourceSub.json", "订阅源", "书源订阅地址") {
                appDb.ruleSubDao.all.size
            },
            BackupItemDef("txtTocRule.json", "TXT目录规则", "本地TXT目录解析规则") {
                appDb.txtTocRuleDao.all.size
            },
            BackupItemDef("httpTTS.json", "TTS配置", "在线朗读引擎配置") {
                appDb.httpTTSDao.all.size
            },
            BackupItemDef("keyboardAssists.json", "键盘辅助", "键盘快捷输入配置") {
                appDb.keyboardAssistsDao.all.size
            },
            BackupItemDef("dictRule.json", "词典规则", "长按查词规则") {
                appDb.dictRuleDao.all.size
            },
            BackupItemDef("servers.json", "服务器配置", "远程服务器配置（加密）") {
                appDb.serverDao.all.size
            }
        )

        backupItems.forEach { item ->
            val count = item.counter()
            val file = File(Backup.backupPath, item.fileName)
            val size = if (file.exists()) file.length() else 0L
            totalSize += size

            items.add(BackupItemInfo(
                fileName = item.fileName,
                displayName = item.displayName,
                description = item.description,
                count = count,
                size = size
            ))
        }

        val configItems = listOf(
            ConfigItemDef(ReadBookConfig.configFileName, "阅读样式配置", "阅读界面样式配置"),
            ConfigItemDef(ReadBookConfig.shareConfigFileName, "共享阅读配置", "跨设备共享的阅读配置"),
            ConfigItemDef(ThemeConfig.configFileName, "主题配置", "界面主题样式配置"),
            ConfigItemDef(BookCover.configFileName, "封面规则", "自定义封面生成规则"),
            ConfigItemDef("config.xml", "应用设置", "应用程序偏好设置"),
            ConfigItemDef("videoConfig.xml", "视频配置", "视频播放器设置")
        )

        configItems.forEach { item ->
            val file = File(Backup.backupPath, item.fileName)
            if (file.exists()) {
                totalSize += file.length()
                items.add(BackupItemInfo(
                    fileName = item.fileName,
                    displayName = item.displayName,
                    description = item.description,
                    count = 1,
                    size = file.length()
                ))
            }
        }

        return BackupOverview(
            fileName = "backup.zip",
            totalSize = totalSize,
            createTime = System.currentTimeMillis(),
            items = items.filter { it.count > 0 || it.size > 0 }
        )
    }

    /**
     * 格式化文件大小
     */
    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.1f MB", size / (1024.0 * 1024))
        }
    }
}
