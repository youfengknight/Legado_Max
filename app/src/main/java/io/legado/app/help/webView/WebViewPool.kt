package io.legado.app.help.webView

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.rss.read.VisibleWebView
import io.legado.app.utils.setDarkeningAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.util.Stack
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object WebViewPool {
    const val BLANK_HTML = "about:blank"
    const val DATA_HTML = "data:text/html;charset=utf-8;base64,"
    private val inlineHeightScript = """
        (function() {
            var doc = document.documentElement;
            var body = document.body;
            return Math.max(
                doc ? doc.scrollHeight : 0,
                doc ? doc.offsetHeight : 0,
                doc ? doc.clientHeight : 0,
                body ? body.scrollHeight : 0,
                body ? body.offsetHeight : 0,
                body ? body.clientHeight : 0
            );
        })();
    """.trimIndent()
    // 未使用的、已预初始化的WebView池 (使用栈结构，后进先出，复用缓存)
    private val idlePool = Stack<PooledWebView>()
    // 正在使用的WebView集合
    private val inUsePool = mutableMapOf<String, PooledWebView>()

    private var needInitialize = true
    private val CACHED_WEB_VIEW_MAX_NUM = max(AppConfig.threadCount / 10, 5) // 池子总容量（闲置+使用）
    private const val IDLE_TIME_OUT: Long = 5 * 60 * 1000 // 闲置5分钟后销毁
    private const val IDLE_TIME_OUT_LAST: Long = 30 * 60 * 1000 // 最后一个闲置30分钟后销毁
    private val cleanupScope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    private var cleanupJob: Job? = null

    private fun nextInlineContentGeneration(webView: WebView): Long {
        val generation = ((webView.getTag(R.id.inline_content_generation) as? Long) ?: 0L) + 1L
        webView.setTag(R.id.inline_content_generation, generation)
        return generation
    }

    fun currentInlineContentGeneration(webView: WebView): Long {
        return (webView.getTag(R.id.inline_content_generation) as? Long) ?: 0L
    }

    // 获取一个WebView
    @Synchronized
    fun acquire(context: Context): PooledWebView {
        val pooledWebView = if (idlePool.isNotEmpty()) {
            idlePool.pop() // 复用闲置实例
        } else {
            if (needInitialize) {
                needInitialize = false
                startCleanupTimer()
            }
            createNewWebView() // 创建新实例
        }
        pooledWebView.upContext(context).apply {
            realWebView.settings.setDarkeningAllowed(AppConfig.isNightTheme) //设置是否夜间
            if (inUsePool.isEmpty()) {
                realWebView.resumeTimers()
            }
            isInUse = true
        }
        inUsePool[pooledWebView.id] = pooledWebView
        return pooledWebView
    }

    // 释放WebView回池
    @Synchronized
    fun release(pooledWebView: PooledWebView) {
        if (inUsePool.remove(pooledWebView.id) == null) {
            pooledWebView.realWebView.destroy()
            return
        }
        // 重置WebView状态
        pooledWebView.realWebView.run {
            (parent as? ViewGroup)?.removeView(this)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            stopLoading()
            clearFocus() //清除焦点
            setOnLongClickListener(null)
            // 清除触摸监听器，避免内存泄漏和错误回调
            setOnTouchListener(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener(null)
            }
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            setDownloadListener(null)
            outlineProvider = null
            clipToOutline = false
            webChromeClient = null
            clearFormData() //清除表单数据
            clearMatches() //清除查找匹配项
            clearDisappearingChildren() //清除消失中的子视图
            clearAnimation() //清除动画
            pooledWebView.upContext(appCtx)
            if (idlePool.size >= CACHED_WEB_VIEW_MAX_NUM - inUsePool.size) {
                // 池子已满，直接销毁
                pooledWebView.realWebView.destroy()
                return
            }
            webViewClient = object: WebViewClient() {
                @SuppressLint("SetJavaScriptEnabled")
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != BLANK_HTML) return
                    view?.let{ webview ->
                        webview.settings.apply {
                            javaScriptEnabled = false
                            javaScriptEnabled = true // 禁用再启用来重置js环境，注意需要禁用的订阅源需要再次执行
                            blockNetworkImage = false // 确保允许加载网络图片
                            cacheMode = WebSettings.LOAD_DEFAULT // 重置缓存模式
                            useWideViewPort = false // 恢复默认关闭宽视模式
                            loadWithOverviewMode = false // 恢复默认
                            textZoom = 100
                        }
                        if (inUsePool.isEmpty()) {
                            webview.pauseTimers()
                        }
                        webview.onPause()
                    }
                    pooledWebView.isInUse = false
                    pooledWebView.lastUseTime = System.currentTimeMillis()
                    idlePool.push(pooledWebView)
                }
            }
            loadUrl(BLANK_HTML)
        }
    }

    /**
     * 准备内联内容 WebView
     * 初始化高度测量代次，设置背景色和滚动属性，重置布局参数
     * @param initialHeight 初始高度（像素），默认为屏幕高度的 1/3
     */
    fun prepareForInlineContent(webView: WebView, initialHeight: Int = 0) {
        nextInlineContentGeneration(webView)
        webView.setBackgroundColor(webView.context.backgroundColor)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        val defaultHeight = if (initialHeight > 0) {
            initialHeight
        } else {
            (webView.context.resources.displayMetrics.heightPixels * 0.35f).roundToInt()
        }
        val layoutParams = (webView.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).also {
            it.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = defaultHeight
        }
        webView.layoutParams = layoutParams
        webView.scrollTo(0, 0)
        webView.requestLayout()
    }

    fun fitInlineContent(webView: WebView, afterLayout: (() -> Unit)? = null) {
        fitInlineContent(webView, currentInlineContentGeneration(webView), afterLayout)
    }

    /**
     * 调整 WebView 高度以适应内容
     * 通过 JavaScript 获取内容实际高度并更新布局参数
     * @param generation 代次标识，用于防止过期的回调执行
     * @param afterLayout 高度调整完成后的回调
     */
    fun fitInlineContent(
        webView: WebView,
        generation: Long,
        afterLayout: (() -> Unit)? = null
    ) {
        webView.post {
            if (currentInlineContentGeneration(webView) != generation) return@post
            val fallbackHeight = (webView.contentHeight * webView.resources.displayMetrics.density)
                .roundToInt()
            webView.evaluateJavascript(inlineHeightScript) { result ->
                if (currentInlineContentGeneration(webView) != generation) return@evaluateJavascript
                val jsHeight = result
                    ?.trim('"')
                    ?.toFloatOrNull()
                    ?.times(webView.resources.displayMetrics.density)
                    ?.roundToInt()
                    ?: 0
                val targetHeight = max(jsHeight, fallbackHeight)
                    .takeIf { it > 1 }
                    ?: ViewGroup.LayoutParams.WRAP_CONTENT
                val layoutParams = (webView.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeight
                )).also {
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.height = targetHeight
                }
                webView.layoutParams = layoutParams
                webView.requestLayout()
                afterLayout?.invoke()
            }
        }
    }

    /**
     * 平滑调整 WebView 高度
     * 使用 ValueAnimator 实现高度变化的平滑过渡
     * @param generation 代次标识
     * @param afterLayout 高度调整完成后的回调
     * @param duration 动画时长（毫秒），默认 200ms
     */
    fun fitInlineContentSmooth(
        webView: WebView,
        generation: Long,
        afterLayout: (() -> Unit)? = null,
        duration: Long = 200L
    ) {
        webView.post {
            if (currentInlineContentGeneration(webView) != generation) return@post
            val currentHeight = webView.layoutParams?.height ?: 0
            val fallbackHeight = (webView.contentHeight * webView.resources.displayMetrics.density)
                .roundToInt()
            webView.evaluateJavascript(inlineHeightScript) { result ->
                if (currentInlineContentGeneration(webView) != generation) return@evaluateJavascript
                val jsHeight = result
                    ?.trim('"')
                    ?.toFloatOrNull()
                    ?.times(webView.resources.displayMetrics.density)
                    ?.roundToInt()
                    ?: 0
                val targetHeight = max(jsHeight, fallbackHeight)
                    .takeIf { it > 1 }
                    ?: ViewGroup.LayoutParams.WRAP_CONTENT
                val targetHeightInt = if (targetHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    currentHeight
                } else {
                    targetHeight
                }
                if (targetHeightInt == currentHeight) {
                    afterLayout?.invoke()
                    return@evaluateJavascript
                }
                val heightDiff = kotlin.math.abs(targetHeightInt - currentHeight)
                if (heightDiff < 50) {
                    val layoutParams = (webView.layoutParams ?: ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        targetHeightInt
                    )).also {
                        it.width = ViewGroup.LayoutParams.MATCH_PARENT
                        it.height = targetHeightInt
                    }
                    webView.layoutParams = layoutParams
                    afterLayout?.invoke()
                } else {
                    android.animation.ValueAnimator.ofInt(currentHeight, targetHeightInt).apply {
                        this.duration = duration
                        addUpdateListener { animator ->
                            if (currentInlineContentGeneration(webView) != generation) {
                                cancel()
                                return@addUpdateListener
                            }
                            val height = animator.animatedValue as Int
                            webView.layoutParams?.let { lp ->
                                if (lp.height != height) {
                                    lp.height = height
                                    webView.requestLayout()
                                }
                            }
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                if (currentInlineContentGeneration(webView) == generation) {
                                    afterLayout?.invoke()
                                }
                            }
                        })
                        start()
                    }
                }
            }
        }
    }

    /**
     * 调度高度测量
     * 简化测量逻辑，只在内容加载完成后测量一次，避免反复重绘
     * @param delay 延迟时间（毫秒），默认 300ms
     */
    fun scheduleInlineContentFit(
        webView: WebView,
        afterLayout: (() -> Unit)? = null,
        delays: LongArray = longArrayOf(300L)
    ) {
        val generation = currentInlineContentGeneration(webView)
        delays.forEach { delayMillis ->
            webView.postDelayed({
                if (webView.handler == null || !webView.isAttachedToWindow) return@postDelayed
                fitInlineContent(webView, generation, afterLayout)
            }, delayMillis)
        }
    }

    /**
     * 安装触摸事件监听器，在用户触摸后重新测量高度
     * 用于处理动态加载内容（如图片懒加载）导致的高度变化
     */
    fun installInlineContentRefitOnTouch(
        webView: WebView,
        afterLayout: (() -> Unit)? = null
    ) {
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                scheduleInlineContentFit(webView, afterLayout, longArrayOf(120L, 360L, 720L))
            }
            false
        }
    }

    private fun createNewWebView(): PooledWebView {
        val webView = VisibleWebView(MutableContextWrapper(appCtx))
        preInitWebView(webView)
        return PooledWebView(webView, generateId())
    }

    private fun generateId(): String {
        return "web_${System.currentTimeMillis()}_${Random.nextLong()}"
    }

    // 初始化
    @SuppressLint("SetJavaScriptEnabled")
    private fun preInitWebView(webView: WebView) {
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.settings.apply {
            javaScriptEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
        }
    }

    // 定时清理闲置过久的WebView
    private fun startCleanupTimer() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = cleanupScope.launch {
            while (true) {
                delay(30_000) // 每30秒执行一次清理
                val now = System.currentTimeMillis()
                val toRemove = mutableListOf<PooledWebView>()
                var shouldCancel = false
                synchronized(this@WebViewPool) {
                    for ((index, pooled) in idlePool.withIndex()) {
                        val timeout = if (index == 0) {
                            IDLE_TIME_OUT_LAST
                        } else {
                            IDLE_TIME_OUT
                        }
                        if (now - pooled.lastUseTime > timeout) {
                            toRemove.add(pooled)
                        }
                    }
                    toRemove.forEach { pooled ->
                        idlePool.remove(pooled)
                        try {
                            pooled.realWebView.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (idlePool.isEmpty()) {
                        shouldCancel = true
                    }
                }
                if (shouldCancel) {
                    needInitialize = true
                    this@launch.cancel()
                }
            }
        }
    }

}
