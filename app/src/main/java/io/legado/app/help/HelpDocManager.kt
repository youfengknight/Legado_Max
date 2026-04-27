package io.legado.app.help

import android.content.Context
import android.content.res.AssetManager

object HelpDocManager {
    // 帮助文档切换列表，不是所有的
    val allHelpDocs = listOf(
        HelpDoc("appHelp", "APP帮助文档"),
        HelpDoc("ruleHelp", "书源制作教程"),
        HelpDoc("rssRuleHelp", "订阅源教程"),
        HelpDoc("jsHelp", "js变量和函数"),
        HelpDoc("xpathHelp", "xpath语法教程"),
        HelpDoc("regexHelp", "正则表达式教程"),
        HelpDoc("txtTocRuleHelp", "txt目录正则说明"),
        HelpDoc("debugHelp", "书源调试说明"),
        HelpDoc("httpTTSHelp", "在线朗读规则"),
        HelpDoc("webDavBookHelp", "WebDav书籍使用教程"),
        HelpDoc("webDavHelp", "WebDav备份教程"),
        HelpDoc("readMenuHelp", "阅读菜单说明"),
        HelpDoc("dictRuleHelp", "字典规则说明"),
        HelpDoc("replaceRuleHelp", "替换规则说明"),
        HelpDoc("SourceMBookHelp", "书源说明"),
        HelpDoc("SourceMRssHelp", "RSS源说明"),
        HelpDoc("ExtensionContentType", "扩展内容类型")
    )
    
    fun loadDoc(assets: AssetManager, fileName: String): String {
        return String(assets.open("web/help/md/${fileName}.md").readBytes())
    }
    
    fun getDocIndex(fileName: String): Int {
        return allHelpDocs.indexOfFirst { it.fileName == fileName }
    }
}
