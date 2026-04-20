package io.legado.app.ui.debug

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityRegexTestBinding
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

class RegexTestActivity : BaseActivity<ActivityRegexTestBinding>() {

    override val binding by lazy { ActivityRegexTestBinding.inflate(layoutInflater) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initClick()
    }

    private fun initClick() {
        binding.btnTest.setOnClickListener {
            testRegex()
        }
        binding.btnCopy.setOnClickListener {
            val result = binding.tvResult.text.toString()
            if (result.isNotEmpty()) {
                sendToClip(result)
            }
        }
        binding.btnClear.setOnClickListener {
            binding.etPattern.setText("")
            binding.etInput.setText("")
            binding.tvResult.text = ""
            binding.tvMatches.text = ""
        }
    }

    private fun testRegex() {
        val patternStr = binding.etPattern.text.toString()
        val input = binding.etInput.text.toString()

        if (patternStr.isEmpty()) {
            toastOnUi(R.string.debug_pattern_empty)
            return
        }
        if (input.isEmpty()) {
            toastOnUi(R.string.input_is_empty)
            return
        }

        try {
            val regexOptions = mutableSetOf<RegexOption>()
            if (binding.cbIgnoreCase.isChecked) {
                regexOptions.add(RegexOption.IGNORE_CASE)
            }
            if (binding.cbMultiline.isChecked) {
                regexOptions.add(RegexOption.MULTILINE)
            }
            if (binding.cbDotAll.isChecked) {
                regexOptions.add(RegexOption.DOT_MATCHES_ALL)
            }

            val regex = Regex(patternStr, regexOptions)
            val matches = regex.findAll(input).toList()

            if (matches.isEmpty()) {
                binding.tvResult.text = getString(R.string.debug_no_match)
                binding.tvMatches.text = ""
                return
            }

            val sb = StringBuilder()
            matches.forEachIndexed { index, match ->
                sb.append("匹配 ${index + 1}:\n")
                sb.append("  完整匹配: ${match.value}\n")
                match.groupValues.forEachIndexed { groupIndex, groupValue ->
                    if (groupIndex > 0) {
                        sb.append("  分组 $groupIndex: $groupValue\n")
                    }
                }
                sb.append("\n")
            }
            binding.tvResult.text = sb.toString()

            val spannableString = SpannableString(input)
            val highlightColor = Color.parseColor("#40FFEB3B")
            matches.forEach { match ->
                spannableString.setSpan(
                    BackgroundColorSpan(highlightColor),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.tvMatches.text = spannableString

        } catch (e: Exception) {
            binding.tvResult.text = "错误: ${e.message}"
            binding.tvMatches.text = input
        }
    }
}
