package io.legado.app.help.storage

import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx

/**
 * 备份配置管理类
 * 
 * 管理备份和恢复时的配置忽略规则，用于：
 * - 控制哪些配置项不参与备份/恢复
 * - 用户可自定义忽略特定配置
 * 
 * 配置忽略分为两类：
 * 1. 自动忽略：固定不备份的配置（如备份路径、设备名等）
 * 2. 用户忽略：用户可选择忽略的配置（如阅读配置、主题配置等）
 * 
 * 忽略配置存储在 restoreIgnore.json 文件中
 */
@Suppress("ConstPropertyName")
object BackupConfig {

    /** 忽略配置文件路径 */
    private val ignoreConfigPath = FileUtils.getPath(appCtx.filesDir, "restoreIgnore.json")

    /** 忽略配置映射表，key为配置项，value为是否忽略 */
    val ignoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(ignoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    // ==================== 配置项Key常量 ====================

    /** 阅读配置忽略Key */
    private const val readConfigKey = "readConfig"

    /** 主题模式忽略Key */
    private const val themeConfigKey = "themeConfig"

    /** 封面配置忽略Key */
    private const val coverConfigKey = "coverConfig"

    /** 本地书籍忽略Key */
    private const val localBookKey = "localBook"

    // ==================== 用户可配置忽略项 ====================

    /** 用户可配置忽略的配置Key列表 */
    val ignoreKeys = arrayOf(
        readConfigKey,
        PreferKey.themeMode,
        themeConfigKey,
        coverConfigKey,
        PreferKey.bookshelfLayout,
        PreferKey.showRss,
        PreferKey.threadCount,
        localBookKey
    )

    /** 用户可配置忽略的配置标题列表（用于UI显示） */
    val ignoreTitle = arrayOf(
        appCtx.getString(R.string.read_config),
        appCtx.getString(R.string.theme_mode),
        appCtx.getString(R.string.theme_config),
        appCtx.getString(R.string.cover_config),
        appCtx.getString(R.string.bookshelf_layout),
        appCtx.getString(R.string.show_rss),
        appCtx.getString(R.string.thread_count),
        appCtx.getString(R.string.local_book)
    )

    // ==================== 自动忽略项 ====================

    /**
     * 自动忽略的SharedPreferences Key列表
     * 这些配置项固定不参与备份/恢复
     */
    private val ignorePrefKeys = arrayOf(
        PreferKey.defaultCover,
        PreferKey.defaultCoverDark,
        PreferKey.backupPath,
        PreferKey.defaultBookTreeUri,
        PreferKey.webDavDeviceName,
        PreferKey.launcherIcon,
        PreferKey.bitmapCacheSize,
        PreferKey.webServiceWakeLock,
        PreferKey.readAloudWakeLock,
        PreferKey.audioPlayWakeLock
    )

    // ==================== 分类配置Key ====================

    /** 阅读相关配置Key列表 */
    private val readPrefKeys = arrayOf(
        PreferKey.readStyleSelect,
        PreferKey.comicStyleSelect,
        PreferKey.shareLayout,
        PreferKey.hideStatusBar,
        PreferKey.hideNavigationBar,
        PreferKey.autoReadSpeed,
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR
    )

    /** 主题相关配置Key列表 */
    private val themePrefKeys = arrayOf(
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.tNavBar,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring,
        PreferKey.tNavBarN
    )

    /** 封面相关配置Key列表 */
    private val coverPrefKeys = arrayOf(
        PreferKey.useDefaultCover,
        PreferKey.loadCoverOnlyWifi,
        PreferKey.coverShowName,
        PreferKey.coverShowAuthor,
        PreferKey.coverShowNameN,
        PreferKey.coverShowAuthorN
    )

    /**
     * 判断配置Key是否不应该被忽略
     * 
     * 检查逻辑：
     * 1. 是否在自动忽略列表中
     * 2. 是否在用户忽略列表中（根据用户配置）
     * 
     * @param key SharedPreferences配置Key
     * @return true表示应该备份/恢复，false表示应该忽略
     */
    fun keyIsNotIgnore(key: String): Boolean {
        return when {
            ignorePrefKeys.contains(key) -> false
            ignoreReadConfig && readPrefKeys.contains(key) -> false
            ignoreThemeConfig && themePrefKeys.contains(key) -> false
            ignoreCoverConfig && coverPrefKeys.contains(key) -> false
            PreferKey.themeMode == key && ignoreThemeMode -> false
            PreferKey.bookshelfLayout == key && ignoreBookshelfLayout -> false
            PreferKey.showRss == key && ignoreShowRss -> false
            PreferKey.threadCount == key && ignoreThreadCount -> false
            else -> true
        }
    }

    // ==================== 忽略配置属性 ====================

    /** 是否忽略阅读配置 */
    val ignoreReadConfig: Boolean
        get() = ignoreConfig[readConfigKey] == true

    /** 是否忽略主题模式 */
    private val ignoreThemeMode: Boolean
        get() = ignoreConfig[PreferKey.themeMode] == true

    /** 是否忽略主题配置 */
    private val ignoreThemeConfig: Boolean
        get() = ignoreConfig[themeConfigKey] == true

    /** 是否忽略封面配置 */
    private val ignoreCoverConfig: Boolean
        get() = ignoreConfig[coverConfigKey] == true

    /** 是否忽略书架布局 */
    private val ignoreBookshelfLayout: Boolean
        get() = ignoreConfig[PreferKey.bookshelfLayout] == true

    /** 是否忽略RSS显示 */
    private val ignoreShowRss: Boolean
        get() = ignoreConfig[PreferKey.showRss] == true

    /** 是否忽略线程数 */
    private val ignoreThreadCount: Boolean
        get() = ignoreConfig[PreferKey.threadCount] == true

    /** 是否忽略本地书籍 */
    val ignoreLocalBook: Boolean
        get() = ignoreConfig[localBookKey] == true

    /**
     * 保存忽略配置到文件
     * 将当前的忽略配置序列化为JSON并写入文件
     */
    fun saveIgnoreConfig() {
        val json = GSON.toJson(ignoreConfig)
        FileUtils.createFileIfNotExist(ignoreConfigPath).writeText(json)
    }

}
