package io.legado.app.ui.about

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.Item1lineTextBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.delete
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.isActive
import java.io.FileFilter

/**
 * 应用崩溃日志对话框
 * 用于查看和清除应用崩溃时记录的日志文件
 * 崩溃日志来源包括：
 * 1. 应用缓存目录下的 crash 文件夹
 * 2. 用户配置的备份路径下的 crash 文件夹
 */
class CrashLogsDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)

    private val viewModel by viewModels<CrashViewModel>()

    private val adapter by lazy { LogAdapter() }

    private var searchQuery: String = ""

    private var allLogs: List<FileDoc> = emptyList()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.crash_log)
        binding.toolBar.inflateMenu(R.menu.crash_log)
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        viewModel.logLiveData.observe(viewLifecycleOwner) {
            allLogs = it
            filterLogs()
        }
        viewModel.initData()
        setupSearchView()
    }

    /**
     * 设置搜索视图
     * 监听搜索文本变化，实时过滤日志列表并高亮匹配文字
     */
    private fun setupSearchView() {
        val searchItem = binding.toolBar.menu.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText ?: ""
                    filterLogs()
                    return true
                }
            })
        }
    }

    /**
     * 根据搜索关键词过滤日志
     * 同时触发列表刷新以更新高亮显示
     */
    private fun filterLogs() {
        if (searchQuery.isEmpty()) {
            adapter.setItems(allLogs)
        } else {
            val filtered = allLogs.filter { logEntry ->
                logEntry.name.contains(searchQuery, ignoreCase = true)
            }
            adapter.setItems(filtered)
        }
    }

    /**
     * 处理菜单项点击事件
     */
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_search -> {
                // SearchView 会自动处理展开/收起
            }
            R.id.menu_clear -> viewModel.clearCrashLog()
        }
        return true
    }

    /**
     * 显示日志文件内容
     * 将文件内容读取后通过 TextDialog 显示
     */
    private fun showLogFile(fileDoc: FileDoc) {
        viewModel.readFile(fileDoc) {
            if (lifecycleScope.isActive) {
                showDialogFragment(TextDialog(fileDoc.name, it))
            }
        }
    }

    /**
     * 崩溃日志列表适配器
     * 用于展示 FileDoc 类型的文件对象
     * 每个条目显示文件名，点击后查看文件内容
     */
    inner class LogAdapter : RecyclerAdapter<FileDoc, Item1lineTextBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): Item1lineTextBinding {
            return Item1lineTextBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: Item1lineTextBinding) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    showLogFile(item)
                }
            }
        }

        /**
         * 绑定文件名到视图
         * 当存在搜索关键词时，高亮匹配的文字
         */
        override fun convert(
            holder: ItemViewHolder,
            binding: Item1lineTextBinding,
            item: FileDoc,
            payloads: MutableList<Any>
        ) {
            val fileName = item.name
            if (searchQuery.isNotEmpty() && fileName.contains(searchQuery, ignoreCase = true)) {
                binding.textView.text = highlightText(fileName, searchQuery)
            } else {
                binding.textView.text = fileName
            }
        }
    }

    /**
     * 高亮显示匹配的文字
     * 使用 BackgroundColorSpan 将匹配部分标记为黄色背景
     */
    private fun highlightText(text: String, query: String): android.text.Spannable {
        val spannable = android.text.SpannableStringBuilder(text)
        var startIndex = 0
        while (true) {
            val index = text.indexOf(query, startIndex, ignoreCase = true)
            if (index < 0) break
            spannable.setSpan(
                android.text.style.BackgroundColorSpan(Color.YELLOW),
                index,
                index + query.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + query.length
        }
        return spannable
    }

    /**
     * 崩溃日志对话框的 ViewModel
     * 负责加载、读取和清除崩溃日志文件
     */
    class CrashViewModel(application: Application) : BaseViewModel(application) {

        val logLiveData = MutableLiveData<List<FileDoc>>()

        /**
         * 初始化数据
         * 从以下来源加载崩溃日志文件：
         * 1. 应用外部缓存目录的 crash 子目录
         * 2. 用户配置的备份路径下的 crash 目录
         * 加载后按文件名降序排列并去重
         */
        fun initData() {
            execute {
                val list = arrayListOf<FileDoc>()
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.listFiles(FileFilter { it.isFile })
                    ?.forEach {
                        list.add(FileDoc.fromFile(it))
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.list {
                            !it.isDir
                        }?.let {
                            list.addAll(it)
                        }
                }
                return@execute list.sortedByDescending { it.name }.distinctBy { it.name }
            }.onSuccess {
                logLiveData.postValue(it)
            }
        }

        /**
         * 读取指定日志文件的内容
         * @param fileDoc 日志文件对象
         * @param success 读取成功后的回调，参数为文件内容字符串
         */
        fun readFile(fileDoc: FileDoc, success: (String) -> Unit) {
            execute {
                String(fileDoc.readBytes())
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
        }

        /**
         * 清除所有崩溃日志
         * 同时清理以下位置的文件：
         * 1. 应用外部缓存目录的 crash 目录
         * 2. 用户备份路径下的 crash 目录
         * 清除完成后重新加载数据
         */
        fun clearCrashLog() {
            execute {
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.let {
                        FileUtils.delete(it, false)
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.delete()
                }
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }.onFinally {
                initData()
            }
        }
    }
}