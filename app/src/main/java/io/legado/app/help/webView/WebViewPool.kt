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

/**
 * WebView 对象池
 * 
 * 管理 WebView 的创建、复用和销毁，优化内存使用和性能。
 * 采用池化设计，避免频繁创建和销毁 WebView 带来的性能开销。
 * 
 * 主要功能：
 * 1. WebView 对象池管理（获取、释放、清理）
 * 2. 内联内容高度测量和平滑过渡
 * 3. 触摸事件后高度重新测量（处理懒加载图片）
 * 
 * 使用场景：
 * - 发现页 useWeb 内容显示
 * - 书籍详情页 useWeb 内容显示
 * - 视频播放页 useWeb 内容显示
 */
object WebViewPool {
    // 空白页面 URL，用于重置 WebView
    const val BLANK_HTML = "about:blank"
    // Base64 编码的 HTML 数据前缀
    const val DATA_HTML = "data:text/html;charset=utf-8;base64,"
    
    /**
     * 获取页面内容高度的 JavaScript 脚本
     * 通过比较 documentElement 和 body 的各种高度属性，获取最准确的页面高度
     */
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

    // 是否需要初始化清理定时器
    private var needInitialize = true
    // 池子总容量（闲置+使用），根据线程数动态计算，最小为 5
    private val CACHED_WEB_VIEW_MAX_NUM = max(AppConfig.threadCount / 10, 5)
    // 闲置5分钟后销毁
    private const val IDLE_TIME_OUT: Long = 5 * 60 * 1000
    // 最后一个闲置30分钟后销毁（保留一个备用）
    private const val IDLE_TIME_OUT_LAST: Long = 30 * 60 * 1000
    // 清理协程作用域
    private val cleanupScope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    // 清理任务
    private var cleanupJob: Job? = null

    /**
     * 生成下一个内联内容代次标识
     * 用于防止过期的回调执行，每次调用都会递增代次
     * 
     * @param webView 目标 WebView
     * @return 新的代次标识
     */
    private fun nextInlineContentGeneration(webView: WebView): Long {
        val generation = ((webView.getTag(R.id.inline_content_generation) as? Long) ?: 0L) + 1L
        webView.setTag(R.id.inline_content_generation, generation)
        return generation
    }

    /**
     * 获取当前内联内容代次标识
     * 
     * @param webView 目标 WebView
     * @return 当前代次标识
     */
    fun currentInlineContentGeneration(webView: WebView): Long {
        return (webView.getTag(R.id.inline_content_generation) as? Long) ?: 0L
    }

