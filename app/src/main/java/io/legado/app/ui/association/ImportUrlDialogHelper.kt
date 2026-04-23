package io.legado.app.ui.association

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogImportUrlBinding
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 网络导入URL对话框辅助类
 * 提供信号标志（连接状态检测）和地球按钮（内置浏览器打开）功能
 */
object ImportUrlDialogHelper {

    /**
     * 创建网络导入对话框的Binding
     * @param layoutInflater 布局填充器
     * @param context 上下文
     * @param lifecycleOwner 生命周期所有者，用于协程管理
     * @param cacheUrls 历史URL缓存列表
     * @param onUrlsChanged URL列表变更回调
     * @param openBrowser 用浏览器打开URL的回调
     */
    fun createBinding(
        layoutInflater: LayoutInflater,
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cacheUrls: MutableList<String>,
        onUrlsChanged: (List<String>) -> Unit,
        openBrowser: (String) -> Unit
    ): DialogImportUrlBinding {
        var checkJob: Job? = null
        return DialogImportUrlBinding.inflate(layoutInflater).apply {
            editView.hint = "url"
            editView.setFilterValues(cacheUrls)
            editView.delCallBack = { value ->
                cacheUrls.remove(value)
                onUrlsChanged(cacheUrls)
            }
            ivSignal.setImageResource(R.drawable.ic_signal)
            ibOpenBrowser.setOnClickListener {
                val text = editView.text?.toString()?.trim()
                if (text.isNullOrEmpty()) {
                    context.toastOnUi(R.string.please_input_url)
                    return@setOnClickListener
                }
                if (!text.isAbsUrl()) {
                    context.toastOnUi(R.string.url_format_error)
                    return@setOnClickListener
                }
                openBrowser(text)
            }
            editView.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString()?.trim()
                    if (text.isNullOrEmpty() || !text.isAbsUrl()) {
                        ivSignal.visibility = View.GONE
                        return
                    }
                    ivSignal.visibility = View.VISIBLE
                    ivSignal.setImageResource(R.drawable.ic_signal)
                    checkJob?.cancel()
                    checkJob = lifecycleOwner.lifecycleScope.launch {
                        delay(500)
                        val isConnected = checkUrlConnection(text)
                        ivSignal.setImageResource(
                            if (isConnected) {
                                R.drawable.ic_signal_green
                            } else {
                                R.drawable.ic_signal_red
                            }
                        )
                    }
                }
            })
        }
    }

    /**
     * 检测URL连接状态
     * 使用HEAD请求检测，超时或异常返回false
     * @param url 待检测的URL
     * @return 是否连接成功
     */
    private suspend fun checkUrlConnection(url: String): Boolean {
        return withContext(IO) {
            try {
                val request = Request.Builder().url(url).head().build()
                okHttpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                AppLog.put("URL连接检测失败: $url", e)
                false
            }
        }
    }
}
