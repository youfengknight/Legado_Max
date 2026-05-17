package io.legado.app.constant
//事件总线机制，用于在不同组件之间传递事�?
/**
 * 事件总线常量�?
 *
 * 基于 LiveEventBus 的事件标签定义，每个常量对应一个独立的事件通道�?
 * 发送端通过 postEvent(tag, data) 发送，接收端通过 observeEvent(tag) 订阅�?
 * tag 即下方定义的字符串常量，data 的类型由业务决定（String/Int/Bundle 等）�?
 *
 * 使用示例�?
 *   发送：postEvent(EventBus.BOOKSHELF_REFRESH, "")
 *   接收：observeEvent<String>(EventBus.BOOKSHELF_REFRESH) { refreshBookshelf() }
 */
object EventBus {

    // ── 通用 ──
    const val RECREATE = "RECREATE"                         // 重建 Activity
    const val NOTIFY_MAIN = "notifyMain"                    // 通知主界面刷�?
    const val WEB_SERVICE = "webService"                    // Web 服务状态变�?
    const val DEBUG_MODE_CHANGED = "debugModeChanged"       // 调试模式开关变�?

    // ── 书架 ──
    const val UP_BOOKSHELF = "upBookToc"                    // 书籍目录更新
    const val BOOKSHELF_REFRESH = "bookshelfRefresh"        // 书架列表刷新
    const val SOURCE_CHANGED = "sourceChanged"              // 书源变更
    const val REFRESH_BOOK_INFO = "refreshBookInfo"         // 刷新书籍信息
    const val REFRESH_BOOK_CONTENT = "refreshBookContent"   // 刷新书籍内容
    const val REFRESH_BOOK_TOC = "refreshBookToc"           // 刷新书籍目录

    // ── 阅读�?──
    const val UP_CONFIG = "upConfig"                        // 阅读配置变更（字�?背景/排版等）
    const val UPDATE_READ_ACTION_BAR = "updateReadActionBar"// 阅读页操作栏更新
    const val UP_SEEK_BAR = "upSeekBar"                     // 阅读进度条更�?
    const val TIP_COLOR = "tipColor"                        // 提示文字颜色变更
    const val UP_MANGA_CONFIG = "upMangaConfig"             // 漫画阅读配置更新
    const val MEDIA_BUTTON = "mediaButton"                  // 媒体按钮事件（耳机线控等）

    // ── 朗读 / TTS ──
    const val ALOUD_STATE = "aloud_state"                   // 朗读状态变�?
    const val TTS_PROGRESS = "ttsStart"                     // TTS 朗读进度
    const val READ_ALOUD_DS = "readAloudDs"                 // 朗读数据源变�?
    const val READ_ALOUD_PLAY = "readAloudPlay"             // 朗读播放控制指令
    const val SHOW_READ_MENU = "showReadMenu"               // ��ʾ���Ĳ˵�

    // ── 音频播放 ──
    const val AUDIO_DS = "audioDs"                          // 音频数据源变�?
    const val AUDIO_STATE = "audioState"                    // 音频播放状态变�?
    const val AUDIO_SUB_TITLE = "audioSubTitle"             // 音频字幕更新
    const val AUDIO_PROGRESS = "audioProgress"              // 音频播放进度
    const val AUDIO_BUFFER_PROGRESS = "audioBufferProgress" // 音频缓冲进度
    const val AUDIO_SIZE = "audioSize"                      // 音频总时�?
    const val AUDIO_SPEED = "audioSpeed"                    // 音频播放速度
    const val PLAY_MODE_CHANGED = "playModeChanged"         // 播放模式变化（单�?列表等）

    // ── 视频播放 ──
    const val VIDEO_SUB_TITLE = "VideoSubTitle"             // 视频字幕更新
    const val UP_VIDEO_INFO = "upVideoInfo"                 // 视频信息更新

    // ── 系统 ──
    const val BATTERY_CHANGED = "batteryChanged"            // 电池电量变化
    const val TIME_CHANGED = "timeChanged"                  // 系统时间变化

    // ── 下载 / 导出 ──
    const val UP_DOWNLOAD = "upDownload"                    // 下载任务更新
    const val UP_DOWNLOAD_STATE = "upDownloadState"         // 下载状态变�?
    const val SAVE_CONTENT = "saveContent"                  // 保存内容完成
    const val EXPORT_BOOK = "exportBook"                    // 导出书籍

    // ── 校源 ──
    const val CHECK_SOURCE = "checkSource"                  // 开始校�?
    const val CHECK_SOURCE_RESULT = "checkSourceResult"     // 单个书源校源结果
    const val CHECK_SOURCE_DONE = "checkSourceDone"         // 校源完成

    // ── 搜索 ──
    const val SEARCH_RESULT = "searchResult"                // 搜索结果更新

    // ── 封面模板 ──
    const val COVER_HTML_TEMPLATE_CHANGED = "coverHtmlTemplateChanged"  // HTML封面模板变更
}
