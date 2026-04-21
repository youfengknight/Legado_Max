package io.legado.app.ui.widget.image

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.AppCompatImageView
import androidx.collection.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.model.BookCover
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray
import android.view.ViewOutlineProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx

/**
 * 封面图片视图
 * 
 * 支持多种封面来源：
 * - 网络图片（通过URL加载）
 * - 默认封面（全局设置）
 * - HTML模板生成封面（自定义HTML代码）
 * - Canvas绘制书名作者（无封面时的兜底方案）
 */
@Suppress("unused")
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    companion object {
        private val nameBitmapCache by lazy { LruCache<String, Bitmap>(33) }
        private val needNameBitmap by lazy { LruCache<String, Boolean>(99) }
        private val htmlCoverCache by lazy { LruCache<String, Bitmap>(50) }

        /**
         * 清除HTML封面缓存
         * 
         * 在模板内容变更、切换选中模板、启用/禁用HTML封面时调用，
         * 确保书架上的封面能及时刷新
         */
        fun clearHtmlCoverCache() {
            htmlCoverCache.evictAll()
        }
    }
    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var currentJob: Job? = null
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
    var bitmapPath: String? = null
        private set
    private var name: String? = null
    private var author: String? = null
    private var nameHeight = 0f
    private var authorHeight = 0f
    private var isHtmlCover = false
    private val drawBookName = BookCover.drawBookName
    private val drawBookAuthor by lazy { BookCover.drawBookAuthor }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        if (params != null) {
            val width = params.width
            if (width >= 0) {
                params.height = width * 4 / 3
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        super.setLayoutParams(params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = measuredWidth * 4 / 3
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, 12f)
            }
        }
        clipToOutline = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawBookName || isHtmlCover) return
        val currentName = this.name ?: return
        if (AppConfig.useDefaultCover || needNameBitmap[bitmapPath.toString()] == true) {
            val currentAuthor = this.author
            val pathName = if (drawBookAuthor){
                currentName + currentAuthor
            } else {
                currentName
            }
            val cacheBitmap =  nameBitmapCache[pathName + width]
            if (cacheBitmap != null) {
                canvas.drawBitmap(cacheBitmap, 0f, 0f, null)
                return
            }
            drawNameAuthor(pathName, currentName, currentAuthor, false)
        }
    }

    private fun drawNameAuthor(pathName: String, name: String, author: String?, asyncAwait: Boolean = true) {
        generateCoverAsync(pathName, name, author, asyncAwait)
    }
    private fun generateCoverAsync(pathName: String, name: String, author: String?, asyncAwait: Boolean) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                if (asyncAwait) {
                    withTimeoutOrNull(1200) {
                        triggerChannel.receive()
                    }
                    ensureActive()
                }
                if (width == 0) {
                    var attempts = 0
                    do {
                        delay(1L)
                        attempts++
                    } while (width == 0 && attempts < 2000)
                }
                ensureActive()
                val bitmap = generateCoverBitmap(name, author)
                ensureActive()
                needNameBitmap.put(bitmapPath.toString(), true)
                nameBitmapCache.put(pathName + width, bitmap)
                invalidate()
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateCoverBitmap(name: String?, author: String?): Bitmap {
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        val bitmap = createBitmap(width, height)
        val bitmapCanvas = Canvas(bitmap)
        var startX = width * 0.2f
        var startY = viewHeight * 0.2f
        val backgroundColor = appCtx.backgroundColor
        val accentColor = appCtx.accentColor
        val namePaint = TextPaint().apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        name?.toStringArray()?.let { name ->
            var line = 0
            namePaint.textSize = viewWidth / 7
            namePaint.strokeWidth = namePaint.textSize / 6
            name.forEachIndexed { index, char ->
                namePaint.color = backgroundColor
                namePaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(char, startX, startY, namePaint)
                namePaint.color = accentColor
                namePaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(char, startX, startY, namePaint)
                startY += namePaint.textHeight
                if (startY > viewHeight * 0.9) {
                    if ((name.size - index - 1) == 1) {
                        startY -= namePaint.textHeight / 5
                        namePaint.textSize = viewWidth / 9
                        return@forEachIndexed
                    }
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                }
                else if (startY > viewHeight * 0.8 && (name.size - index - 1) > 2) {
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                }
            }
        }
        if (!drawBookAuthor){
            return bitmap
        }
        val authorPaint = TextPaint(namePaint).apply {
            typeface = Typeface.DEFAULT
        }
        author?.toStringArray()?.let { author ->
            authorPaint.textSize = viewWidth / 10
            authorPaint.strokeWidth = authorPaint.textSize / 5
            startX = width * 0.8f
            startY = viewHeight * 0.95f - author.size * authorPaint.textHeight
            startY = maxOf(startY, viewHeight * 0.3f)
            author.forEach {
                authorPaint.color = backgroundColor
                authorPaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(it, startX, startY, authorPaint)
                authorPaint.color = accentColor
                authorPaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(it, startX, startY, authorPaint)
                startY += authorPaint.textHeight
                if (startY > viewHeight * 0.95) {
                    return@let
                }
            }
        }
        return bitmap
    }

    fun setHeight(height: Int) {
        val width = height * 3 / 4
        minimumWidth = width
    }

    private val glideListener by lazy {
        object : RequestListener<Drawable> {

            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                triggerChannel.trySend(Unit)
                needNameBitmap.put(bitmapPath.toString(), true)
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                currentJob?.cancel()
                currentJob = null
                needNameBitmap.remove(bitmapPath.toString())
                invalidate()
                return false
            }

        }
    }

    fun load(
        searchBook: SearchBook,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null
    ) {
        load(searchBook.coverUrl, searchBook.name, searchBook.author, loadOnlyWifi, searchBook.origin, fragment, lifecycle)
    }

    fun load(
        book: Book,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        onLoadFinish: (() -> Unit)? = null
    ) {
       load(book.getDisplayCover(), book.name, book.author, loadOnlyWifi, book.origin, fragment, lifecycle, onLoadFinish)
    }

    fun load(
        path: String? = null,
        name: String? = null,
        author: String? = null,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        onLoadFinish: (() -> Unit)? = null
    ) {
        val currentAuthor = author?.replace(AppPattern.bdRegex, "")?.trim()?.also {
            this.author = it
        }
        val currentName = name?.replace(AppPattern.bdRegex, "")?.trim()?.also {
            this.name = it
        }
        this.bitmapPath = path

        // 检查是否启用HTML封面生成（由封面配置页的开关控制）
        val htmlTemplate = CoverHtmlTemplateConfig.getSelectedTemplate()
        if (appCtx.getPrefBoolean(PreferKey.coverHtmlEnable) && htmlTemplate.htmlCode.isNotBlank() && currentName != null) {
            isHtmlCover = true
            loadHtmlCover(currentName, currentAuthor, onLoadFinish)
            return
        }

        isHtmlCover = false

        if (AppConfig.useDefaultCover) {
            ImageLoader.load(context, BookCover.defaultDrawable)
                .centerCrop()
                .into(this)
        } else {
            if (drawBookName && currentName != null) {
                val pathName = if (drawBookAuthor){
                    currentName + currentAuthor
                } else {
                    currentName
                }
                drawNameAuthor(pathName, currentName, currentAuthor, true)
            }
            var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
            if (sourceOrigin != null) {
                options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
            }
            var builder = if (fragment != null && lifecycle != null) {
                ImageLoader.load(fragment, lifecycle, path)
            } else {
                ImageLoader.load(context, path)
            }
            builder = builder.apply(options)
                .placeholder(BookCover.defaultDrawable)
                .error(BookCover.defaultDrawable)
                .listener(glideListener)
            if (onLoadFinish != null) {
                builder = builder.addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }
                })
            }
            builder
                .centerCrop()
                .into(this)
        }
    }

    /**
     * 加载HTML封面
     * 
     * 流程：
     * 1. 等待View布局完成（width/height > 0），超时则使用默认封面
     * 2. 查询缓存，命中则直接使用
     * 3. 获取当前选中模板，替换变量后通过WebView渲染生成Bitmap
     * 4. 缓存结果并显示
     * 
     * 渲染使用固定尺寸600x900，不依赖CoverImageView的实际像素大小，
     * 避免小尺寸下CSS视口过小导致文字溢出或偏移
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun loadHtmlCover(bookName: String, author: String?, onLoadFinish: (() -> Unit)?) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                if (width <= 0 || height <= 0) {
                    var attempts = 0
                    do {
                        delay(16L)
                        attempts++
                    } while ((width <= 0 || height <= 0) && attempts < 100)
                }
                if (width <= 0 || height <= 0) {
                    setImageDrawable(BookCover.defaultDrawable)
                    onLoadFinish?.invoke()
                    return@launch
                }

                val cacheKey = "$bookName-$author"
                val cachedBitmap = htmlCoverCache[cacheKey]
                if (cachedBitmap != null) {
                    setImageDrawable(cachedBitmap.toDrawable(resources))
                    onLoadFinish?.invoke()
                    return@launch
                }

                val htmlTemplate = CoverHtmlTemplateConfig.getSelectedTemplate()
                val htmlCode = htmlTemplate.htmlCode
                if (htmlCode.isBlank()) {
                    setImageDrawable(BookCover.defaultDrawable)
                    onLoadFinish?.invoke()
                    return@launch
                }

                val renderedHtml = BookCover.renderHtmlTemplate(htmlCode, bookName, author ?: "")

                val bitmap = generateHtmlCoverBitmap(renderedHtml)

                if (bitmap != null) {
                    htmlCoverCache.put(cacheKey, bitmap)
                    setImageDrawable(bitmap.toDrawable(resources))
                } else {
                    setImageDrawable(BookCover.defaultDrawable)
                }
                onLoadFinish?.invoke()
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
                setImageDrawable(BookCover.defaultDrawable)
                onLoadFinish?.invoke()
            }
        }
    }

    /**
     * 使用WebView生成HTML封面Bitmap
     * 
     * 使用固定尺寸600x900渲染，确保CSS视口足够大，
     * 文字和布局在不同设备上表现一致。
     * 使用applicationContext避免Activity泄漏，每次用完即销毁。
     * 设置超时保护（最多等待2.5秒），超时返回null使用默认封面。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun generateHtmlCoverBitmap(html: String): Bitmap? {
        val renderWidth = 600
        val renderHeight = 900
        return withContext(Dispatchers.Main) {
            var wv: WebView? = null
            try {
                wv = WebView(context.applicationContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.useWideViewPort = false
                wv.settings.loadWithOverviewMode = false
                wv.setInitialScale(100)

                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(renderWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(renderHeight, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, renderWidth, renderHeight)

                var renderComplete = false

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.postDelayed({
                            if (renderComplete) return@postDelayed
                            renderComplete = true
                        }, 300)
                    }
                }

                wv.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)

                var attempts = 0
                while (!renderComplete && attempts < 40) {
                    delay(50)
                    attempts++
                }

                if (!renderComplete) {
                    renderComplete = true
                }

                val bitmap = try {
                    wv.measure(
                        View.MeasureSpec.makeMeasureSpec(renderWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(renderHeight, View.MeasureSpec.EXACTLY)
                    )
                    wv.layout(0, 0, renderWidth, renderHeight)
                    val bmp = createBitmap(renderWidth, renderHeight)
                    val canvas = Canvas(bmp)
                    wv.draw(canvas)
                    bmp
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    wv?.stopLoading()
                    wv?.destroy()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        currentJob?.cancel()
        currentJob = null
        super.onDetachedFromWindow()
    }

}
