package io.legado.app.help

import android.text.style.URLSpan
import android.view.View
import io.legado.app.utils.openInInnerBrowser

/**
 * 自定义 URLSpan，将链接点击重定向到应用内置浏览器
 * 用于替换 HTML 渲染中默认的 URLSpan（默认行为是打开外部浏览器）
 * 通过 replaceUrlSpans() 在 setHtml/setMarkdown 中自动替换
 */
class InnerBrowserUrlSpan(url: String) : URLSpan(url) {

    override fun onClick(widget: View) {
        widget.context.openInInnerBrowser(url)
    }

}
