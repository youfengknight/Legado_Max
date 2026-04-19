package io.legado.app.ui.dict.rule

import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictRuleEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

class DictRuleEditDialog() : BaseDialogFragment(R.layout.dialog_dict_rule_edit, true),
    Toolbar.OnMenuItemClickListener {

    val viewModel by viewModels<DictRuleEditViewModel>()
    val binding by viewBinding(DialogDictRuleEditBinding::bind)
    private var focusedEditText: EditText? = null

    constructor(name: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.dict_rule_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        viewModel.initData(arguments?.getString("name")) {
            upRuleView(viewModel.dictRule)
        }
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val view = focusedEditText
            if (view == null) {
                toastOnUi(R.string.focus_lost_on_textbox)
                return@registerForActivityResult
            }
            view.requestFocus()
            result.data?.getStringExtra("text")?.let {
                view.setText(it)
            }
            result.data?.getIntExtra("cursorPosition", -1)?.takeIf { it in 0 ..< view.text.length }?.let {
                view.setSelection(it)
            }
        }
    }
    private fun onFullEditClicked() {
        val view = dialog?.window?.decorView?.findFocus()
        if (view is EditText) {
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            focusedEditText = view
            val currentText = view.text.toString()
            val intent = Intent(requireActivity(), CodeEditActivity::class.java).apply {
                putExtra("text", currentText)
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
            }
            textEditLauncher.launch(intent)
        }
        else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            // 调试按钮：保存规则后弹出输入框，输入关键词进行调试
            R.id.menu_debug -> onDebugClicked()
            // 全屏编辑按钮：跳转到代码编辑界面
            R.id.menu_fullscreen_edit -> onFullEditClicked()
            // 保存按钮：保存规则并关闭对话框
            R.id.menu_save -> viewModel.save(getDictRule()) {
                dismissAllowingStateLoss()
            }
            // 复制规则按钮：将规则JSON复制到剪贴板
            R.id.menu_copy_rule -> viewModel.copyRule(getDictRule())
            // 粘贴规则按钮：从剪贴板粘贴规则JSON
            R.id.menu_paste_rule -> viewModel.pasteRule {
                upRuleView(it)
            }
        }
        return true
    }

    /**
     * 调试按钮点击处理
     * 检查规则名称是否为空，保存规则后弹出调试对话框
     */
    private fun onDebugClicked() {
        val dictRule = getDictRule()
        // 规则名称不能为空
        if (dictRule.name.isBlank()) {
            toastOnUi(R.string.cannot_empty)
            return
        }
        // 先保存规则，再进行调试
        viewModel.save(dictRule) {
            viewModel.debug(dictRule)
        }
    }

    private fun upRuleView(dictRule: DictRule?) {
        binding.tvRuleName.setText(dictRule?.name)
        binding.tvUrlRule.setText(dictRule?.urlRule)
        binding.tvShowRule.apply{
            addLegadoPattern()
            addJsonPattern()
            addJsPattern()
            setText(dictRule?.showRule)
        }
    }

    private fun getDictRule(): DictRule {
        val dictRule = viewModel.dictRule?.copy() ?: DictRule()
        dictRule.name = binding.tvRuleName.text.toString()
        dictRule.urlRule = binding.tvUrlRule.text.toString()
        dictRule.showRule = binding.tvShowRule.text.toString()
        return dictRule
    }

    private fun isSame(): Boolean{
        val dictRule = viewModel.dictRule ?: return binding.tvRuleName.text.toString().isEmpty()
        return dictRule.name == binding.tvRuleName.text.toString() &&
        dictRule.urlRule == binding.tvUrlRule.text.toString() &&
        dictRule.showRule == binding.tvShowRule.text.toString()
    }

    override fun dismiss() {
        if (!isSame()) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) {
                    super.dismiss()
                }
            }
        } else {
            super.dismiss()
        }
    }

    class DictRuleEditViewModel(application: Application) : BaseViewModel(application) {

        var dictRule: DictRule? = null

        fun initData(name: String?, onFinally: () -> Unit) {
            execute {
                if (dictRule == null && name != null) {
                    dictRule = appDb.dictRuleDao.getByName(name)
                }
            }.onFinally {
                onFinally.invoke()
            }
        }

        fun save(newDictRule: DictRule, onFinally: () -> Unit) {
            execute {
                dictRule?.let {
                    appDb.dictRuleDao.delete(it)
                }
                appDb.dictRuleDao.insert(newDictRule)
                dictRule = newDictRule
            }.onFinally {
                onFinally.invoke()
            }
        }

        fun copyRule(dictRule: DictRule) {
            context.sendToClip(GSON.toJson(dictRule))
        }

        fun pasteRule(success: (DictRule) -> Unit) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                context.toastOnUi("剪贴板没有内容")
                return
            }
            execute {
                GSON.fromJsonObject<DictRule>(text).getOrThrow()
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi("格式不对")
            }
        }

        /**
         * 调试字典规则
         * 弹出对话框让用户输入关键词，然后调用规则的search方法进行搜索
         * 搜索结果在对话框中显示
         */
        fun debug(dictRule: DictRule) {
            // 弹出输入对话框，让用户输入搜索关键词
            context.alert("调试字典规则") {
                val input = android.widget.EditText(context).apply {
                    hint = "输入关键词"
                }
                customView { input }
                okButton {
                    val word = input.text.toString()
                    // 关键词不能为空
                    if (word.isBlank()) {
                        context.toastOnUi("关键词不能为空")
                        return@okButton
                    }
                    // 执行搜索并显示结果
                    execute {
                        dictRule.search(word)
                    }.onSuccess {
                        // 显示调试结果对话框
                        context.alert("调试结果") {
                            setMessage(it)
                            okButton()
                        }.show()
                    }.onError {
                        // 显示错误提示
                        context.toastOnUi("调试失败: ${it.localizedMessage}")
                    }
                }
                cancelButton()
            }.show()
        }

    }

}