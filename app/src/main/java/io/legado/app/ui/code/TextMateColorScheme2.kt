package io.legado.app.ui.code

import androidx.core.graphics.toColorInt
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel

/**
 * TextMate 配色方案扩展类
 * 继承自 TextMateColorScheme，提供自定义颜色配置
 * 支持深色和浅色主题的额外颜色设置
 */
class TextMateColorScheme2(themeRegistry: ThemeRegistry, themeModel: ThemeModel) : TextMateColorScheme(themeRegistry, themeModel) {
    companion object {
        /**
         * 创建配色方案实例
         * @param themeRegistry 主题注册器
         * @return 配色方案实例
         */
        fun create(themeRegistry: ThemeRegistry): TextMateColorScheme2 {
            return create(ThemeRegistry.getInstance(), themeRegistry.currentThemeModel)
        }
        /**
         * 创建配色方案实例
         * @param themeRegistry 主题注册器
         * @param themeModel 主题模型
         * @return 配色方案实例
         */
        fun create(themeRegistry: ThemeRegistry, themeModel: ThemeModel): TextMateColorScheme2 {
            return TextMateColorScheme2(themeRegistry, themeModel)
        }
    }

    /**
     * 应用默认颜色
     * 根据主题类型应用深色或浅色颜色配置
     */
    override fun applyDefault() {
        super.applyDefault()
        if (isDark)
            applyDarkThemeColors()
        else
            applyLightThemeColors()
    }

    /**
     * 应用深色主题颜色
     * 配置选中括号、滚动条、行号面板等颜色
     */
    private fun applyDarkThemeColors() {
        setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, "#60FFFFFF".toColorInt()) // 选中括号
        setColor(SCROLL_BAR_THUMB, "#FF27292A".toColorInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, "#90D8D8D8".toColorInt()) // 滚动条反色
        setColor(LINE_NUMBER_PANEL_TEXT, "#80D8D8D8".toColorInt()) // 滚动条提示文本色
    }

    /**
     * 应用浅色主题颜色
     * 配置选中括号等颜色
     */
    private fun applyLightThemeColors() {
        setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, "#60000000".toColorInt())
    }
}
