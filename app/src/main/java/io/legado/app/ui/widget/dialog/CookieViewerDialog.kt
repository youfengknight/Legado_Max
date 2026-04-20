package io.legado.app.ui.widget.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemCookieBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class CookieViewerDialog(private val url: String) : BaseDialogFragment(R.layout.dialog_recycler_view),
    androidx.appcompat.widget.Toolbar.OnMenuItemClickListener {

    private val viewModel by viewModels<CookieViewerViewModel>()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { CookieAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.view_cookie)
            toolBar.inflateMenu(R.menu.cookie_viewer)
            toolBar.setOnMenuItemClickListener(this@CookieViewerDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        viewModel.loadCookies(url).observe(viewLifecycleOwner) { cookies ->
            adapter.setItems(cookies)
        }
    }

    override fun onMenuItemClick(item: android.view.MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_copy_all -> {
                val allCookies = adapter.getItems().joinToString("\n") { it }
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Cookies", allCookies))
                requireContext().toastOnUi(R.string.copy_complete)
            }
            R.id.menu_refresh -> {
                viewModel.loadCookies(url)
            }
        }
        return true
    }

    inner class CookieAdapter(context: Context) :
        io.legado.app.base.adapter.RecyclerAdapter<String, ItemCookieBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemCookieBinding {
            return ItemCookieBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: io.legado.app.base.adapter.ItemViewHolder,
            binding: ItemCookieBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item
        }

        override fun registerListener(holder: io.legado.app.base.adapter.ItemViewHolder, binding: ItemCookieBinding) {
        }
    }
}
