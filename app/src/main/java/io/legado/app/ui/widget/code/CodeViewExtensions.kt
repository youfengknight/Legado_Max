@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import splitties.init.appCtx
import splitties.resources.color
import java.util.regex.Pattern

/**
 * 阅读规则语法模式
 * 匹配 ||、&&、%%、@js:、@Json:、@css:、@@、@XPath:、@webjs: 等阅读规则关键字
 */
val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@js:|@Json:|@css:|@@|@XPath:|@webjs:")

/**
 * JSON语法模式
 * 匹配JSON中的键名、引号、花括号、方括号
 */
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")

/**
 * 换行符模式
 * 匹配转义的换行符 \n
 */
val wrapPattern: Pattern = Pattern.compile("\\\\n")

/**
 * 运算符模式
 * 匹配各种运算符符号
 */
val operationPattern: Pattern =
    Pattern.compile(":|==|>|<|!=|>=|<=|->|=|%|-|-=|%=|\\+|\\-|\\-=|\\+=|\\^|\\&|\\|::|\\?|\\*")

/**
 * JavaScript关键字模式
 * 匹配 var、let、const 关键字
 */
val jsPattern: Pattern = Pattern.compile("\\b(?:var|let|const)\\b")

/**
 * HTML标签模式
 * 匹配HTML标签，如 <div>、</div>、<img src="..."/>
 */
val htmlTagPattern: Pattern = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*[^>]*>")

/**
 * HTML属性模式
 * 匹配HTML属性名，如 class=、id=、style=
 */
val htmlAttrPattern: Pattern = Pattern.compile("\\s[a-zA-Z-]+=")

/**
 * HTML注释模式
 * 匹配HTML注释 <!-- ... -->
 */
val htmlCommentPattern: Pattern = Pattern.compile("<!--.*?-->")

/**
 * 模板变量模式
 * 匹配模板变量 {{variableName}}，用于封面HTML模板中的变量替换
 */
val htmlVariablePattern: Pattern = Pattern.compile("\\{\\{[a-zA-Z]+\\}\\}")

/**
 * 为CodeView添加阅读规则语法高亮
 */
fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, appCtx.color(R.color.md_orange_900))
}

/**
 * 为CodeView添加JSON语法高亮
 */
fun CodeView.addJsonPattern() {
    addSyntaxPattern(jsonPattern, appCtx.color(R.color.md_blue_800))
}

/**
 * 为CodeView添加JavaScript语法高亮
 */
fun CodeView.addJsPattern() {
    addSyntaxPattern(wrapPattern, appCtx.color(R.color.md_blue_grey_500))
    addSyntaxPattern(operationPattern, appCtx.color(R.color.md_orange_900))
    addSyntaxPattern(jsPattern, appCtx.color(R.color.md_light_blue_600))
}

/**
 * 为CodeView添加HTML语法高亮
 * 
 * 包含以下高亮规则：
 * - HTML标签：红色
 * - HTML属性：橙色
 * - HTML注释：灰色
 * - 模板变量（如{{bookName}}）：绿色
 */
fun CodeView.addHtmlPattern() {
    addSyntaxPattern(htmlTagPattern, appCtx.color(R.color.md_red_700))
    addSyntaxPattern(htmlAttrPattern, appCtx.color(R.color.md_orange_800))
    addSyntaxPattern(htmlCommentPattern, appCtx.color(R.color.md_grey_500))
    addSyntaxPattern(htmlVariablePattern, appCtx.color(R.color.md_green_700))
}

/**
 * 创建关键词自动补全适配器
 * 
 * @param keywords 关键词数组
 * @return ArrayAdapter 适配器
 */
fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}
