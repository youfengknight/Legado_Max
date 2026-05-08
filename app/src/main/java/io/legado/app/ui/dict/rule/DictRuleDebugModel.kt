package io.legado.app.ui.dict.rule

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.DictRule
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl

class DictRuleDebugModel(application: Application) : BaseViewModel(application) {

    var dictRule: DictRule? = null
    var callback: ((Int, String) -> Unit)? = null
    var urlSrc: String = ""
    var resultSrc: String = ""

    fun init(dictRule: DictRule, finally: () -> Unit) {
        this.dictRule = dictRule
        finally.invoke()
    }

    fun observe(callback: (Int, String) -> Unit) {
        this.callback = callback
    }

    fun startDebug(word: String, start: (() -> Unit)? = null) {
        execute {
            val rule = dictRule ?: return@execute
            start?.invoke()

            callback?.invoke(0, "=== 开始查询字典 ===")
            callback?.invoke(0, "规则名称: ${rule.name}")
            callback?.invoke(0, "查询词: $word")
            callback?.invoke(0, "URL规则: ${rule.urlRule}")

            val analyzeUrl = AnalyzeUrl(rule.urlRule, key = word)
            val url = analyzeUrl.url
            callback?.invoke(0, "请求URL: $url")

            val response = analyzeUrl.getStrResponseAwait()
            val body = response.body ?: ""
            urlSrc = body
            callback?.invoke(0, "响应状态码: ${response.code()}")
            callback?.invoke(0, "响应长度: ${body.length} 字符")

            val result = if (rule.showRule.isBlank()) {
                callback?.invoke(0, "未设置显示规则，直接返回响应内容")
                body
            } else {
                callback?.invoke(0, "显示规则: ${rule.showRule}")
                val analyzeRule = AnalyzeRule()
                analyzeRule.setRuleName(rule.name)
                analyzeRule.getString(rule.showRule, mContent = body) ?: body
            }

            resultSrc = result
            callback?.invoke(1, "=== 查询结果 ===")
            callback?.invoke(1, result)

        }.onError { e ->
            callback?.invoke(-1, "查询失败: ${e.message}")
            callback?.invoke(-1, e.stackTraceToString())
        }
    }

}