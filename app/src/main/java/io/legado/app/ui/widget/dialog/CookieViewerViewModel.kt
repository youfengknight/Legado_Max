package io.legado.app.ui.widget.dialog

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.help.http.CookieStore
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank

class CookieViewerViewModel(application: Application) : BaseViewModel(application) {

    private val _cookiesLiveData = MutableLiveData<List<String>>()
    
    fun loadCookies(url: String): LiveData<List<String>> {
        execute {
            val cookies = mutableListOf<String>()
            
            val domain = NetworkUtils.getSubDomain(url)
            val httpCookie = CookieStore.getCookie(url)
            if (httpCookie.isNotBlank()) {
                cookies.add("--- HTTP Cookie (来自CookieStore) ---")
                httpCookie.splitNotBlank(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        cookies.add(trimmed)
                    }
                }
            }
            
            val webCookieManager = CookieManager.getInstance()
            val webViewCookie = webCookieManager.getCookie(url)
            if (webViewCookie.isNotBlank()) {
                cookies.add("")
                cookies.add("--- WebView Cookie (来自WebView) ---")
                webViewCookie.splitNotBlank(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        cookies.add(trimmed)
                    }
                }
            }
            
            if (cookies.isEmpty()) {
                cookies.add("当前网页没有 Cookie")
            }
            
            _cookiesLiveData.postValue(cookies)
        }
        return _cookiesLiveData
    }
}
