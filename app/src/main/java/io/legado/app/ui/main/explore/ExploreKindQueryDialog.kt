package io.legado.app.ui.main.explore

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.DialogExploreKindQueryBinding
import io.legado.app.databinding.ItemExploreKindBinding
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.visible
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO

/**
 * 发现分类查询对话框
 * 用于显示书源的所有发现分类，支持搜索过滤
 */
class ExploreKindQueryDialog : BaseDialogFragment(R.layout.dialog_explore_kind_query) {

    private val binding by viewBinding(DialogExploreKindQueryBinding::bind)
    private val adapter by lazy { KindAdapter(requireContext()) }
    private var sourceUrl: String = ""
    private var sourceName: String = ""
    private var kinds: List<ExploreKind> = emptyList()
    private var filteredKinds: List<ExploreKind> = emptyList()
    private var callBack: CallBack? = null

    constructor(sourceUrl: String, sourceName: String) : this() {
        arguments = Bundle().apply {
            putString("sourceUrl", sourceUrl)
            putString("sourceName", sourceName)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CallBack) {
            callBack = context
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.8f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        sourceUrl = arguments?.getString("sourceUrl") ?: ""
        sourceName = arguments?.getString("sourceName") ?: ""
        initToolBar()
        initRecyclerView()
        loadData()
    }

    /**
     * 初始化工具栏
     * 设置标题和搜索框
     */
    private fun initToolBar() {
        binding.toolBar.title = sourceName
        binding.searchView.applyTint(primaryTextColor)
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterKinds(newText)
                return true
            }
        })
    }

    /**
     * 初始化 RecyclerView
     * 设置布局管理器和适配器
     */
    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    /**
     * 加载发现分类数据
     * 异步获取书源的发现分类列表
     */
    private fun loadData() {
        binding.rotateLoading.visible()
        Coroutine.async(lifecycleScope, IO) {
            val bookSource = appDb.bookSourceDao.getBookSource(sourceUrl)
            bookSource?.exploreKinds() ?: emptyList()
        }.onSuccess { kindList ->
            kinds = kindList
            filteredKinds = kindList
            adapter.setItems(filteredKinds)
            updateEmptyView()
        }.onFinally {
            binding.rotateLoading.gone()
        }
    }

    /**
     * 过滤分类列表
     * 根据搜索关键词过滤分类
     * @param query 搜索关键词
     */
    private fun filterKinds(query: String?) {
        filteredKinds = if (query.isNullOrBlank()) {
            kinds
        } else {
            kinds.filter { kind ->
                kind.title.contains(query, ignoreCase = true)
            }
        }
        adapter.setItems(filteredKinds)
        updateEmptyView()
    }

    /**
     * 更新空视图状态
     * 当没有分类时显示提示信息
     */
    private fun updateEmptyView() {
        if (filteredKinds.isEmpty()) {
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
        }
    }

    /**
     * 分类列表适配器
     * 用于显示发现分类项
     */
    private inner class KindAdapter(context: Context) :
        RecyclerAdapter<ExploreKind, ItemExploreKindBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemExploreKindBinding {
            return ItemExploreKindBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemExploreKindBinding,
            item: ExploreKind,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.title
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemExploreKindBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let { kind ->
                    val url = kind.url?.takeIf { it.isNotBlank() } ?: return@let
                    callBack?.openExplore(sourceUrl, kind.title, url)
                    dismiss()
                }
            }
        }
    }

    /**
     * 回调接口
     * 用于打开发现页面
     */
    interface CallBack {
        fun openExplore(sourceUrl: String, title: String, exploreUrl: String?)
    }

}