    /**
     * 从池中获取一个 WebView
     * 
     * 获取策略：
     * 1. 优先从闲置池中复用
     * 2. 闲置池为空时创建新实例
     * 3. 首次获取时启动清理定时器
     * 
     * @param context 上下文
     * @return 包装后的 WebView 对象
     */
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
                // 第一个 WebView 被获取时，恢复全局定时器
                realWebView.resumeTimers()
            }
            isInUse = true
        }
        inUsePool[pooledWebView.id] = pooledWebView
        return pooledWebView
    }

    /**
     * 将 WebView 释放回池中
     * 
     * 释放流程：
     * 1. 从使用池中移除
     * 2. 重置 WebView 状态（清除监听器、重置设置等）
     * 3. 加载空白页面重置 JS 环境
     * 4. 加入闲置池或销毁（池满时）
     * 
     * @param pooledWebView 要释放的 WebView 包装对象
     */
    // 释放WebView回池
    @Synchronized
    fun release(pooledWebView: PooledWebView) {
        if (inUsePool.remove(pooledWebView.id) == null) {
            // 不在使用池中，直接销毁
            pooledWebView.realWebView.destroy()
            return
        }
        // 重置WebView状态
        pooledWebView.realWebView.run {
            // 从父视图中移除
            (parent as? ViewGroup)?.removeView(this)
            // 重置布局参数
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
            // 切换回应用上下文
            pooledWebView.upContext(appCtx)
            if (idlePool.size >= CACHED_WEB_VIEW_MAX_NUM - inUsePool.size) {
                // 池子已满，直接销毁
                pooledWebView.realWebView.destroy()
                return
            }
            // 设置 WebViewClient 在加载空白页完成后将 WebView 加入闲置池
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
                            // 所有 WebView 都已释放，暂停全局定时器节省资源
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
     * 
     * 初始化高度测量代次，设置透明背景和滚动属性，重置布局参数。
     * 用于 WebView 作为内联内容嵌入列表时的初始化。
     * 
     * @param webView 目标 WebView
     * @param initialHeight 初始高度（像素），默认为屏幕高度的 35%
     */
    fun prepareForInlineContent(webView: WebView, initialHeight: Int = 0) {
        // 递增代次，使之前的回调失效
        nextInlineContentGeneration(webView)
        // 设置透明背景，让 HTML 中的背景色生效
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // 禁用过度滚动效果
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        // 隐藏滚动条
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        // 计算默认高度：使用传入高度或屏幕高度的 35%
        val defaultHeight = if (initialHeight > 0) {
            initialHeight
        } else {
            (webView.context.resources.displayMetrics.heightPixels * 0.35f).roundToInt()
        }
        // 设置布局参数
        val layoutParams = (webView.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).also {
            it.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = defaultHeight
        }
        webView.layoutParams = layoutParams
        // 滚动到顶部
        webView.scrollTo(0, 0)
        webView.requestLayout()
    }

    /**
     * 调整 WebView 高度以适应内容（便捷方法）
     * 
     * @param webView 目标 WebView
     * @param afterLayout 高度调整完成后的回调
     */
    fun fitInlineContent(webView: WebView, afterLayout: (() -> Unit)? = null) {
        fitInlineContent(webView, currentInlineContentGeneration(webView), afterLayout)
    }

    /**
     * 调整 WebView 高度以适应内容
     * 
     * 通过 JavaScript 获取内容实际高度并更新布局参数。
     * 使用代次标识防止过期的回调执行。
     * 
     * @param webView 目标 WebView
     * @param generation 代次标识，用于防止过期的回调执行
     * @param afterLayout 高度调整完成后的回调
     */
    fun fitInlineContent(
        webView: WebView,
        generation: Long,
        afterLayout: (() -> Unit)? = null
    ) {
        webView.post {
            // 检查代次是否匹配，防止过期回调
            if (currentInlineContentGeneration(webView) != generation) return@post
            // 计算备用高度（基于 WebView 的 contentHeight）
            val fallbackHeight = (webView.contentHeight * webView.resources.displayMetrics.density)
                .roundToInt()
            // 执行 JS 获取精确高度
            webView.evaluateJavascript(inlineHeightScript) { result ->
                // 再次检查代次
                if (currentInlineContentGeneration(webView) != generation) return@evaluateJavascript
                // 解析 JS 返回的高度值
                val jsHeight = result
                    ?.trim('"')
                    ?.toFloatOrNull()
                    ?.times(webView.resources.displayMetrics.density)
                    ?.roundToInt()
                    ?: 0
                // 取 JS 高度和备用高度的最大值
                val targetHeight = max(jsHeight, fallbackHeight)
                    .takeIf { it > 1 }
                    ?: ViewGroup.LayoutParams.WRAP_CONTENT
                // 更新布局参数
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
     * 
     * 使用 ValueAnimator 实现高度变化的平滑过渡，避免视觉跳变。
     * 优化：动画过程中不调用 requestLayout，只在结束时调用一次，减少布局重绘。
     * 
     * 高度变化策略：
     * 1. 高度变化 < 50px：直接设置，不使用动画
     * 2. 高度变化 >= 50px：使用 200ms 动画平滑过渡
     * 
     * @param webView 目标 WebView
     * @param generation 代次标识，用于防止过期的回调执行
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
            // 检查代次是否匹配，防止过期回调
            if (currentInlineContentGeneration(webView) != generation) return@post
            // 获取当前高度
            val currentHeight = webView.layoutParams?.height ?: 0
            // 计算备用高度（基于 WebView 的 contentHeight）
            val fallbackHeight = (webView.contentHeight * webView.resources.displayMetrics.density)
                .roundToInt()
            // 执行 JS 获取精确高度
            webView.evaluateJavascript(inlineHeightScript) { result ->
                // 再次检查代次
                if (currentInlineContentGeneration(webView) != generation) return@evaluateJavascript
                // 解析 JS 返回的高度值
                val jsHeight = result
                    ?.trim('"')
                    ?.toFloatOrNull()
                    ?.times(webView.resources.displayMetrics.density)
                    ?.roundToInt()
                    ?: 0
                // 取 JS 高度和备用高度的最大值作为目标高度
                val targetHeight = max(jsHeight, fallbackHeight)
                // 目标高度无效时使用 WRAP_CONTENT
                val finalHeight = if (targetHeight <= 1) {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    targetHeight
                }
                // 高度未变化时直接回调
                if (finalHeight == currentHeight) {
                    afterLayout?.invoke()
                    return@evaluateJavascript
                }
                // 计算高度差
                val heightDiff = kotlin.math.abs(
                    if (finalHeight == ViewGroup.LayoutParams.WRAP_CONTENT) 0 else finalHeight - currentHeight
                )
                if (heightDiff < 50 || finalHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    // 高度变化较小或使用 WRAP_CONTENT，直接设置，不使用动画
                    val layoutParams = (webView.layoutParams ?: ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        finalHeight
                    )).also {
                        it.width = ViewGroup.LayoutParams.MATCH_PARENT
                        it.height = finalHeight
                    }
                    webView.layoutParams = layoutParams
                    webView.requestLayout()
                    afterLayout?.invoke()
                } else {
                    // 高度变化较大，使用动画平滑过渡
                    android.animation.ValueAnimator.ofInt(currentHeight, finalHeight).apply {
                        this.duration = duration
                        // 动画更新监听：只更新高度值，不调用 requestLayout
                        addUpdateListener { animator ->
                            // 检查代次，过期则取消动画
                            if (currentInlineContentGeneration(webView) != generation) {
                                cancel()
                                return@addUpdateListener
                            }
                            val height = animator.animatedValue as Int
                            // 只更新高度，不触发布局
                            webView.layoutParams?.height = height
                        }
                        // 动画结束监听：触发一次布局并执行回调
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                if (currentInlineContentGeneration(webView) == generation) {
                                    // 动画结束时才触发布局
                                    webView.requestLayout()
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
     * 
     * 简化测量逻辑，只在内容加载完成后测量一次，避免反复重绘。
     * 支持多次延迟测量，用于处理内容加载时机不确定的情况。
     * 
     * @param webView 目标 WebView
     * @param afterLayout 高度调整完成后的回调
     * @param delays 延迟时间数组（毫秒），默认 [300L]
     */
    fun scheduleInlineContentFit(
        webView: WebView,
        afterLayout: (() -> Unit)? = null,
        delays: LongArray = longArrayOf(300L)
    ) {
        val generation = currentInlineContentGeneration(webView)
        delays.forEach { delayMillis ->
            webView.postDelayed({
                // 检查 WebView 是否仍然有效
                if (webView.handler == null || !webView.isAttachedToWindow) return@postDelayed
                fitInlineContent(webView, generation, afterLayout)
            }, delayMillis)
        }
    }

    /**
     * 安装触摸事件监听器
     * 
     * 在用户触摸后重新测量高度，用于处理动态加载内容（如图片懒加载）导致的高度变化。
     * 触摸后会进行多次延迟测量，确保图片加载完成后高度正确。
     * 
     * @param webView 目标 WebView
     * @param afterLayout 高度调整完成后的回调
     */
    fun installInlineContentRefitOnTouch(
        webView: WebView,
        afterLayout: (() -> Unit)? = null
    ) {
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                // 触摸结束后进行多次延迟测量，处理图片懒加载
                scheduleInlineContentFit(webView, afterLayout, longArrayOf(120L, 360L, 720L))
            }
            // 返回 false 不消费事件，让 WebView 正常处理触摸
            false
        }
    }

    /**
     * 创建新的 WebView 实例
     * 
     * @return 包装后的 WebView 对象
     */
    private fun createNewWebView(): PooledWebView {
        val webView = VisibleWebView(MutableContextWrapper(appCtx))
        preInitWebView(webView)
        return PooledWebView(webView, generateId())
    }

    /**
     * 生成唯一 ID
     * 
     * @return 格式为 "web_时间戳_随机数" 的唯一标识
     */
    private fun generateId(): String {
        return "web_${System.currentTimeMillis()}_${Random.nextLong()}"
    }

    /**
     * 预初始化 WebView
     * 
     * 设置 WebView 的基本参数，在创建时调用。
     * 
     * @param webView 目标 WebView
     */
    // 初始化
    @SuppressLint("SetJavaScriptEnabled")
    private fun preInitWebView(webView: WebView) {
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.settings.apply {
            // 启用 JavaScript
            javaScriptEnabled = true
            // 允许混合内容（HTTP 和 HTTPS）
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 启用 DOM 存储
            domStorageEnabled = true
            // 允许媒体自动播放
            mediaPlaybackRequiresUserGesture = false
            // 允许访问内容提供者
            allowContentAccess = true
            // 启用缩放控件但隐藏缩放按钮
            builtInZoomControls = true
            displayZoomControls = false
            // 固定文字缩放比例
            textZoom = 100
        }
    }

    /**
     * 启动清理定时器
     * 
     * 定时清理闲置过久的 WebView，释放内存。
     * 清理策略：
     * - 每 30 秒检查一次
     * - 闲置超过 5 分钟的 WebView 被销毁
     * - 最后一个 WebView 闲置 30 分钟后才销毁（保留备用）
     * - 所有 WebView 都被清理后，停止定时器
     */
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
                        // 最后一个 WebView 使用更长的超时时间
                        val timeout = if (index == 0) {
                            IDLE_TIME_OUT_LAST
                        } else {
                            IDLE_TIME_OUT
                        }
                        if (now - pooled.lastUseTime > timeout) {
                            toRemove.add(pooled)
                        }
                    }
                    // 从池中移除并销毁
                    toRemove.forEach { pooled ->
                        idlePool.remove(pooled)
                        try {
                            pooled.realWebView.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    // 闲置池为空时停止定时器
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
