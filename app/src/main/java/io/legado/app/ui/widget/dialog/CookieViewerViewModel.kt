package io.legado.app.ui.widget.dialog

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.help.http.CookieStore
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank

/**
 * Cookie查看对话框的ViewModel
 * 负责加载和管理Cookie数据
 */
class CookieViewerViewModel(application: Application) : BaseViewModel(application) {

    private val _cookiesLiveData = MutableLiveData<List<String>>()
    
    /**
     * 加载指定URL的Cookie
     * 会同时获取HTTP层的CookieStore和WebView层的Cookie
     *
     * @param url 目标URL
     * @return Cookie列表的LiveData
     */
    fun loadCookies(url: String): LiveData<List<String>> {
        execute {
            val cookies = mutableListOf<String>()
            
            // 获取域名
            val domain = NetworkUtils.getSubDomain(url)
            
            // 从CookieStore获取HTTP层的Cookie（持久化存储）
            val httpCookie = CookieStore.getCookie(url)
            if (httpCookie.isNotBlank()) {
                httpCookie.splitNotBlank(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        cookies.add(trimmed)
                    }
                }
            }
            
            // 从WebView CookieManager获取浏览器层的Cookie
            val webCookieManager = CookieManager.getInstance()
            val webViewCookie = webCookieManager.getCookie(url)
            if (!webViewCookie.isNullOrBlank()) {
                if (cookies.isNotEmpty()) {
                    cookies.add("")  // 添加空行分隔
                }
                webViewCookie.splitNotBlank(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        cookies.add(trimmed)
                    }
                }
            }
            
            // 如果没有任何Cookie，显示提示信息
            if (cookies.isEmpty()) {
                cookies.add("当前网页没有 Cookie")
            }
            
            _cookiesLiveData.postValue(cookies)
        }
        return _cookiesLiveData
    }
}
