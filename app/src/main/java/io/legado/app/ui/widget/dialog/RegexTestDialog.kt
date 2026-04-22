package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogRegexTestBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 正则测试弹窗
 * 
 * 用于测试正则表达式或普通文本的匹配效果，支持：
 * - 实时预览：输入时自动执行匹配
 * - 高亮显示：匹配部分用黄色背景高亮
 * - 替换预览：显示替换后的文本效果
 * - 匹配详情：显示匹配次数、每个匹配的位置索引
 * 
 * 调用示例：
 * ```
 * showDialogFragment(RegexTestDialog(pattern, replacement, isRegex))
 * ```
 */
class RegexTestDialog() : BaseDialogFragment(R.layout.dialog_regex_test) {

    private val binding by viewBinding(DialogRegexTestBinding::bind)

    private var pattern: String = ""
    private var replacement: String = ""
    private var isRegex: Boolean = true

    /**
     * 带参数构造函数
     * @param pattern 正则表达式或匹配文本
     * @param replacement 替换文本
     * @param isRegex 是否使用正则模式
     */
    constructor(pattern: String, replacement: String, isRegex: Boolean) : this() {
        arguments = Bundle().apply {
            putString("pattern", pattern)
            putString("replacement", replacement)
            putBoolean("isRegex", isRegex)
        }
    }

    override fun onStart() {
        super.onStart()
        // 设置弹窗大小：宽度匹配父容器，高度为屏幕的90%
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 从参数中获取初始值
        pattern = arguments?.getString("pattern") ?: ""
        replacement = arguments?.getString("replacement") ?: ""
        isRegex = arguments?.getBoolean("isRegex", true) ?: true

        initToolBar()
        initView()
        setupTextWatchers()
        performTest()
    }

    /**
     * 初始化工具栏
     */
    private fun initToolBar() {
        binding.toolBar.setBackgroundColor(primaryColor)
    }

    /**
     * 初始化视图，填充初始数据
     */
    private fun initView() {
        binding.etPattern.setText(pattern)
        binding.etReplacement.setText(replacement)
        binding.cbUseRegex.isChecked = isRegex
    }

