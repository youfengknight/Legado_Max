package io.legado.app.ui.rss.article

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemRssReadRecordBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.utils.SearchHighlightUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * RSS 阅读记录对话框
 * 用于显示和管理 RSS 文章的阅读历史记录
 */
class ReadRecordDialog(private val origin: String? = null) : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val viewModel by viewModels<RssSortViewModel>()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy {
        ReadRecordAdapter(requireContext())
    }

    private var searchQuery: String = ""
    private var allRecords: List<RssReadRecord> = emptyList()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.read_record)
            toolBar.inflateMenu(R.menu.rss_read_record)
            toolBar.setOnMenuItemClickListener(this@ReadRecordDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        allRecords = viewModel.getRecords(origin)
        adapter.setItems(allRecords)
        adapter.setOnRecordClickListener(object : OnRecordClickListener {
            override fun onRecordClick(record: RssReadRecord?) {
                record?.let { ReadRss.readRss(activity as AppCompatActivity, it)}
            }
        })
        setupSearchView()
    }

    /**
     * 设置搜索视图
     * 监听搜索文本变化，实时过滤记录列表并高亮匹配文字
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
                    filterRecords()
                    return true
                }
            })
        }
    }

    /**
     * 根据搜索关键词过滤记录
     * 同时触发列表刷新以更新高亮显示
     */
    private fun filterRecords() {
        val filtered = SearchHighlightUtils.filterList(allRecords, searchQuery) { record, query ->
            record.title.contains(query, ignoreCase = true) ||
            record.record.contains(query, ignoreCase = true)
        }
        adapter.setItems(filtered)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_search -> {
                // SearchView 会自动处理展开/收起
            }
            R.id.menu_clear -> {
                alert(R.string.draw) {
                    val countRead = viewModel.countRecords(origin)
                    setMessage(getString(R.string.sure_del) + "\n" + countRead + " " + getString(R.string.read_record))
                    noButton()
                    yesButton {
                        viewModel.deleteAllRecord(origin)
                        adapter.clearItems()
                    }
                }
            }
        }
        return true
    }

    inner class ReadRecordAdapter(context: Context) :
        RecyclerAdapter<RssReadRecord, ItemRssReadRecordBinding>(context) {

        private var recordClickListener: OnRecordClickListener? = null
        fun setOnRecordClickListener(listener: OnRecordClickListener) {
            this.recordClickListener = listener
        }

        override fun getViewBinding(parent: ViewGroup): ItemRssReadRecordBinding {
            return ItemRssReadRecordBinding.inflate(inflater, parent, false)
        }

        /**
         * 绑定记录数据到视图
         * 显示标题和阅读进度
         * 当存在搜索关键词时，高亮匹配的文字
         */
        override fun convert(
            holder: ItemViewHolder,
            binding: ItemRssReadRecordBinding,
            item: RssReadRecord,
            payloads: MutableList<Any>
        ) {
            binding.textTitle.text = SearchHighlightUtils.getHighlightedText(item.title, searchQuery)
            binding.textRecord.text = SearchHighlightUtils.getHighlightedText(item.record, searchQuery)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemRssReadRecordBinding) {
            binding.textTitle.setOnClickListener {
                recordClickListener?.onRecordClick(getItem(holder.bindingAdapterPosition))
                dismiss()
            }
        }
    }

    interface OnRecordClickListener {
        fun onRecordClick(record: RssReadRecord?)
    }

}