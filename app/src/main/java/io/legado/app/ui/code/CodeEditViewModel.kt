package io.legado.app.ui.code

import android.app.Application
import android.content.Intent
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import org.eclipse.tm4e.core.registry.IThemeSource
import org.jsoup.Jsoup
import splitties.init.appCtx

/**
 * 代码编辑视图模型
 * 管理代码编辑器的语言、主题、格式化等功能
 * 支持 TextMate 语法高亮和代码格式化
 */
class CodeEditViewModel(application: Application) : BaseViewModel(application) {
    private val themeFileNames = arrayOf(
        "d_monokai_dimmed",
        "d_monokai",
        "d_modern",
        "l_modern",
        "d_solarized",
        "l_solarized",
        "d_abyss",
        "l_quiet"
    )

    var initialText = ""
    var cursorPosition = 0
    var language: TextMateLanguage? = null
    private var languageName = "source.js"
    private val themeRegistry: ThemeRegistry = ThemeRegistry.getInstance()
    var writable = true
    var title: String? = null

    /**
     * 初始化 Sora 编辑器
     * 加载 TextMate 语法和主题配置
     */
    fun initSora() {
        //初始化sora加载
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(appCtx.assets)
        )
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    /**
     * 初始化数据
     * 从 Intent 或缓存中加载文本内容，设置语言类型
     * @param intent 包含初始化数据的 Intent
     * @param success 初始化成功回调
     */
    fun initData(
        intent: Intent, success: () -> Unit
    ) {
        execute {
            val cacheKey = intent.getStringExtra("cacheKey")
            if (cacheKey != null) {
                val cacheText = CacheManager.getFromMemory(cacheKey) as? String ?: throw Exception("未获取到查看文本")
                writable = false
                initialText = cacheText
            } else {
                initialText = intent.getStringExtra("text") ?: throw Exception("未获取到待编辑文本")
            }
            if (isHtmlStr(initialText)) {
                languageName = "text.html.basic"
            } else {
                intent.getStringExtra("languageName")?.let { languageName = it }
            }
            language = TextMateLanguage.create(languageName, AppConfig.editAutoComplete)
            cursorPosition = intent.getIntExtra("cursorPosition", 0)
            title = intent.getStringExtra("title")
        }.onSuccess {
            success.invoke()
        }.onError {
            context.toastOnUi("error\n${it.localizedMessage}")
            it.printOnDebug()
        }
    }

    /**
     * 判断文本是否为 HTML 格式
     * @param text 待检测文本
     * @return 是否为 HTML 格式
     */
    private fun isHtmlStr(text: String): Boolean {
        val trimmedText = text.trim()
        val htmlRegex = Regex("""^(?:\[[\s\d.]])?<(?:html|!DOCTYPE)""", RegexOption.IGNORE_CASE)
        return htmlRegex.containsMatchIn(trimmedText) && trimmedText.endsWith(">")
    }

    /**
     * 加载 TextMate 主题
     * @param index 主题索引
     */
    fun loadTextMateThemes(index: Int) {
        val theme = themeFileNames.getOrElse(index) { "d_monokai" }
        val themeModel = themeRegistry.findThemeByFileName(theme)
        if (themeModel == null) {
            val themeAssetsPath = "textmate/$theme.json"
            val themeSource = IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath),
                themeAssetsPath,
                null
            )
            themeRegistry.loadTheme(ThemeModel(themeSource, theme).apply {
                isDark = theme.startsWith("d_")
            })
        } else {
            themeRegistry.setTheme(themeModel)
        }
    }

    /**
     * 格式化代码
     * 支持 JavaScript、HTML 等语言的格式化
     * 使用 js-beautify 库进行格式化
     * @param editor 代码编辑器实例
     */
    fun formatCode(editor: CodeEditor) {
        execute {
            val text = editor.text.toString()
            if (languageName.contains("markdown")) {
                context.toastOnUi("markdown不需要格式化")
                return@execute text
            }
            val isHtml = languageName.contains("html")
            if (isHtml) {
                return@execute formatCodeHtml(text)
            }
            var result = ""
            var start = 0
            val indexS = text.indexOf("<js>")
            if (indexS >= 0) {
                if (indexS > 0) {
                    result += text.substring(start, indexS).trim()
                }
                val indexE = text.indexOf("</js>", indexS)
                val jsCode = text.substring(indexS + 4, indexE)
                result += "<js>\n"
                result += webFormatCode(jsCode)
                result += "\n</js>"
                start = indexE + 5
            }
            val indexS2 = text.indexOf("@js:")
            if (indexS2 >= 0) {
                if (indexS2 > start) {
                    result += text.substring(start, indexS2).trim()
                }
                val jsCode = text.substring(indexS2 + 4)
                result += "@js:\n"
                result += webFormatCode(jsCode)
                start = text.length
            } else {
                val indexS2 = text.indexOf("@webjs:")
                if (indexS2 >= 0) {
                    if (indexS2 > start) {
                        result += text.substring(start, indexS2).trim()
                    }
                    val jsCode = text.substring(indexS2 + 7)
                    result += "@webjs:\n"
                    result += webFormatCode(jsCode)
                    start = text.length
                }
            }
            if (start == 0) {
                result += webFormatCode(text)
                start = text.length
            }
            if (text.length > start) {
                result += text.substring(start).trim()
            }
            result
        }.onSuccess {
            editor.setText(it)
        }.onError {
            AppLog.put("格式化失败",it, true)
        }
    }

    /**
     * 使用 WebView 格式化 JavaScript 代码
     * 通过 js-beautify 库进行格式化
     * @param jsCode JavaScript 代码
     * @return 格式化后的代码
     */
    private suspend fun webFormatCode(jsCode: String): String? {
        CacheManager.putMemory("web_format_code", jsCode)
        return BackstageWebView(
            url = null,
            html = """<html><body><script src="https://cdnjs.cloudflare.com/ajax/libs/js-beautify/1.15.4/beautify.min.js"></script>
                <script>
                window.re = js_beautify($nameCache.getFromMemory('web_format_code'), {
                indent_size: 4,
                indent_char: ' ',
                preserve_newlines: true,
                max_preserve_newlines: 5,
                brace_style: 'collapse',
                space_before_conditional: true,
                unescape_strings: false,
                jslint_happy: false,
                end_with_newline: false,
                wrap_line_length: 0,
                comma_first: false
                });
                </script></body></html>""".trimIndent(),
            javaScript = "window.re",
            cacheFirst = true,
            timeout = 5000,
            isRule = true
        ).getStrResponse().body
    }

    /**
     * 格式化 HTML 代码
     * 使用 Jsoup 库进行格式化
     * @param html HTML 代码
     * @return 格式化后的 HTML
     */
    private fun formatCodeHtml(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.outputSettings()
            .indentAmount(4)
            .prettyPrint(true)
        return doc.outerHtml()
    }

}