    /**
     * 设置文本监听器，实现实时预览功能
     */
    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performTest()
            }
        }

        binding.etPattern.addTextChangedListener(watcher)
        binding.etReplacement.addTextChangedListener(watcher)
        binding.etTestInput.addTextChangedListener(watcher)
        binding.cbUseRegex.setOnCheckedChangeListener { _, _ -> performTest() }
    }

    /**
     * 执行匹配测试
     * 
     * 处理流程：
     * 1. 检查输入文本是否为空
     * 2. 检查匹配模式是否为空
     * 3. 如果是正则模式，验证正则语法
     * 4. 执行匹配并显示结果
     */
    private fun performTest() {
        val inputText = binding.etTestInput.text.toString()
        val patternText = binding.etPattern.text.toString()
        val replacementText = binding.etReplacement.text.toString()
        val useRegex = binding.cbUseRegex.isChecked

        // 输入为空时显示初始状态
        if (inputText.isEmpty()) {
            showEmptyState()
            return
        }

        // 匹配模式为空时显示错误
        if (patternText.isEmpty()) {
            showErrorStatus(getString(R.string.pattern_empty))
            showNoMatch()
            return
        }

        // 正则模式下验证语法
        if (useRegex) {
            try {
                Pattern.compile(patternText)
            } catch (e: PatternSyntaxException) {
                showErrorStatus(getString(R.string.regex_syntax_error, e.localizedMessage))
                showNoMatch()
                return
            }
        }

        // 执行匹配
        try {
            val matches = findMatches(inputText, patternText, useRegex)
            if (matches.isEmpty()) {
                showErrorStatus(getString(R.string.no_match_found))
                showNoMatch()
            } else {
                showSuccessStatus(getString(R.string.regex_valid_match_success))
                showMatchResult(inputText, matches, replacementText, useRegex)
            }
        } catch (e: Exception) {
            showErrorStatus(e.localizedMessage ?: getString(R.string.match_error))
            showNoMatch()
        }
    }

    /**
     * 查找所有匹配项
     * 
     * @param input 输入文本
     * @param pattern 匹配模式
     * @param isRegex 是否为正则模式
     * @return 匹配结果列表
     */
    private fun findMatches(input: String, pattern: String, isRegex: Boolean): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        
        if (isRegex) {
            // 正则模式：使用正则表达式查找所有匹配
            val regex = pattern.toRegex()
            regex.findAll(input).forEach { matchResult ->
                results.add(MatchResult(matchResult.range.first, matchResult.range.last + 1, matchResult.value))
            }
        } else {
            // 普通模式：使用字符串查找
            var startIndex = 0
            while (true) {
                val index = input.indexOf(pattern, startIndex)
                if (index == -1) break
                results.add(MatchResult(index, index + pattern.length, pattern))
                startIndex = index + pattern.length
            }
        }
        
        return results
    }

    /**
     * 显示匹配结果
     * 
     * @param input 原始输入文本
     * @param matches 匹配结果列表
     * @param replacement 替换文本
     * @param isRegex 是否为正则模式
     */
    private fun showMatchResult(input: String, matches: List<MatchResult>, replacement: String, isRegex: Boolean) {
        // 显示匹配数量
        binding.tvMatchCount.text = getString(R.string.match_count_format, matches.size)

        // 高亮显示匹配部分
        val highlightedText = SpannableStringBuilder(input)
        for (match in matches) {
            highlightedText.setSpan(
                BackgroundColorSpan(0xFFFFFF00.toInt()), // 黄色背景
                match.start,
                match.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.tvMatchResult.text = highlightedText

        // 计算并显示替换预览
        val replacedText = if (isRegex) {
            input.replace(binding.etPattern.text.toString().toRegex(), replacement)
        } else {
            input.replace(binding.etPattern.text.toString(), replacement)
        }

        // 有替换文本时显示替换预览
        if (replacement.isNotEmpty()) {
            binding.llReplacePreview.visibility = View.VISIBLE
            binding.tvReplacePreview.text = replacedText
        } else {
            binding.llReplacePreview.visibility = View.GONE
        }

        // 显示匹配详情（最多显示10个）
        val infoBuilder = StringBuilder()
        infoBuilder.append(getString(R.string.match_times, matches.size))
        matches.take(10).forEachIndexed { index, match ->
            infoBuilder.append("\n").append(getString(R.string.match_position, index + 1, match.start, match.end))
        }
        if (matches.size > 10) {
            infoBuilder.append("\n...").append(getString(R.string.more_matches, matches.size - 10))
        }
        binding.tvMatchInfo.text = infoBuilder.toString()
        binding.llMatchInfo.visibility = View.VISIBLE
    }

    /**
     * 显示空状态（初始状态）
     */
    private fun showEmptyState() {
        binding.tvMatchCount.text = ""
        binding.tvMatchResult.text = getString(R.string.input_test_text_hint)
        binding.llReplacePreview.visibility = View.GONE
        binding.llMatchInfo.visibility = View.GONE
        binding.llStatus.visibility = View.GONE
    }

    /**
     * 显示无匹配状态
     */
    private fun showNoMatch() {
        binding.tvMatchCount.text = getString(R.string.zero_match)
        binding.tvMatchResult.text = binding.etTestInput.text.toString()
        binding.llReplacePreview.visibility = View.GONE
        binding.llMatchInfo.visibility = View.GONE
    }

    /**
     * 显示成功状态（绿色背景 + 对勾图标）
     * @param message 状态消息
     */
    private fun showSuccessStatus(message: String) {
        binding.llStatus.visibility = View.VISIBLE
        binding.llStatus.setBackgroundResource(R.drawable.bg_status_success)
        binding.ivStatusIcon.setImageResource(R.drawable.ic_check)
        binding.ivStatusIcon.setColorFilter(resources.getColor(R.color.regex_status_success, null))
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(resources.getColor(R.color.regex_status_success, null))
    }

    /**
     * 显示错误状态（红色背景 + 错误图标）
     * @param message 状态消息
     */
    private fun showErrorStatus(message: String) {
        binding.llStatus.visibility = View.VISIBLE
        binding.llStatus.setBackgroundResource(R.drawable.bg_status_error)
        binding.ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
        binding.ivStatusIcon.setColorFilter(resources.getColor(R.color.regex_status_error, null))
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(resources.getColor(R.color.regex_status_error, null))
    }

    /**
     * 匹配结果数据类
     * @param start 匹配起始位置
     * @param end 匹配结束位置
     * @param value 匹配的文本内容
     */
    private data class MatchResult(val start: Int, val end: Int, val value: String)

}
