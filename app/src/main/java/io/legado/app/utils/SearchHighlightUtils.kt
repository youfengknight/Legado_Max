package io.legado.app.utils

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan

/**
 * 搜索高亮工具类
 * 提供文本搜索过滤和高亮显示功能
 */
object SearchHighlightUtils {

    /**
     * 高亮显示匹配的文字
     * 使用 BackgroundColorSpan 将匹配部分标记为指定颜色背景
     *
     * @param text 原始文本
     * @param query 搜索关键词
     * @param highlightColor 高亮背景颜色，默认黄色
     * @return 带高亮效果的 Spannable 文本
     */
    fun highlightText(
        text: String,
        query: String,
        highlightColor: Int = Color.YELLOW
    ): Spannable {
        val spannable = SpannableStringBuilder(text)
        if (query.isEmpty()) return spannable

        var startIndex = 0
        while (true) {
            val index = text.indexOf(query, startIndex, ignoreCase = true)
            if (index < 0) break
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                index,
                index + query.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + query.length
        }
        return spannable
    }

    /**
     * 根据搜索关键词过滤列表
     *
     * @param items 原始列表
     * @param query 搜索关键词
     * @param matcher 匹配函数，返回 true 表示匹配成功
     * @return 过滤后的列表
     */
    fun <T> filterList(
        items: List<T>,
        query: String,
        matcher: (T, String) -> Boolean
    ): List<T> {
        if (query.isEmpty()) return items
        return items.filter { matcher(it, query) }
    }

    /**
     * 检查文本是否包含搜索关键词（忽略大小写）
     *
     * @param text 原始文本
     * @param query 搜索关键词
     * @return 是否包含
     */
    fun containsIgnoreCase(text: String?, query: String): Boolean {
        if (text == null || query.isEmpty()) return false
        return text.contains(query, ignoreCase = true)
    }

    /**
     * 获取带高亮的文本，如果匹配则返回高亮文本，否则返回原始文本
     *
     * @param text 原始文本
     * @param query 搜索关键词
     * @param highlightColor 高亮背景颜色
     * @return 原始文本或高亮文本
     */
    fun getHighlightedText(
        text: String,
        query: String,
        highlightColor: Int = Color.YELLOW
    ): CharSequence {
        return if (query.isNotEmpty() && containsIgnoreCase(text, query)) {
            highlightText(text, query, highlightColor)
        } else {
            text
        }
    }
}