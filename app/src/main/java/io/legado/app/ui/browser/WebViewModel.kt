package io.legado.app.ui.browser

import android.app.Application
import android.content.Intent
import android.util.Base64
import android.webkit.URLUtil
import android.webkit.WebView
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppConst.imagePathKey
import io.legado.app.constant.SourceType
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.ACache
import io.legado.app.utils.FileDoc
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import org.apache.commons.text.StringEscapeUtils
import java.util.Date
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.webView.WebJsExtensions.Companion.JS_INJECTION2

/**
 * WebView 视图模型
 * 管理 WebView 数据，包括源信息、URL、HTML 内容等
 * 支持图片保存、源验证结果保存、源禁用/删除等功能
 */
class WebViewModel(application: Application) : BaseViewModel(application) {
    var source: BaseSource? = null
    var intent: Intent? = null
    var baseUrl: String = ""
    var html: String? = null
    var localHtml: Boolean = false
    val headerMap: HashMap<String, String> = hashMapOf()
    var sourceVerificationEnable: Boolean = false
    var refetchAfterSuccess: Boolean = true
    var sourceName: String = ""
    var sourceOrigin: String = ""
    var sourceType = SourceType.book

    /**
     * 初始化数据
     * 从 Intent 中提取 URL、源信息等，并解析请求头
     * @param intent 包含初始化数据的 Intent
     * @param success 初始化成功回调
     */
    fun initData(
        intent: Intent,
        success: () -> Unit
    ) {
        execute {
            this@WebViewModel.intent = intent
            val url = intent.getStringExtra("url")
                ?: throw NoStackTraceException("url不能为空")
            sourceName = intent.getStringExtra("sourceName") ?: ""
            sourceOrigin = intent.getStringExtra("sourceOrigin") ?: ""
            sourceType = intent.getIntExtra("sourceType", SourceType.book)
            sourceVerificationEnable = intent.getBooleanExtra("sourceVerificationEnable", false)
            refetchAfterSuccess = intent.getBooleanExtra("refetchAfterSuccess", true)
            html = intent.getStringExtra("html")?.let{
                localHtml = true
                val headIndex = it.indexOf("<head", ignoreCase = true)
                if (headIndex >= 0) {
                    val closingHeadIndex = it.indexOf('>', startIndex = headIndex)
                    if (closingHeadIndex >= 0) {
                        val insertPos = closingHeadIndex + 1
                        StringBuilder(it).insert(insertPos, "<script>$JS_INJECTION2</script>").toString()
                    } else {
                        "<head><script>$JS_INJECTION2</script></head>$it"
                    }
                } else {
                    "<head><script>$JS_INJECTION2</script></head>$it"
                }
            }
            source = SourceHelp.getSource(sourceOrigin, sourceType)
            val analyzeUrl = AnalyzeUrl(url, source = source, coroutineContext = coroutineContext)
            baseUrl = analyzeUrl.url
            headerMap.putAll(analyzeUrl.headerMap)
            if (analyzeUrl.isPost()) {
                html = analyzeUrl.getStrResponseAwait(useWebView = false).body
            }
        }.onSuccess {
            success.invoke()
        }.onError {
            context.toastOnUi("error\n${it.localizedMessage}")
            it.printOnDebug()
        }
    }

    /**
     * 保存网页图片
     * 支持从 URL 或 Base64 数据保存图片
     * @param webPic 图片 URL 或 Base64 数据
     * @param path 保存路径
     */
    fun saveImage(webPic: String?, path: String) {
        webPic ?: return
        execute {
            val fileName = "${AppConst.fileNameFormat.format(Date(System.currentTimeMillis()))}.jpg"
            webData2bitmap(webPic)?.let { byteArray ->
                val fileDoc = FileDoc.fromDir(path)
                val picFile = fileDoc.createFileIfNotExist(fileName)
                picFile.openOutputStream().getOrThrow().use {
                    it.write(byteArray)
                }
            } ?: throw Throwable("NULL")
        }.onError {
            ACache.get().remove(imagePathKey)
            context.toastOnUi("保存图片失败:${it.localizedMessage}")
        }.onSuccess {
            context.toastOnUi("保存成功")
        }
    }

    /**
     * 将网页数据转换为字节数组
     * 支持 URL 和 Base64 格式
     * @param data 图片数据（URL 或 Base64）
     * @return 图片字节数组
     */
    private suspend fun webData2bitmap(data: String): ByteArray? {
        return if (URLUtil.isValidUrl(data)) {
            okHttpClient.newCallResponseBody {
                url(data)
            }.bytes()
        } else {
            Base64.decode(data.split(",").toTypedArray()[1], Base64.DEFAULT)
        }
    }

    /**
     * 保存源验证结果
     * 用于 Cloudflare 等验证通过后保存验证信息
     * @param WebView 用于获取页面 HTML 内容
     * @param success 保存成功回调
     */
    fun saveVerificationResult(webView: WebView, success: () -> Unit) {
        if (!sourceVerificationEnable) {
            return success.invoke()
        }
        if (refetchAfterSuccess) {
            execute {
                val url = intent!!.getStringExtra("url")!!
                val source = appDb.bookSourceDao.getBookSource(sourceOrigin)
                if (html == null) {
                    html = AnalyzeUrl(
                        url,
                        headerMapF = headerMap,
                        source = source,
                        coroutineContext = coroutineContext
                    ).getStrResponseAwait(useWebView = false).body
                }
                SourceVerificationHelp.setResult(sourceOrigin, html ?: "", baseUrl)
            }.onSuccess {
                success.invoke()
            }
        } else {
            webView.evaluateJavascript("document.documentElement.outerHTML") {
                execute {
                    html = StringEscapeUtils.unescapeJson(it).trim('"')
                }.onSuccess {
                    SourceVerificationHelp.setResult(sourceOrigin, html ?: "",  webView.url ?: "")
                    success.invoke()
                }
            }
        }
    }

    /**
     * 禁用源
     * @param block 禁用成功回调
     */
    fun disableSource(block: () -> Unit) {
        execute {
            SourceHelp.enableSource(sourceOrigin, sourceType, false)
        }.onSuccess {
            block.invoke()
        }
    }

    /**
     * 删除源
     * @param block 删除成功回调
     */
    fun deleteSource(block: () -> Unit) {
        execute {
            SourceHelp.deleteSource(sourceOrigin, sourceType)
        }.onSuccess {
            block.invoke()
        }
    }

}