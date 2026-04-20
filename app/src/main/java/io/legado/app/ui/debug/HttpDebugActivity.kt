package io.legado.app.ui.debug

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityHttpDebugBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class HttpDebugActivity : BaseActivity<ActivityHttpDebugBinding>() {

    override val binding by lazy { ActivityHttpDebugBinding.inflate(layoutInflater) }

    private val methods = listOf("GET", "POST")
    private val client = OkHttpClient.Builder().build()
    private var lastResponse: StrResponse? = null
    private var lastRequestSrc: String? = null

    private val uaNames by lazy {
        listOf(
            getString(R.string.debug_ua_default),
            getString(R.string.debug_ua_chrome_pc),
            getString(R.string.debug_ua_chrome_mobile),
            getString(R.string.debug_ua_safari_ios),
            getString(R.string.debug_ua_firefox),
            getString(R.string.debug_ua_custom)
        )
    }

    private val uaValues by lazy {
        listOf(
            "",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${BuildConfig.Cronet_Main_Version} Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            ""
        )
    }

    private var customUa: String = ""

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initSpinner()
        initUaSpinner()
        initClick()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.http_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_response_src).isEnabled = lastResponse != null
        menu.findItem(R.id.menu_request_src).isEnabled = lastRequestSrc != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_response_src -> showResponseSrc()
            R.id.menu_request_src -> showRequestSrc()
            R.id.menu_clear -> clearAll()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 显示响应体源码对话框
     * 使用 TextDialog 组件展示完整的响应信息（响应行、响应头、响应体）
     */
    private fun showResponseSrc() {
        val response = lastResponse ?: return
        val sb = StringBuilder()
        sb.append("=== 响应行 ===\n")
        sb.append("HTTP/1.1 ${response.code()} ${response.message()}\n\n")
        sb.append("=== 响应头 ===\n")
        response.raw.headers.forEach { (name, value) ->
            sb.append("$name: $value\n")
        }
        sb.append("\n=== 响应体 ===\n")
        sb.append(response.body)
        showDialogFragment(TextDialog(getString(R.string.debug_response_src), sb.toString()))
    }

    /**
     * 显示请求体源码对话框
     * 使用 TextDialog 组件展示完整的请求信息（请求行、请求头、请求体）
     */
    private fun showRequestSrc() {
        val requestSrc = lastRequestSrc ?: return
        showDialogFragment(TextDialog(getString(R.string.debug_request_src), requestSrc))
    }

    private fun clearAll() {
        binding.etUrl.setText("")
        binding.etHeaders.setText("")
        binding.etBody.setText("")
        binding.tvResponse.text = ""
        binding.tvHeaders.text = ""
        lastResponse = null
        lastRequestSrc = null
        invalidateOptionsMenu()
    }

    private fun initSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, methods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMethod.adapter = adapter
        binding.spinnerMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tilBody.visibility = if (position == 1) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initUaSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, uaNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUa.adapter = adapter
        binding.spinnerUa.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == uaNames.size - 1) {
                    showCustomUaDialog()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showCustomUaDialog() {
        val dialogBinding = io.legado.app.databinding.DialogEditTextBinding.inflate(layoutInflater)
        dialogBinding.editView.hint = getString(R.string.debug_user_agent)
        dialogBinding.editView.setText(customUa.ifEmpty { AppConfig.userAgent })
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.debug_ua_custom)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                customUa = dialogBinding.editView.text?.toString()?.trim() ?: ""
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                binding.spinnerUa.setSelection(0)
            }
            .setOnCancelListener {
                binding.spinnerUa.setSelection(0)
            }
            .show()
    }

    private fun getSelectedUa(): String {
        val position = binding.spinnerUa.selectedItemPosition
        return when {
            position == 0 -> AppConfig.userAgent
            position == uaValues.size - 1 -> customUa.ifEmpty { AppConfig.userAgent }
            else -> uaValues[position]
        }
    }

    private fun initClick() {
        binding.btnSend.setOnClickListener {
            sendRequest()
        }
        binding.btnCopyResponse.setOnClickListener {
            val result = binding.tvResponse.text.toString()
            if (result.isNotEmpty()) {
                sendToClip(result)
            }
        }
        binding.btnCopyHeaders.setOnClickListener {
            val headers = binding.tvHeaders.text.toString()
            if (headers.isNotEmpty()) {
                sendToClip(headers)
            }
        }
        binding.btnClear.setOnClickListener {
            clearAll()
        }
    }

    private fun sendRequest() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isEmpty()) {
            toastOnUi(R.string.debug_url_empty)
            return
        }

        binding.btnSend.isEnabled = false
        binding.tvResponse.text = getString(R.string.debug_loading)
        binding.tvHeaders.text = ""

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    doRequest(url)
                }
                showResponse(response)
            } catch (e: Exception) {
                binding.tvResponse.text = "错误: ${e.message}"
            } finally {
                binding.btnSend.isEnabled = true
            }
        }
    }

    private suspend fun doRequest(url: String): StrResponse {
        val methodIndex = binding.spinnerMethod.selectedItemPosition
        val headersText = binding.etHeaders.text.toString()
        val bodyText = binding.etBody.text.toString()
        val userAgent = getSelectedUa()

        return client.newCallStrResponse {
            url(url)
            when (methodIndex) {
                0 -> get()
                1 -> {
                    if (bodyText.isNotEmpty()) {
                        val requestBody = bodyText.toRequestBody("application/json; charset=UTF-8".toMediaType())
                        post(requestBody)
                    }
                }
            }
            addHeader("User-Agent", userAgent)
            if (headersText.isNotEmpty()) {
                parseHeaders(headersText).forEach { (key, value) ->
                    if (key.equals("User-Agent", ignoreCase = true)) {
                        return@forEach
                    }
                    addHeader(key, value)
                }
            }
        }.also { response ->
            lastResponse = response
            lastRequestSrc = buildRequestSrc(url, methodIndex, headersText, bodyText, userAgent)
            invalidateOptionsMenu()
        }
    }

    private fun buildRequestSrc(url: String, methodIndex: Int, headersText: String, bodyText: String, userAgent: String): String {
        val sb = StringBuilder()
        sb.append("=== 请求行 ===\n")
        sb.append("${if (methodIndex == 0) "GET" else "POST"} $url\n\n")
        sb.append("=== 请求头 ===\n")
        sb.append("User-Agent: $userAgent\n")
        if (headersText.isNotEmpty()) {
            headersText.lines().forEach { line ->
                if (line.split(":").firstOrNull()?.trim()?.equals("User-Agent", ignoreCase = true) != true) {
                    sb.append("$line\n")
                }
            }
        }
        if (methodIndex == 1 && bodyText.isNotEmpty()) {
            sb.append("Content-Type: application/json; charset=UTF-8\n")
            sb.append("\n=== 请求体 ===\n")
            sb.append(bodyText)
        }
        return sb.toString()
    }

    private fun parseHeaders(headersText: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headersText.lines().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                headers[parts[0].trim()] = parts[1].trim()
            }
        }
        return headers
    }

    private fun showResponse(response: StrResponse) {
        val sb = StringBuilder()
        sb.append("状态码: ${response.code()}\n")
        sb.append("消息: ${response.message()}\n")
        sb.append("耗时: ${response.raw.receivedResponseAtMillis - response.raw.sentRequestAtMillis}ms\n")
        binding.tvHeaders.text = sb.toString()

        val body = response.body
        binding.tvResponse.text = body
    }
}
