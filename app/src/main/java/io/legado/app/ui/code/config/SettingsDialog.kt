package io.legado.app.ui.code.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_LINE_SEPARATOR
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_INNER
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_LEADING
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogEditSettingsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 编辑器设置对话框
 * 用于配置代码编辑器的字体大小、自动补全、不可见字符显示等
 */
class SettingsDialog(private val context: Context, private val callBack: CallBack) :
    BaseDialogFragment(R.layout.dialog_edit_settings) {
    private val binding by viewBinding(DialogEditSettingsBinding::bind)
    private val editNonPrintable = AppConfig.editNonPrintable

    /**
     * 片段创建时初始化
     */
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    /**
     * 初始化数据
     * 加载当前编辑器设置
     */
    @SuppressLint("SetTextI18n")
    private fun initData() {
        binding.run {
            tvFontSize.text = AppConfig.editFontScale.toFontSizeStr()
            cbAutoComplete.isChecked = AppConfig.editAutoComplete
            FLAGDRAWWHITESPACELEADING.isChecked = editNonPrintable and FLAG_DRAW_WHITESPACE_LEADING != 0
            FLAGDRAWWHITESPACEINNER.isChecked = editNonPrintable and FLAG_DRAW_WHITESPACE_INNER != 0
            FLAGDRAWWHITESPACETRAILING.isChecked = editNonPrintable and FLAG_DRAW_WHITESPACE_TRAILING != 0
            FLAGDRAWWHITESPACEFOREMPTYLINE.isChecked = editNonPrintable and FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE != 0
            FLAGDRAWLINESEPARATOR.isChecked = editNonPrintable and FLAG_DRAW_LINE_SEPARATOR != 0
            FLAGDRAWWHITESPACEINSELECTION.isChecked = editNonPrintable and FLAG_DRAW_WHITESPACE_IN_SELECTION != 0
        }
    }

    /**
     * 初始化视图
     * 设置字体大小选择和自动补全开关的监听器
     */
    @SuppressLint("SetTextI18n")
    private fun initView() {
        binding.run {
            tvFontSize.setOnClickListener {
                NumberPickerDialog(requireContext())
                    .setTitle(getString(R.string.font_scale))
                    .setMaxValue(36)
                    .setMinValue(9)
                    .setValue(AppConfig.editFontScale)
                    .setCustomButton((R.string.btn_default_s)) {
                        putPrefInt(PreferKey.editFontScale, 16)
                        callBack.upEdit(fontSize = 16)
                        tvFontSize.text = 16.toFontSizeStr()
                    }
                    .show {
                        putPrefInt(PreferKey.editFontScale, it)
                        callBack.upEdit(fontSize = it)
                        tvFontSize.text = it.toFontSizeStr()
                    }
            }
            cbAutoComplete.setOnCheckedChangeListener { _, isChecked ->
                putPrefBoolean(PreferKey.editAutoComplete, isChecked)
                callBack.upEdit(autoComplete = isChecked)
            }
        }
    }

    /**
     * 对话框关闭时保存设置
     * 保存不可见字符显示标志
     */
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        binding.run {
            var editNonPrintable = 0
            if (FLAGDRAWWHITESPACELEADING.isChecked) {
                editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_LEADING
            }
            if (FLAGDRAWWHITESPACEINNER.isChecked) {
                editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_INNER
            }
            if (FLAGDRAWWHITESPACETRAILING.isChecked) {
                editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_TRAILING
            }
            if (FLAGDRAWWHITESPACEFOREMPTYLINE.isChecked) {
                editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE
            }
            if (FLAGDRAWLINESEPARATOR.isChecked) {
                editNonPrintable = editNonPrintable or FLAG_DRAW_LINE_SEPARATOR
            }
            if (FLAGDRAWWHITESPACEINSELECTION.isChecked) {
                editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_IN_SELECTION
            }
            if (editNonPrintable != this@SettingsDialog.editNonPrintable) {
                putPrefInt(PreferKey.editNonPrintable, editNonPrintable)
                callBack.upEdit(editNonPrintable = editNonPrintable)
            }
        }
    }

    /**
     * 将字体缩放值转换为显示字符串
     * @return 格式化后的字体大小字符串
     */
    private fun Int.toFontSizeStr(): String {
        return context.getString(R.string.font_size, this)
    }

    /**
     * 回调接口
     * 用于通知编辑器设置更改
     */
    interface CallBack {
        fun upEdit(fontSize: Int? = null, autoComplete: Boolean? = null, autoWarp: Boolean? = null, editNonPrintable: Int? = null)
    }

}