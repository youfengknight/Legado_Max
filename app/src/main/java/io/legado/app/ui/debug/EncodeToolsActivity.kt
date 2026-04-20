package io.legado.app.ui.debug

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityEncodeToolsBinding
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.encodeURI
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

class EncodeToolsActivity : BaseActivity<ActivityEncodeToolsBinding>() {

    override val binding by lazy { ActivityEncodeToolsBinding.inflate(layoutInflater) }

    private val encodeTypes = listOf(
        "Base64 编码",
        "Base64 解码",
        "MD5 编码 (32位)",
        "MD5 编码 (16位)",
        "URL 编码",
        "URL 解码",
        "Hex 编码",
        "Hex 解码",
        "Unicode 编码",
        "Unicode 解码"
    )

    private var currentType = 0

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initSpinner()
        initClick()
    }

    private fun initSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, encodeTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerEncodeType.adapter = adapter
        binding.spinnerEncodeType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentType = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initClick() {
        binding.btnConvert.setOnClickListener {
            convert()
        }
        binding.btnCopy.setOnClickListener {
            val result = binding.tvResult.text.toString()
            if (result.isNotEmpty()) {
                sendToClip(result)
            }
        }
        binding.btnClear.setOnClickListener {
            binding.etInput.setText("")
            binding.tvResult.text = ""
        }
        binding.btnSwap.setOnClickListener {
            val input = binding.etInput.text.toString()
            val result = binding.tvResult.text.toString()
            if (result.isNotEmpty()) {
                binding.etInput.setText(result)
                binding.tvResult.text = input
            }
        }
    }

    private fun convert() {
        val input = binding.etInput.text.toString()
        if (input.isEmpty()) {
            toastOnUi(R.string.input_is_empty)
            return
        }
        try {
            val result = when (currentType) {
                0 -> EncoderUtils.base64Encode(input)
                1 -> EncoderUtils.base64Decode(input)
                2 -> MD5Utils.md5Encode(input)
                3 -> MD5Utils.md5Encode16(input)
                4 -> input.encodeURI()
                5 -> java.net.URLDecoder.decode(input, "UTF-8")
                6 -> bytesToHex(input.toByteArray())
                7 -> String(hexToBytes(input))
                8 -> stringToUnicode(input)
                9 -> unicodeToString(input)
                else -> input
            }
            binding.tvResult.text = result
        } catch (e: Exception) {
            binding.tvResult.text = "错误: ${e.message}"
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "")
        return ByteArray(cleanHex.length / 2) {
            cleanHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    private fun stringToUnicode(str: String): String {
        return str.map { char ->
            if (char.code > 127) {
                "\\u${char.code.toString(16).padStart(4, '0')}"
            } else {
                char.toString()
            }
        }.joinToString("")
    }

    private fun unicodeToString(unicode: String): String {
        val regex = Regex("\\\\u([0-9a-fA-F]{4})")
        return regex.replace(unicode) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }
}
