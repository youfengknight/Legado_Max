package io.legado.app.ui.dict.rule

import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictRuleDebugBinding
import io.legado.app.databinding.DialogDictRuleEditBinding
import io.legado.app.help.config.DictDebugConfig
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext

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
            showDebugDialog(dictRule)
        }
    }

    /**
     * 显示调试对话框
     * 使用可滚动的 TextView 显示结果，并支持下拉历史记录
     * 输入框按回车键或点击搜索按钮触发搜索
     */
    private fun showDebugDialog(dictRule: DictRule) {
        // 初始化 ViewBinding
        val dialogBinding = DialogDictRuleDebugBinding.inflate(LayoutInflater.from(requireContext()))

        // 加载搜索历史记录并设置到输入框的下拉列表
        val history = DictDebugConfig.getSearchHistory()
        dialogBinding.inputView.setFilterValues(history)

        // 存储 URL 原始响应和解析结果，用于三点菜单查看源码
        var urlSrc: String = ""
        var resultSrc: String = ""

        // 搜索操作的 Lambda 表达式
        val performSearch = {
            val word = dialogBinding.inputView.text.toString()
            if (word.isBlank()) {
                toastOnUi("关键词不能为空")
            } else {
                DictDebugConfig.addSearchHistory(word)
                dialogBinding.viewResult.text = "正在搜索..."
                viewModel.debugSearch(dictRule, word) { result, urlResponse ->
                    urlSrc = urlResponse
                    resultSrc = result
                    dialogBinding.viewResult.text = resultSrc
                }
            }
        }

        // 输入框回车键监听，触發搜索
        dialogBinding.inputView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // 搜索按钮点击事件
        dialogBinding.btnSearch.setOnClickListener {
            performSearch()
        }

        // 配置 Toolbar 和菜单
        dialogBinding.toolBar.setBackgroundColor(primaryColor)
        dialogBinding.toolBar.inflateMenu(R.menu.dict_rule_debug)
        dialogBinding.toolBar.menu.applyTint(requireContext())
        // 菜单点击事件处理：查看 URL 源码和结果源码
        dialogBinding.toolBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_url_src -> {
                    if (urlSrc.isNotEmpty()) {
                        showDialogFragment(TextDialog("URL源码", urlSrc))
                    } else {
                        toastOnUi("请先执行搜索")
                    }
                }
                R.id.menu_result_src -> {
                    if (resultSrc.isNotEmpty()) {
                        showDialogFragment(TextDialog("结果源码", resultSrc))
                    } else {
                        toastOnUi("请先执行搜索")
                    }
                }
            }
            true
        }

        // 创建并显示对话框
        alert {
            customView { dialogBinding.root }
            okButton {
                performSearch()
            }
            cancelButton()
        }.apply {
            setOnShowListener {
                dialog?.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
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
         * 执行搜索并通过回调返回结果和URL源码
         * @param dictRule 字典规则
         * @param word 搜索关键词
         * @param onSuccess 成功回调 (result, urlResponse)
         */
        fun debugSearch(
            dictRule: DictRule,
            word: String,
            onSuccess: (String, String) -> Unit
        ) {
            execute {
                val analyzeUrl = AnalyzeUrl(
                    dictRule.urlRule,
                    key = word,
                    coroutineContext = currentCoroutineContext()
                )
                val response = analyzeUrl.getStrResponseAwait()
                val body = response.body ?: ""
                // 根据 showRule 判断是否需要解析
                val result = if (dictRule.showRule.isBlank()) {
                    body
                } else {
                    val analyzeRule = AnalyzeRule().setCoroutineContext(currentCoroutineContext())
                    analyzeRule.setRuleName(dictRule.name)
                    analyzeRule.getString(dictRule.showRule, mContent = body) ?: body
                }
                result to body
            }.onSuccess {
                // 回调返回解析结果和原始响应
                onSuccess.invoke(it.first, it.second)
            }.onError {
                context.toastOnUi("调试失败: ${it.localizedMessage}")
            }
        }

    }

}