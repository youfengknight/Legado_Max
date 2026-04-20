package io.legado.app.ui.debug

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityDebugToolsBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setEdgeEffectColor

class DebugToolsActivity : BaseActivity<ActivityDebugToolsBinding>() {

    override val binding by lazy { ActivityDebugToolsBinding.inflate(layoutInflater) }

    private val tools = listOf(
        DebugTool(R.string.debug_encode_tools, R.string.debug_encode_tools_desc, EncodeToolsActivity::class.java),
        DebugTool(R.string.debug_http_request, R.string.debug_http_request_desc, HttpDebugActivity::class.java),
        DebugTool(R.string.debug_regex_test, R.string.debug_regex_test_desc, RegexTestActivity::class.java),
        DebugTool(R.string.debug_timestamp, R.string.debug_timestamp_desc, TimestampConvertActivity::class.java)
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = ToolsAdapter()
    }

    private data class DebugTool(
        val titleRes: Int,
        val descRes: Int,
        val activityClass: Class<*>
    )

    private inner class ToolsAdapter : RecyclerView.Adapter<ToolsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_debug_tool, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tool = tools[position]
            holder.tvTitle.setText(tool.titleRes)
            holder.tvDesc.setText(tool.descRes)
            holder.itemView.setOnClickListener {
                val intent = Intent(this@DebugToolsActivity, tool.activityClass)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = tools.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_title)
            val tvDesc: TextView = view.findViewById(R.id.tv_desc)
        }
    }
}
