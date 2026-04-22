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

class RegexTestDialog() : BaseDialogFragment(R.layout.dialog_regex_test) {

    private val binding by viewBinding(DialogRegexTestBinding::bind)

    private var pattern: String = ""
    private var replacement: String = ""
    private var isRegex: Boolean = true

    constructor(pattern: String, replacement: String, isRegex: Boolean) : this() {
        arguments = Bundle().apply {
            putString("pattern", pattern)
            putString("replacement", replacement)
            putBoolean("isRegex", isRegex)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        pattern = arguments?.getString("pattern") ?: ""
        replacement = arguments?.getString("replacement") ?: ""
        isRegex = arguments?.getBoolean("isRegex", true) ?: true

        initToolBar()
        initView()
        setupTextWatchers()
        performTest()
    }

    private fun initToolBar() {
        binding.toolBar.setBackgroundColor(primaryColor)
    }

    private fun initView() {
        binding.etPattern.setText(pattern)
        binding.etReplacement.setText(replacement)
        binding.cbUseRegex.isChecked = isRegex
    }

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

    private fun performTest() {
        val inputText = binding.etTestInput.text.toString()
        val patternText = binding.etPattern.text.toString()
        val replacementText = binding.etReplacement.text.toString()
        val useRegex = binding.cbUseRegex.isChecked

        if (inputText.isEmpty()) {
            showEmptyState()
            return
        }

        if (patternText.isEmpty()) {
            showErrorStatus(getString(R.string.pattern_empty))
            showNoMatch()
            return
        }

        if (useRegex) {
            try {
                Pattern.compile(patternText)
            } catch (e: PatternSyntaxException) {
                showErrorStatus(getString(R.string.regex_syntax_error, e.localizedMessage))
                showNoMatch()
                return
            }
        }

        try {
            val matches = findMatches(inputText, patternText, useRegex)
            if (matches.isEmpty()) {
                showSuccessStatus(getString(R.string.no_match_found))
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

    private fun findMatches(input: String, pattern: String, isRegex: Boolean): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        
        if (isRegex) {
            val regex = pattern.toRegex()
            regex.findAll(input).forEach { matchResult ->
                results.add(MatchResult(matchResult.range.first, matchResult.range.last + 1, matchResult.value))
            }
        } else {
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

    private fun showMatchResult(input: String, matches: List<MatchResult>, replacement: String, isRegex: Boolean) {
        binding.tvMatchCount.text = getString(R.string.match_count_format, matches.size)

        val highlightedText = SpannableStringBuilder(input)
        for (match in matches) {
            highlightedText.setSpan(
                BackgroundColorSpan(0xFFFFFF00.toInt()),
                match.start,
                match.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.tvMatchResult.text = highlightedText

        val replacedText = if (isRegex) {
            input.replace(binding.etPattern.text.toString().toRegex(), replacement)
        } else {
            input.replace(binding.etPattern.text.toString(), replacement)
        }

        if (replacement.isNotEmpty()) {
            binding.llReplacePreview.visibility = View.VISIBLE
            binding.tvReplacePreview.text = replacedText
        } else {
            binding.llReplacePreview.visibility = View.GONE
        }

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

    private fun showEmptyState() {
        binding.tvMatchCount.text = ""
        binding.tvMatchResult.text = getString(R.string.input_test_text_hint)
        binding.llReplacePreview.visibility = View.GONE
        binding.llMatchInfo.visibility = View.GONE
        binding.llStatus.visibility = View.GONE
    }

    private fun showNoMatch() {
        binding.tvMatchCount.text = getString(R.string.zero_match)
        binding.tvMatchResult.text = binding.etTestInput.text.toString()
        binding.llReplacePreview.visibility = View.GONE
        binding.llMatchInfo.visibility = View.GONE
    }

    private fun showSuccessStatus(message: String) {
        binding.llStatus.visibility = View.VISIBLE
        binding.llStatus.setBackgroundResource(R.drawable.bg_status_success)
        binding.ivStatusIcon.setImageResource(R.drawable.ic_check)
        binding.ivStatusIcon.setColorFilter(0xFF388E3C.toInt())
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(0xFF388E3C.toInt())
    }

    private fun showErrorStatus(message: String) {
        binding.llStatus.visibility = View.VISIBLE
        binding.llStatus.setBackgroundResource(R.drawable.bg_status_error)
        binding.ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
        binding.ivStatusIcon.setColorFilter(0xFFD32F2F.toInt())
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(0xFFD32F2F.toInt())
    }

    private data class MatchResult(val start: Int, val end: Int, val value: String)

}
