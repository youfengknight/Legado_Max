package io.legado.app.help

import android.view.View
import io.legado.app.utils.openInInnerBrowser
import io.noties.markwon.LinkResolver

/**
 * 自定义 Markwon 链接解析器
 * 将 Markdown 中的链接点击重定向到应用内置浏览器，而非外部浏览器
 * 配合 Markwon.builder().linkResolver() 使用
 */
object InnerBrowserLinkResolver : LinkResolver {

    override fun resolve(view: View, link: String) {
        view.context.openInInnerBrowser(link)
    }

}
