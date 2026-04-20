package io.legado.app.ui.debug

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityTimestampConvertBinding
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimestampConvertActivity : BaseActivity<ActivityTimestampConvertBinding>() {

    override val binding by lazy { ActivityTimestampConvertBinding.inflate(layoutInflater) }

    private val formats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd",
        "MM-dd HH:mm:ss",
        "HH:mm:ss",
        "yyyyMMddHHmmss",
        "yyyyMMdd"
    )

    private var currentFormatIndex = 0

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initSpinner()
        initClick()
        updateCurrentTime()
    }

    private fun initSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, formats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFormat.adapter = adapter
        binding.spinnerFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFormatIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initClick() {
        binding.btnTimestampToDate.setOnClickListener {
            timestampToDate()
        }
        binding.btnDateToTimestamp.setOnClickListener {
            dateToTimestamp()
        }
        binding.btnNow.setOnClickListener {
            updateCurrentTime()
        }
        binding.btnCopyTimestamp.setOnClickListener {
            val timestamp = binding.etTimestamp.text.toString()
            if (timestamp.isNotEmpty()) {
                sendToClip(timestamp)
            }
        }
        binding.btnCopyDate.setOnClickListener {
            val date = binding.tvDateResult.text.toString()
            if (date.isNotEmpty()) {
                sendToClip(date)
            }
        }
    }

    private fun updateCurrentTime() {
        val now = System.currentTimeMillis()
        binding.etTimestamp.setText(now.toString())
        binding.tvDateResult.text = formatTimestamp(now)
    }

    private fun timestampToDate() {
        val timestampStr = binding.etTimestamp.text.toString().trim()
        if (timestampStr.isEmpty()) {
            toastOnUi(R.string.input_is_empty)
            return
        }

        try {
            var timestamp = timestampStr.toLong()
            if (timestampStr.length == 10) {
                timestamp *= 1000
            }
            binding.tvDateResult.text = formatTimestamp(timestamp)
        } catch (e: Exception) {
            binding.tvDateResult.text = "错误: ${e.message}"
        }
    }

    private fun dateToTimestamp() {
        val dateStr = binding.etDate.text.toString().trim()
        if (dateStr.isEmpty()) {
            toastOnUi(R.string.input_is_empty)
            return
        }

        try {
            val format = SimpleDateFormat(formats[currentFormatIndex], Locale.getDefault())
            format.timeZone = TimeZone.getDefault()
            val date = format.parse(dateStr)
            date?.let {
                val timestamp = it.time
                binding.etTimestamp.setText(timestamp.toString())
                binding.tvDateResult.text = formatTimestamp(timestamp)
            }
        } catch (e: Exception) {
            binding.tvDateResult.text = "错误: ${e.message}"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}
