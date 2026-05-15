package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.legado.app.utils.getBoolean
import io.legado.app.utils.putBoolean
import io.legado.app.utils.putLong
import io.legado.app.utils.putString
import io.legado.app.utils.remove
import splitties.init.appCtx

/**
 * 本地配置管理类
 * 用于存储应用本地相关的配置信息，如版本号、密码、备份时间等
 * 使用 SharedPreferences 持久化存储
 */
@Suppress("ConstPropertyName")
object LocalConfig : SharedPreferences
by appCtx.getSharedPreferences("local_plus", Context.MODE_PRIVATE) {

    // SharedPreferences 中存储版本号的 key
    private const val versionCodeKey = "appVersionCode"

    /**
     * 本地密码
     * 用来对需要备份的敏感信息（如 webdav 配置等）进行加密
     */
    var password: String?
        get() = getString("password", null)
        set(value) {
            if (value != null) {
                putString("password", value)
            } else {
                remove("password")
            }
        }

    /**
     * 最后一次备份的时间戳
     */
    var lastBackup: Long
        get() = getLong("lastBackup", 0)
        set(value) {
            putLong("lastBackup", value)
        }

    /**
     * 隐私政策是否已同意
     */
    var privacyPolicyOk: Boolean
        get() = getBoolean("privacyPolicyOk")
        set(value) {
            putBoolean("privacyPolicyOk", value)
        }

    /**
     * 阅读帮助是否已是最新版本
     * version=1, 首次打开阅读界面时显示帮助
     */
    val readHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readHelpVersion", "firstRead")

    /**
     * 备份帮助是否已是最新版本
     * version=1, 首次打开备份界面时显示帮助
     */
    val backupHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "backupHelpVersion", "firstBackup")

    /**
     * 阅读菜单帮助是否已是最新版本
     * version=1, 首次打开阅读菜单时显示帮助
     */
    val readMenuHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readMenuHelpVersion", "firstReadMenu")

    /**
     * 书源帮助是否已是最新版本
     * version=1, 首次打开书源列表时显示帮助
     */
    val bookSourcesHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "bookSourceHelpVersion", "firstOpenBookSources")

    /**
     * WebDAV 书籍帮助是否已是最新版本
     * version=1, 首次打开 WebDAV 书籍功能时显示帮助
     */
    val webDavBookHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "webDavBookHelpVersion", "firstOpenWebDavBook")

    /**
     * 规则帮助是否已是最新版本
     * version=1
     */
    val ruleHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "ruleHelpVersion")

    /**
     * 是否需要更新 HTTP TTS 默认配置
     * version=6, 版本号低于此值时会导入新的 HTTP TTS 配置
     */
    val needUpHttpTTS: Boolean
        get() = !isLastVersion(6, "httpTtsVersion")

    /**
     * 是否需要更新 TXT 目录规则默认配置
     * version=3, 版本号低于此值时会导入新的 TXT 目录规则
     */
    val needUpTxtTocRule: Boolean
        get() = !isLastVersion(3, "txtTocRuleVersion")

    /**
     * 是否需要更新 RSS 订阅源默认配置
     * version=7, 版本号低于此值时会从 rssSources.json 重新导入默认订阅源
     * 
     * 升级逻辑：
     * 1. 删除数据库中 sourceGroup like 'legado' 的旧默认源
     * 2. 从 assets/defaultData/rssSources.json 导入新的默认源
     * 3. 使用 OnConflictStrategy.REPLACE 替换已存在的源
     */
    val needUpRssSources: Boolean
        get() = !isLastVersion(7, "rssSourceVersion")

    /**
     * 是否需要更新词典规则默认配置
     * version=2, 版本号低于此值时会导入新的词典规则
     */
    val needUpDictRule: Boolean
        get() = !isLastVersion(2, "needUpDictRule")

    /**
     * 是否需要更新主题配置
     * version=2, 版本号低于此值时会合并新的默认主题
     */
    val needUpThemeConfig: Boolean
        get() = !isLastVersion(2, "themeConfigVersion")

    /**
     * 应用版本号
     * 用于判断是否需要执行默认数据导入等升级操作
     */
    var versionCode
        get() = getLong(versionCodeKey, 0)
        set(value) {
            edit { putLong(versionCodeKey, value) }
        }

    /**
     * 最后一次检查更新的时间戳
     */
    var lastCheckUpdate: Long
        get() = getLong("lastCheckUpdate", 0)
        set(value) {
            putLong("lastCheckUpdate", value)
        }

    /**
     * 是否是首次打开应用
     * 返回 true 后会将状态设为 false（仅触发一次）
     */
    val isFirstOpenApp: Boolean
        get() {
            val value = getBoolean("firstOpen", true)
            if (value) {
                edit { putBoolean("firstOpen", false) }
            }
            return value
        }

    /**
     * 检查指定功能版本是否已是最新版本
     * 
     * @param lastVersion 目标版本号
     * @param versionKey SharedPreferences 中存储版本号的 key
     * @param firstOpenKey 首次打开标记的 key（可选），用于区分新老用户
     * @return true 表示已是最新版本，无需升级；false 表示需要升级
     * 
     * 逻辑说明：
     * 1. 如果当前版本 < 目标版本，则标记为需要升级并返回 false
     * 2. 如果 version=0 且存在 firstOpenKey，判断是否为新用户
     *    - 新用户（firstOpen=true）：保持 version=0，返回 true（首次使用时不需要升级默认数据）
     *    - 老用户（firstOpen=false）：强制设为 version=1，返回 false（老用户需要升级）
     */
    @Suppress("SameParameterValue")
    private fun isLastVersion(
        lastVersion: Int,
        versionKey: String,
        firstOpenKey: String? = null
    ): Boolean {
        var version = getInt(versionKey, 0)
        if (version == 0 && firstOpenKey != null) {
            if (!getBoolean(firstOpenKey, true)) {
                version = 1
            }
        }
        if (version < lastVersion) {
            edit { putInt(versionKey, lastVersion) }
            return false
        }
        return true
    }

    /**
     * 删除书籍时是否弹出确认对话框
     * 默认 true（弹出）
     */
    var bookInfoDeleteAlert: Boolean
        get() = getBoolean("bookInfoDeleteAlert", true)
        set(value) {
            putBoolean("bookInfoDeleteAlert", value)
        }

    /**
     * 删除书籍时是否同时删除本地文件
     */
    var deleteBookOriginal: Boolean
        get() = getBoolean("deleteBookOriginal")
        set(value) {
            putBoolean("deleteBookOriginal", value)
        }

    /**
     * 应用是否发生过崩溃
     * 用于在崩溃后启动时显示相关信息或执行恢复操作
     */
    var appCrash: Boolean
        get() = getBoolean("appCrash")
        set(value) {
            putBoolean("appCrash", value)
        }

}
