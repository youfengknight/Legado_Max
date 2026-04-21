package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCoverHtmlCodeBinding
import io.legado.app.help.DefaultData
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onClick

/**
 * HTML封面代码配置对话框
 * 
 * 用于配置自定义HTML模板来生成封面图片，支持以下功能：
 * - 启用/禁用HTML封面生成
 * - 输入书名和作者进行实时预览
 * - 编辑HTML代码模板（支持语法高亮）
 * - WebView实时渲染预览效果
 * 
 * 支持的变量：
 * - {{bookName}}: 书名
 * - {{author}}: 作者
 */
class CoverHtmlCodeDialog : BaseDialogFragment(R.layout.dialog_cover_html_code) {

    val binding by viewBinding(DialogCoverHtmlCodeBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        initWebView()
        initCodeView()
        initData()
        initClickListeners()
    }

    /**
     * 初始化WebView配置
     * 
     * 配置WebView用于预览HTML封面效果：
     * - 启用JavaScript支持
     * - 禁用缩放功能
     * - 设置自适应布局
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        binding.webViewPreview.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        binding.webViewPreview.webViewClient = WebViewClient()
    }

    /**
     * 初始化代码编辑器
     * 
     * 为代码编辑器添加HTML和JavaScript语法高亮支持
     */
    private fun initCodeView() {
        binding.codeView.addHtmlPattern()
        binding.codeView.addJsPattern()
    }

    /**
     * 初始化数据
     * 
     * 从存储中加载已保存的配置，若未配置则使用默认模板
     */
    private fun initData() {
        lifecycleScope.launch {
            val config = withContext(Dispatchers.IO) {
                BookCover.getCoverHtmlCode()
            }
            binding.cbEnable.isChecked = config.enable
            binding.codeView.setText(config.htmlCode.ifBlank {
                DefaultData.coverHtmlTemplate
            })
            binding.editBookName.setText("示例书名")
            binding.editAuthor.setText("示例作者")
        }
    }

    /**
     * 初始化点击事件监听
     */
    private fun initClickListeners() {
        // 预览按钮：渲染HTML并在WebView中显示
        binding.tvPreview.onClick {
            previewCover()
        }

        // 取消按钮：关闭对话框
        binding.tvCancel.onClick {
            dismissAllowingStateLoss()
        }

        // 确定按钮：保存配置并关闭对话框
        binding.tvOk.onClick {
            saveConfig()
            dismissAllowingStateLoss()
        }

        // 恢复默认按钮：重置为默认模板
        binding.tvFooterLeft.onClick {
            BookCover.delCoverHtmlCode()
            binding.codeView.setText(DefaultData.coverHtmlTemplate)
            binding.cbEnable.isChecked = false
        }
    }

    /**
     * 预览封面
     * 
     * 获取用户输入的书名、作者和HTML模板，
     * 替换变量后在WebView中渲染预览
     */
    private fun previewCover() {
        val htmlTemplate = binding.codeView.text?.toString() ?: return
        val bookName = binding.editBookName.text?.toString() ?: "书名"
        val author = binding.editAuthor.text?.toString() ?: "作者"

        val renderedHtml = renderHtml(htmlTemplate, bookName, author)
        binding.webViewPreview.loadDataWithBaseURL(
            null,
            renderedHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * 渲染HTML模板
     * 
     * 将模板中的变量替换为实际值
     * 
     * @param template HTML模板字符串
     * @param bookName 书名
     * @param author 作者
     * @return 渲染后的HTML字符串
     */
    private fun renderHtml(template: String, bookName: String, author: String): String {
        return template
            .replace("{{bookName}}", bookName)
            .replace("{{author}}", author)
    }

    /**
     * 保存配置
     * 
     * 将当前启用状态和HTML代码保存到存储
     */
    private fun saveConfig() {
        val enable = binding.cbEnable.isChecked
        val htmlCode = binding.codeView.text?.toString() ?: ""
        BookCover.saveCoverHtmlCode(BookCover.CoverHtmlCode(enable, htmlCode))
    }

}
