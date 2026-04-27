package io.legado.app.ui.widget.dialog

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogTextViewBinding
import io.legado.app.help.CacheManager
import io.legado.app.help.HelpDocManager
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.utils.applyTint
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.help.InnerBrowserLinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本弹窗，支持显示Markdown、HTML、普通文本
 */
class TextDialog() : BaseDialogFragment(R.layout.dialog_text_view) {

    // 显示模式枚举
    enum class Mode {
        MD, HTML, TEXT
    }

    // 普通文本弹窗构造函数
    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        time: Long = 0,
        autoClose: Boolean = false
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putLong("time", time)
        }
        isCancelable = false
        this.autoClose = autoClose
    }

    // 帮助文档弹窗构造函数
    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        helpDocName: String? = null
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putString("helpDocName", helpDocName)
        }
        isHelpMode = helpDocName != null
        currentHelpDoc = helpDocName
    }

    private val binding by viewBinding(DialogTextViewBinding::bind)
    private var time = 0L // 自动关闭倒计时
    private var autoClose: Boolean = false // 倒计时结束后是否自动关闭
    private var isHelpMode: Boolean = false // 是否为帮助文档模式
    private var currentHelpDoc: String? = null // 当前帮助文档文件名
    // 追踪当前显示的内容，切换帮助文档时同步更新，确保打开编辑器时获取的是最新内容
    private var currentContent: String? = null
    private var markwon: Markwon? = null // Markdown渲染器

    companion object {
        private const val TAG = "TextDialog"
    }

    override fun onStart() {
        // 设置弹窗大小为屏幕宽度的MATCH_PARENT，高度为90%
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置工具栏颜色
        binding.toolBar.setBackgroundColor(primaryColor)
        // 加载菜单
        binding.toolBar.inflateMenu(R.menu.dialog_text)
        // 应用菜单着色
        binding.toolBar.menu.applyTint(requireContext())
        // 处理传递的参数
        arguments?.let {
            val title = it.getString("title")
            binding.toolBar.title = title
            val content = IntentData.get(it.getString("content")) ?: ""
            currentContent = content
            val mode = it.getString("mode")
            when (mode) {
                Mode.MD.name -> viewLifecycleOwner.lifecycleScope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        binding.textView.setTextClassifier(TextClassifier.NO_OP)
                    }
                    markwon = Markwon.builder(requireContext())
                        .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                                builder.linkResolver(InnerBrowserLinkResolver)
                            }
                        })
                        .usePlugin(GlideImagesPlugin.create(Glide.with(requireContext())))
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(requireContext()))
                        .build()
                    val markdown = withContext(IO) {
                        markwon!!.toMarkdown(content)
                    }
                    binding.textView.setMarkdown(
                        markwon!!,
                        markdown,
                        imgOnLongClickListener = { source  ->
                            showDialogFragment(PhotoDialog(source))
                        }
                    )
                }

                Mode.HTML.name -> binding.textView.setHtml(content)
                else -> {
                    if (content.length >= 32 * 1024) {
                        val truncatedContent =
                            content.take(32 * 1024) + "\n\n数据太大，无法全部显示…"
                        binding.textView.text = truncatedContent
                    } else {
                        binding.textView.text = content
                    }
                }
            }
            time = it.getLong("time", 0L)
        }
        
        binding.toolBar.setOnMenuItemClickListener { menu ->
            when (menu.itemId) {
                R.id.menu_close -> dismissAllowingStateLoss()
                R.id.menu_fullscreen_edit -> {
                    currentContent?.let { content ->
                        val cacheKey = "code_text_${System.currentTimeMillis()}"
                        CacheManager.putMemory(cacheKey, content)
                        startActivity<CodeEditActivity> {
                            putExtra("cacheKey", cacheKey)
                            putExtra("title", binding.toolBar.title)
                            putExtra("languageName", "text.html.markdown")
                        }
                    }
                }
            }
            true
        }
        // 设置倒计时显示
        if (time > 0) {
            // 显示倒计时徽章
            binding.badgeView.setBadgeCount((time / 1000).toInt())
            lifecycleScope.launch {
                while (time > 0) {
                    delay(1000)
                    time -= 1000
                    binding.badgeView.setBadgeCount((time / 1000).toInt())
                    if (time <= 0) {
                        view.post {
                            dialog?.setCancelable(true)
                            if (autoClose) dialog?.cancel()
                        }
                    }
                }
            }
        } else {
            // 无倒计时，允许关闭弹窗
            view.post {
                dialog?.setCancelable(true)
            }
        }
        
        // 初始化帮助文档选择器
        setupHelpSelector()
    }
    
    /**
     * 初始化帮助文档选择器
     * 仅在帮助模式下显示下拉列表供用户切换不同的帮助文档
     */
    private fun setupHelpSelector() {
        if (!isHelpMode) {
            binding.helpSelectorLayout.visibility = View.GONE
            return
        }
        
        binding.helpSelectorLayout.visibility = View.VISIBLE
        
        // 创建帮助文档列表适配器
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            HelpDocManager.allHelpDocs.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.helpSpinner.adapter = adapter
        
        // 设置当前选中的文档
        currentHelpDoc?.let { docName ->
            val index = HelpDocManager.getDocIndex(docName)
            if (index >= 0) {
                binding.helpSpinner.setSelection(index, false)
            }
        }
        
        // 设置下拉选择监听器
        binding.helpSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDoc = HelpDocManager.allHelpDocs[position]
                if (selectedDoc.fileName != currentHelpDoc) {
                    loadHelpDoc(selectedDoc.fileName)
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    
    /**
     * 异步加载帮助文档内容
     */
    private fun loadHelpDoc(fileName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 在IO线程读取文档
            val content = withContext(IO) {
                HelpDocManager.loadDoc(requireContext().assets, fileName)
            }
            currentHelpDoc = fileName
            // 更新显示内容
            updateContent(content)
        }
    }
    
    /**
     * 更新弹窗内容
     * 用于切换帮助文档时刷新显示
     */
    private fun updateContent(content: String) {
        // 同步更新当前内容变量，确保打开编辑器时获取到的是最新内容
        currentContent = content
        markwon?.let { mw ->
            viewLifecycleOwner.lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.textView.setTextClassifier(TextClassifier.NO_OP)
                }
                // 在IO线程转换Markdown
                val markdown = withContext(IO) {
                    mw.toMarkdown(content)
                }
                // 渲染Markdown到TextView
                binding.textView.setMarkdown(
                    mw,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source))
                    }
                )
            }
        }
    }

}
