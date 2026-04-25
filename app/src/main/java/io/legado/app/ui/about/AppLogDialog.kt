package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.LogUtils
import io.legado.app.utils.SearchHighlightUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onClick
import java.util.*

/**
 * 应用运行时日志对话框
 * 用于显示应用运行过程中记录的日志信息
 * 日志数据来源于 AppLog 单例对象，包含时间戳、日志消息和可选的异常堆栈
 */
class AppLogDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)

    private val adapter by lazy {
        LogAdapter(requireContext())
    }

    private var searchQuery: String = ""

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.log)
            toolBar.inflateMenu(R.menu.app_log)
            toolBar.setOnMenuItemClickListener(this@AppLogDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        adapter.setItems(AppLog.logs)
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
        val filtered = SearchHighlightUtils.filterList(AppLog.logs, searchQuery) { logEntry, query ->
            logEntry.second.contains(query, ignoreCase = true)
        }
        adapter.setItems(filtered)
    }

    /**
     * 处理菜单项点击事件
     */
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_search -> {
                // SearchView 会自动处理展开/收起
            }
            R.id.menu_clear -> {
                AppLog.clear()
                adapter.clearItems()
            }
        }
        return true
    }

    /**
     * 日志列表适配器
     * 用于展示 Triple<Long, String, Throwable?> 格式的日志数据
     * - Long: 时间戳
     * - String: 日志消息
     * - Throwable?: 可选的异常对象
     */
    inner class LogAdapter(context: Context) :
        RecyclerAdapter<Triple<Long, String, Throwable?>, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        /**
         * 绑定日志数据到视图
         * 显示日志时间和消息内容
         * 当存在搜索关键词时，高亮匹配的文字
         */
        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: Triple<Long, String, Throwable?>,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = LogUtils.logTimeFormat.format(Date(item.first))
            binding.textMessage.text = SearchHighlightUtils.getHighlightedText(item.second, searchQuery)
        }

        /**
         * 注册点击事件
         * 点击日志条目时，如果存在异常信息则显示异常堆栈详情
         */
        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { item ->
                    item.third?.let {
                        showDialogFragment(TextDialog("Log", it.stackTraceToString()))
                    }
                }
            }
        }
    }
}