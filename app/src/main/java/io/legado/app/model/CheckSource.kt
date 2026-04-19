package io.legado.app.model

import android.content.Context
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.CacheManager
import io.legado.app.help.IntentData
import io.legado.app.service.CheckSourceService
import io.legado.app.utils.startService
import splitties.init.appCtx

object CheckSource {
    var keyword = "我的"

    //校验设置
    var timeout = CacheManager.getLong("checkSourceTimeout") ?: 180000L
    var wSourceComment = CacheManager.get("wSourceComment")?.toBoolean() ?: true
    var checkDomain = CacheManager.get("checkDomain")?.toBoolean() ?: false
    var checkSearch = CacheManager.get("checkSearch")?.toBoolean() ?: true
    var checkDiscovery = CacheManager.get("checkDiscovery")?.toBoolean() ?: true
    var checkInfo = CacheManager.get("checkInfo")?.toBoolean() ?: true
    var checkCategory = CacheManager.get("checkCategory")?.toBoolean() ?: true
    var checkContent = CacheManager.get("checkContent")?.toBoolean() ?: true
    val summary get() = upSummary()

    /**
     * 启动书源校验
     * @param context 上下文
     * @param sources 书源列表
     */
    fun start(context: Context, sources: List<BookSourcePart>) {
        val selectedIds = sources.map {
            it.bookSourceUrl
        }
        IntentData.put("checkSourceSelectedIds", selectedIds)
        context.startService<CheckSourceService> {
            action = IntentAction.start
        }
    }

    /**
     * 停止书源校验
     * @param context 上下文
     */
    fun stop(context: Context) {
        context.startService<CheckSourceService> {
            action = IntentAction.stop
        }
    }

    /**
     * 恢复书源校验
     * @param context 上下文
     */
    fun resume(context: Context) {
        context.startService<CheckSourceService> {
            action = IntentAction.resume
        }
    }

    /**
     * 保存配置
     */
    fun putConfig() {
        CacheManager.put("checkSourceTimeout", timeout)
        CacheManager.put("wSourceComment", wSourceComment)
        CacheManager.put("checkDomain", checkDomain)
        CacheManager.put("checkSearch", checkSearch)
        CacheManager.put("checkDiscovery", checkDiscovery)
        CacheManager.put("checkInfo", checkInfo)
        CacheManager.put("checkCategory", checkCategory)
        CacheManager.put("checkContent", checkContent)
    }

    /**
     * 更新摘要信息
     * @return 摘要字符串
     */
    private fun upSummary(): String {
        var checkItem = ""
        if (checkDomain) checkItem = "$checkItem ${appCtx.getString(R.string.domain)}"
        if (checkSearch) checkItem = "$checkItem ${appCtx.getString(R.string.search)}"
        if (checkDiscovery) checkItem = "$checkItem ${appCtx.getString(R.string.discovery)}"
        if (checkInfo) checkItem = "$checkItem ${appCtx.getString(R.string.source_tab_info)}"
        if (checkCategory) checkItem = "$checkItem ${appCtx.getString(R.string.chapter_list)}"
        if (checkContent) checkItem = "$checkItem ${appCtx.getString(R.string.main_body)}"
        return appCtx.getString(
            R.string.check_source_config_summary,
            (timeout / 1000).toString(),
            checkItem
        )
    }
}