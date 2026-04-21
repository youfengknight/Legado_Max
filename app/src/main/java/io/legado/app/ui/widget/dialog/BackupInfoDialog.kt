package io.legado.app.ui.widget.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemBackupCategoryBinding
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 备份信息查看对话框
 * 显示当前会备份的数据统计
 */
class BackupInfoDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { BackupInfoAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.85f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        toolBar.title = getString(R.string.view_backup_info)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadBackupInfo()
    }

    private fun loadBackupInfo() {
        val overview = BackupInfoHelper.getBackupOverview()

        if (overview.items.isEmpty()) {
            binding.tvMsg.visibility = View.VISIBLE
            binding.tvMsg.text = getString(R.string.no_backup_data)
            return
        }

        val items = mutableListOf<BackupInfoItem>()

        // 添加头部信息
        items.add(BackupInfoItem.Header(
            itemCount = overview.items.size,
            totalSize = BackupInfoHelper.formatSize(overview.totalSize)
        ))

        // 按分类添加
        val categories = BackupInfoHelper.categorizeItems(overview.items)
        categories.forEach { cat ->
            items.add(BackupInfoItem.Category(
                name = cat.name,
                icon = cat.icon,
                count = cat.items.size,
                totalSize = BackupInfoHelper.formatSize(cat.totalSize)
            ))
            cat.items.forEach { item ->
                items.add(BackupInfoItem.File(
                    fileName = item.fileName,
                    displayName = item.displayName,
                    size = BackupInfoHelper.formatSize(item.size)
                ))
            }
        }

        adapter.setItems(items)
    }

    companion object {
        fun newInstance(): BackupInfoDialog {
            return BackupInfoDialog()
        }
    }

    sealed class BackupInfoItem {
        data class Header(
            val itemCount: Int,
            val totalSize: String
        ) : BackupInfoItem()

        data class Category(
            val name: String,
            val icon: String,
            val count: Int,
            val totalSize: String
        ) : BackupInfoItem()

        data class File(
            val fileName: String,
            val displayName: String,
            val size: String
        ) : BackupInfoItem()
    }

    class BackupInfoAdapter(context: Context) :
        RecyclerAdapter<BackupInfoItem, ItemBackupCategoryBinding>(context) {

        override fun getItemViewType(item: BackupInfoItem, position: Int): Int {
            return when (item) {
                is BackupInfoItem.Header -> 0
                is BackupInfoItem.Category -> 1
                is BackupInfoItem.File -> 2
            }
        }

        override fun getViewBinding(parent: ViewGroup): ItemBackupCategoryBinding {
            return ItemBackupCategoryBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBackupCategoryBinding,
            item: BackupInfoItem,
            payloads: MutableList<Any>
        ) {
            when (item) {
                is BackupInfoItem.Header -> bindHeader(binding, item)
                is BackupInfoItem.Category -> bindCategory(binding, item)
                is BackupInfoItem.File -> bindFile(binding, item)
            }
        }

        private fun bindHeader(binding: ItemBackupCategoryBinding, item: BackupInfoItem.Header) {
            binding.root.visibility = View.VISIBLE
            binding.apply {
                tvIcon.text = "📦"
                tvTitle.text = "备份数据统计"
                tvSubtitle.text = "预估大小: ${item.totalSize}"
                tvCount.text = "${item.itemCount} 项"
                tvSize.visibility = View.GONE
            }
        }

        private fun bindCategory(binding: ItemBackupCategoryBinding, item: BackupInfoItem.Category) {
            binding.root.visibility = View.VISIBLE
            binding.apply {
                tvIcon.text = item.icon
                tvTitle.text = item.name
                tvSubtitle.visibility = View.GONE
                tvCount.text = "${item.count} 项"
                tvSize.text = item.totalSize
                tvSize.visibility = View.VISIBLE
            }
        }

        private fun bindFile(binding: ItemBackupCategoryBinding, item: BackupInfoItem.File) {
            binding.root.visibility = View.VISIBLE
            binding.apply {
                tvIcon.text = "  📄"
                tvTitle.text = item.displayName
                tvSubtitle.text = item.fileName
                tvSubtitle.visibility = View.VISIBLE
                tvCount.visibility = View.GONE
                tvSize.text = item.size
                tvSize.visibility = View.VISIBLE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBackupCategoryBinding) {
        }
    }
}
