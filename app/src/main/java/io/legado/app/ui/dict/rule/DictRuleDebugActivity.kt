package io.legado.app.ui.dict.rule

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.ActivityDictRuleDebugBinding
import io.legado.app.help.config.DictDebugConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.source.debug.BookSourceDebugAdapter
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import splitties.views.onClick

class DictRuleDebugActivity : VMBaseActivity<ActivityDictRuleDebugBinding, DictRuleDebugModel>() {

    override val binding by viewBinding(ActivityDictRuleDebugBinding::inflate)
    override val viewModel by viewModels<DictRuleDebugModel>()

    private val adapter by lazy { BookSourceDebugAdapter(this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        val dictRule = DictRule().apply {
            name = intent.getStringExtra("name") ?: ""
            urlRule = intent.getStringExtra("urlRule") ?: ""
            showRule = intent.getStringExtra("showRule") ?: ""
        }
        viewModel.init(dictRule) {
            initHelpView()
        }
        viewModel.observe { state, msg ->
            lifecycleScope.launch {
                adapter.addItem(msg)
                if (state == -1 || state == 1) {
                    binding.rotateLoading.gone()
                }
            }
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        binding.rotateLoading.loadingColor = accentColor
    }

    private fun initSearchView() {
        searchView.onActionViewExpanded()
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = "输入关键词"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                openOrCloseHelp(false)
                startSearch(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            openOrCloseHelp(hasFocus)
        }
        openOrCloseHelp(true)
    }

    private fun initHelpView() {
        binding.textTestDefault.onClick {
            searchView.setQuery("测试", true)
        }
        binding.textTestExample.onClick {
            searchView.setQuery("字典", true)
        }
        binding.textTestWord.onClick {
            searchView.setQuery("词语", true)
        }
        binding.textClearLog.onClick {
            adapter.clearItems()
            toastOnUi("日志已清空")
        }
        binding.textClearHistory.onClick {
            DictDebugConfig.clearSearchHistory()
            toastOnUi("搜索历史已清空")
        }
    }

    private fun openOrCloseHelp(open: Boolean) {
        binding.help.visibility = if (open) View.VISIBLE else View.GONE
    }

    private fun startSearch(word: String) {
        if (word.isBlank()) {
            toastOnUi("关键词不能为空")
            return
        }

        DictDebugConfig.addSearchHistory(word)
        adapter.clearItems()
        viewModel.startDebug(word) {
            binding.rotateLoading.visible()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dict_rule_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_url_src -> {
                if (viewModel.urlSrc.isNotEmpty()) {
                    showDialogFragment(TextDialog("URL源码", viewModel.urlSrc))
                } else {
                    toastOnUi("请先执行搜索")
                }
            }
            R.id.menu_result_src -> {
                if (viewModel.resultSrc.isNotEmpty()) {
                    showDialogFragment(TextDialog("结果源码", viewModel.resultSrc))
                } else {
                    toastOnUi("请先执行搜索")
                }
            }
            R.id.menu_help -> showHelp("dictRuleHelp")
        }
        return super.onCompatOptionsItemSelected(item)
    }

}