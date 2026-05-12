package io.legado.app.ui.book.read.page.entities

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.text.TextPaint
import androidx.annotation.Keep
import io.legado.app.help.PaintPool
import io.legado.app.help.book.isImage
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextPage.Companion.emptyTextPage
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.canvasrecorder.recordIfNeededThenDraw
import io.legado.app.utils.dpToPx
import splitties.init.appCtx

/**
 * 行信息
 */
@Keep
@Suppress("unused", "MemberVisibilityCanBePrivate")
data class TextLine(
    var text: String = "",
    private val textColumns: ArrayList<BaseColumn> = arrayListOf(),
    var lineTop: Float = 0f,
    var lineBase: Float = 0f,
    var lineBottom: Float = 0f,
    var indentWidth: Float = 0f,
    var paragraphNum: Int = 0,
    var chapterPosition: Int = 0,
    var pagePosition: Int = 0,
    val isTitle: Boolean = false,
    var isParagraphEnd: Boolean = false,
    var isImage: Boolean = false,
    var isHtml: Boolean = false,
    var startX: Float = 0f,
    var indentSize: Int = 0,
    var extraLetterSpacing: Float = 0f,
    var extraLetterSpacingOffsetX: Float = 0f,
    var wordSpacing: Float = 0f,
    var exceed: Boolean = false,
    var onlyTextColumn: Boolean = true,
) {

    val columns: List<BaseColumn> get() = textColumns
    val charSize: Int get() = text.length
    val lineStart: Float get() = textColumns.firstOrNull()?.start ?: 0f
    val lineEnd: Float get() = textColumns.lastOrNull()?.end ?: 0f
    val chapterIndices: IntRange get() = chapterPosition..chapterPosition + charSize
    val height: Float inline get() = lineBottom - lineTop
    val canvasRecorder = CanvasRecorderFactory.create()
    var searchResultColumnCount = 0
    var isReadAloud: Boolean = false
        set(value) {
            if (field != value) {
                invalidate()
            }
            if (value) {
                textPage.hasReadAloudSpan = true
            }
            field = value
        }
    var textPage: TextPage = emptyTextPage
    var isLeftLine = true

    /**
     * 向行中添加文本列
     */
    fun addColumn(column: BaseColumn) {
        if (column !is TextColumn) {
            onlyTextColumn = false
        }
        column.textLine = this
        textColumns.add(column)
    }

    /**
     * 向行中批量添加文本列
     */
    fun addColumns(columns: Collection<BaseColumn>) {
        onlyTextColumn = false
        columns.forEach { column ->
            column.textLine = this
        }
        textColumns.addAll(columns)
    }

    /**
     * 获取指定位置的文本列，越界时返回最后一个
     */
    fun getColumn(index: Int): BaseColumn {
        return textColumns.getOrElse(index) {
            textColumns.last()
        }
    }

    /**
     * 从后向前获取指定位置的文本列
     */
    fun getColumnReverseAt(index: Int, offset: Int = 0): BaseColumn {
        return textColumns[textColumns.lastIndex - offset - index]
    }

    /**
     * 获取行内文本列数量
     */
    fun getColumnsCount(): Int {
        return textColumns.size
    }

    /**
     * 更新行的顶部、底部和基线位置
     */
    fun upTopBottom(durY: Float, textHeight: Float, fontMetrics: Paint.FontMetrics) {
        lineTop = ChapterProvider.paddingTop + durY
        lineBottom = lineTop + textHeight
        lineBase = lineBottom - fontMetrics.descent
    }

    /**
     * 判断触摸坐标是否在当前行范围内
     */
    fun isTouch(x: Float, y: Float, relativeOffset: Float): Boolean {
        return y > lineTop + relativeOffset
                && y < lineBottom + relativeOffset
                && x >= lineStart
                && x <= lineEnd + 20.dpToPx()
    }

    /**
     * 判断触摸Y坐标是否在当前行范围内
     */
    fun isTouchY(y: Float, relativeOffset: Float): Boolean {
        return y > lineTop + relativeOffset
                && y < lineBottom + relativeOffset
    }

    /**
     * 判断行是否在可视区域内
     */
    fun isVisible(relativeOffset: Float): Boolean {
        val top = lineTop + relativeOffset
        val bottom = lineBottom + relativeOffset
        val width = bottom - top
        val visibleTop = ChapterProvider.paddingTop
        val visibleBottom = ChapterProvider.visibleBottom
        val visible = when {
            // 完全可视
            top >= visibleTop && bottom <= visibleBottom -> true
            top <= visibleTop && bottom >= visibleBottom -> true
            // 上方第一行部分可视
            top < visibleTop && bottom > visibleTop && bottom < visibleBottom -> {
                if (isImage) {
                    true
                } else {
                    val visibleRate = (bottom - visibleTop) / width
                    visibleRate > 0.6
                }
            }
            // 下方第一行部分可视
            top > visibleTop && top < visibleBottom && bottom > visibleBottom -> {
                if (isImage) {
                    true
                } else {
                    val visibleRate = (visibleBottom - top) / width
                    visibleRate > 0.6
                }
            }
            // 不可视
            else -> false
        }
        return visible
    }

    /**
     * 绘制整行内容，包含优化渲染和普通渲染两种模式
     */
    fun draw(view: ContentTextView, canvas: Canvas) {
        if (AppConfig.optimizeRender) {
            canvasRecorder.recordIfNeededThenDraw(canvas, view.width, height.toInt()) {
                drawTextLine(view, this)
            }
        } else {
            drawTextLine(view, canvas)
        }
    }

    /**
     * 绘制行内文本和列内容，包含搜索高亮、墨水屏下划线、自定义下划线等
     */
    private fun drawTextLine(view: ContentTextView, canvas: Canvas) {
        drawCurrentSearchResultBackgrounds(canvas)
        drawStyledBackgrounds(canvas)
        if (checkFastDraw()) {
            fastDrawTextLine(view, canvas)
        } else {
            for (i in columns.indices) {
                columns[i].draw(view, canvas)
            }
        }

        // 墨水屏模式下的朗读和搜索下划线
        if (AppConfig.isEInkMode && (isReadAloud || searchResultColumnCount > 0)) {
            val underlinePaint = PaintPool.obtain()
            underlinePaint.set(ChapterProvider.contentPaint)
            underlinePaint.strokeWidth = einkUnderlineWidth
            val lineY = height - einkUnderlineWidth
            canvas.drawLine(lineStart + indentWidth, lineY, lineEnd, lineY, underlinePaint)
            PaintPool.recycle(underlinePaint)
        }

        drawStyledUnderlines(canvas)

        val underlineMode = ReadBookConfig.underlineMode
        if (underlineMode == 0) return
        if (!isImage && !isHtml && ReadBook.book?.isImage != true) {
            drawUnderline(canvas, underlineMode)
        }
    }

    /**
     * 快速绘制纯文本行，适用于优化渲染模式
     */
    @SuppressLint("NewApi")
    private fun fastDrawTextLine(view: ContentTextView, canvas: Canvas) {
        val textPaint = if (isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = if (isReadAloud) {
            ReadBookConfig.textAccentColor
        } else {
            ReadBookConfig.textColor
        }
        if (textPaint.color != textColor) {
            textPaint.color = textColor
        }
        val paint = PaintPool.obtain()
        paint.set(textPaint)
        val letterSpacing = paint.letterSpacing * paint.textSize
        val letterSpacingHalf = letterSpacing * 0.5f
        if (extraLetterSpacing != 0f) {
            paint.letterSpacing += extraLetterSpacing
        }
        if (wordSpacing != 0f) {
            paint.wordSpacing = wordSpacing
        }
        val offsetX = if (atLeastApi35) letterSpacingHalf else extraLetterSpacingOffsetX
        canvas.drawText(text, indentSize, text.length, startX + offsetX, lineBase - lineTop, paint)
        PaintPool.recycle(paint)
        for (i in columns.indices) {
            val column = columns[i] as TextColumn
            if (column.selected && !column.isSearchResult) {
                canvas.drawRect(column.start, 0f, column.end, height, view.selectedPaint)
            }
        }
    }

    /**
     * 绘制全局下划线（朗读标记除外），支持实线/虚线/波浪线/点线
     */
    private fun drawUnderline(canvas: Canvas, underlineMode: Int) {
        val underlineWidth = ReadBookConfig.durConfig.underlineWidth
        val paint = PaintPool.obtain()
        paint.set(ChapterProvider.contentPaint)
        paint.strokeWidth = underlineWidth.dpToPx().toFloat()
        paint.style = android.graphics.Paint.Style.STROKE
        paint.isAntiAlias = true
        val distance = (ChapterProvider.lineSpacingExtra * 10 - 11).coerceIn(-1f, 10f)
        val lineY = height + distance.dpToPx()
        val startX = lineStart + indentWidth
        val endX = lineEnd
        when (underlineMode) {
            1 -> canvas.drawLine(startX, lineY, endX, lineY, paint)
            2 -> drawDashedLine(canvas, paint, startX, lineY, endX, underlineWidth)
            3 -> drawWavyLine(canvas, paint, startX, lineY, endX, underlineWidth)
            4 -> drawDottedLine(canvas, paint, startX, lineY, endX, underlineWidth)
        }
        PaintPool.recycle(paint)
    }

    /**
     * 绘制虚线下划线，每段8dp线段+5dp间隔
     */
    private fun drawDashedLine(canvas: Canvas, paint: Paint, startX: Float, y: Float, endX: Float, underlineWidth: Float) {
        paint.strokeWidth = underlineWidth.dpToPx()
        val dashLen = 8.dpToPx().toFloat()
        val gapLen = 5.dpToPx().toFloat()
        var x = startX
        while (x < endX) {
            val x2 = (x + dashLen).coerceAtMost(endX)
            canvas.drawLine(x, y, x2, y, paint)
            x += dashLen + gapLen
        }
    }

    /**
     * 绘制点线下划线，2dp圆点+4dp间隔
     */
    private fun drawDottedLine(canvas: Canvas, paint: Paint, startX: Float, y: Float, endX: Float, underlineWidth: Float) {
        paint.strokeWidth = underlineWidth.dpToPx()
        val dotSize = 2.dpToPx().toFloat()
        val gapLen = 4.dpToPx().toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        var x = startX
        while (x < endX) {
            val x2 = (x + dotSize).coerceAtMost(endX)
            canvas.drawLine(x, y, x2, y, paint)
            x += dotSize + gapLen
        }
    }

    /**
     * 绘制波浪线下划线，使用贝塞尔曲线实现
     */
    private fun drawWavyLine(canvas: Canvas, paint: Paint, startX: Float, y: Float, endX: Float, underlineWidth: Float) {
        paint.strokeWidth = underlineWidth.dpToPx()
        val path = Path()
        val waveAmplitude = 3.dpToPx().toFloat()
        val waveLength = 12.dpToPx().toFloat()
        path.moveTo(startX, y)
        var currentX = startX
        while (currentX < endX) {
            val nextX = (currentX + waveLength).coerceAtMost(endX)
            val midX = (currentX + nextX) / 2
            path.quadTo(midX, y - waveAmplitude, nextX, y)
            currentX = nextX
            if (currentX < endX) {
                val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                val midX2 = (currentX + nextX2) / 2
                path.quadTo(midX2, y + waveAmplitude, nextX2, y)
                currentX = nextX2
            }
        }
        canvas.drawPath(path, paint)
    }

    /**
     * 判断是否满足快速绘制条件
     */
    fun checkFastDraw(): Boolean {
        if (!AppConfig.optimizeRender || exceed || !onlyTextColumn || textPage.isMsgPage) {
            return false
        }
        if (wordSpacing != 0f && (!atLeastApi26 || !wordSpacingWorking)) {
            return false
        }
        if (searchResultColumnCount != 0) {
            return false
        }
        return columns.none {
            it is TextBaseColumn && (it.textColor != null || it.underlineMode != 0 || it.bgImage.isNotEmpty())
        }
    }

    private fun drawStyledBackgrounds(canvas: Canvas) {
        if (isImage || columns.isEmpty()) return
        if (columns.none { (it as? TextBaseColumn)?.bgImage?.isNotEmpty() == true }) return
        var rangeStart = 0f
        var rangeEnd = 0f
        var currentBgImage = ""
        var currentBgImageFit = 0
        var currentBgImageScale = 1f
        var active = false
        columns.forEachIndexed { index, column ->
            val textColumn = column as? TextBaseColumn
            val bgImage = textColumn?.bgImage ?: ""
            val bgImageFit = textColumn?.bgImageFit ?: 0
            val bgImageScale = textColumn?.bgImageScale ?: 1f
            when {
                bgImage.isEmpty() && active -> {
                    drawBgImageSegment(canvas, rangeStart, rangeEnd, currentBgImage, currentBgImageFit, currentBgImageScale)
                    active = false
                }
                bgImage.isNotEmpty() && !active -> {
                    rangeStart = textColumn!!.start
                    rangeEnd = textColumn.end
                    currentBgImage = bgImage
                    currentBgImageFit = bgImageFit
                    currentBgImageScale = bgImageScale
                    active = true
                }
                bgImage.isNotEmpty() && bgImage == currentBgImage && bgImageFit == currentBgImageFit && bgImageScale == currentBgImageScale -> {
                    rangeEnd = textColumn!!.end
                }
                bgImage.isNotEmpty() -> {
                    drawBgImageSegment(canvas, rangeStart, rangeEnd, currentBgImage, currentBgImageFit, currentBgImageScale)
                    rangeStart = textColumn!!.start
                    rangeEnd = textColumn.end
                    currentBgImage = bgImage
                    currentBgImageFit = bgImageFit
                    currentBgImageScale = bgImageScale
                }
            }
            if (active && index == columns.lastIndex) {
                drawBgImageSegment(canvas, rangeStart, rangeEnd, currentBgImage, currentBgImageFit, currentBgImageScale)
            }
        }
    }

    /**
     * 绘制高亮规则匹配文本的下划线段
     */
    private fun drawStyledUnderlines(canvas: Canvas) {
        if (isImage || columns.isEmpty()) return
        if (columns.none { (it as? TextBaseColumn)?.underlineMode?.let { m -> m != 0 } == true }) return
        var rangeStart = 0f
        var rangeEnd = 0f
        var mode = 0
        var color = 0
        var width = 1f
        var svgPath = ""
        var active = false
        columns.forEachIndexed { index, column ->
            val textColumn = column as? TextBaseColumn
            val currentMode = textColumn?.underlineMode ?: 0
            val currentColor = textColumn?.underlineColor
                ?: textColumn?.textColor
                ?: ReadBookConfig.textColor
            val currentWidth = textColumn?.underlineWidth ?: 1f
            val currentSvgPath = textColumn?.underlineSvgPath ?: ""
            val shouldContinue = active && currentMode == mode && currentColor == color && currentWidth == width && currentSvgPath == svgPath
            when {
                currentMode == 0 && active -> {
                    drawUnderlineSegment(canvas, rangeStart, rangeEnd, mode, color, width, svgPath)
                    active = false
                }
                currentMode != 0 && !active -> {
                    rangeStart = textColumn!!.start
                    rangeEnd = textColumn.end
                    mode = currentMode
                    color = currentColor
                    width = currentWidth
                    svgPath = currentSvgPath
                    active = true
                }
                currentMode != 0 && shouldContinue -> {
                    rangeEnd = textColumn!!.end
                }
                currentMode != 0 -> {
                    drawUnderlineSegment(canvas, rangeStart, rangeEnd, mode, color, width, svgPath)
                    rangeStart = textColumn!!.start
                    rangeEnd = textColumn.end
                    mode = currentMode
                    color = currentColor
                    width = currentWidth
                    svgPath = currentSvgPath
                }
            }
            if (active && index == columns.lastIndex) {
                drawUnderlineSegment(canvas, rangeStart, rangeEnd, mode, color, width, svgPath)
            }
        }
    }

    /**
     * 绘制当前搜索结果匹配区域的高亮背景
     */
    private fun drawCurrentSearchResultBackgrounds(canvas: Canvas) {
        if (columns.isEmpty() || searchResultColumnCount == 0) return
        var startX = 0f
        var endX = 0f
        var active = false
        columns.forEachIndexed { index, column ->
            val textColumn = column as? TextBaseColumn
            val current = textColumn?.isCurrentSearchResult == true
            when {
                current && !active -> {
                    startX = textColumn.start
                    endX = textColumn.end
                    active = true
                }
                current -> {
                    endX = textColumn.end
                }
                active -> {
                    drawCurrentSearchRange(canvas, startX, endX)
                    active = false
                }
            }
            if (active && index == columns.lastIndex) {
                drawCurrentSearchRange(canvas, startX, endX)
            }
        }
    }

    /**
     * 绘制搜索结果匹配范围的圆角背景
     */
    private fun drawCurrentSearchRange(canvas: Canvas, startX: Float, endX: Float) {
        val paint = PaintPool.obtain()
        paint.set(ChapterProvider.contentPaint)
        paint.color = (0x33 shl 24) or (ReadBookConfig.textAccentColor and 0x00FFFFFF)
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(
            startX,
            searchPadding,
            endX,
            height - searchPadding,
            searchRadius,
            searchRadius,
            paint
        )
        PaintPool.recycle(paint)
    }

    /**
     * 绘制单段下划线，用于高亮规则匹配区域，支持实线/虚线/波浪线/标题强调条
     */
    private fun drawUnderlineSegment(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        underlineMode: Int,
        underlineColor: Int,
        underlineWidth: Float = 1f,
        svgPathStr: String = "",
    ) {
        val paint = PaintPool.obtain()
        paint.set(ChapterProvider.contentPaint)
        paint.color = underlineColor
        paint.strokeWidth = underlineWidth.dpToPx()
        paint.style = android.graphics.Paint.Style.STROKE
        val distance = (ChapterProvider.lineSpacingExtra * 10 - 11).coerceIn(-1f, 10f)
        val lineY = height + distance.dpToPx()
        when (underlineMode) {
            1 -> canvas.drawLine(startX, lineY, endX, lineY, paint)
            2 -> drawDashedLine(canvas, paint, startX, lineY, endX, underlineWidth)
            3 -> drawWavyLine(canvas, paint, startX, lineY, endX, underlineWidth)
            4 -> {
                val line2Y = lineY + doubleLineGap + underlineWidth.dpToPx()
                canvas.drawLine(startX, lineY, endX, lineY, paint)
                canvas.drawLine(startX, line2Y, endX, line2Y, paint)
            }
            5 -> {
                if (svgPathStr.isNotBlank()) {
                    drawSvgPath(canvas, startX, endX, lineY, svgPathStr, paint)
                }
            }
        }
        PaintPool.recycle(paint)
    }
    
    private fun drawSvgPath(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        lineY: Float,
        svgPathStr: String,
        paint: Paint
    ) {
        val baseWidth = 100f
        val baseY = 50f
        val path = io.legado.app.ui.book.read.config.SvgPathParser.parse(svgPathStr) ?: return
        
        val width = endX - startX
        val scaleX = width / baseWidth
        val scaleY = 1f
        val translateX = startX
        val translateY = lineY - baseY
        
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleX, scaleY)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawBgImageSegment(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        bgImage: String,
        bgImageFit: Int,
        bgImageScale: Float,
    ) {
        val bitmap = getBgBitmap(bgImage) ?: return
        val paint = PaintPool.obtain()
        paint.style = android.graphics.Paint.Style.FILL
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        val top = bgPaddingTop
        val bottom = height - bgPaddingBottom
        val rectWidth = endX - startX
        val rectHeight = bottom - top
        val scale = bgImageScale.coerceIn(0.1f, 5f)
        when (bgImageFit) {
            1 -> {
                val sw = rectWidth * scale
                val sh = rectHeight * scale
                val dx = startX + (rectWidth - sw) / 2f
                val dy = top + (rectHeight - sh) / 2f
                canvas.save()
                canvas.clipRect(startX, top, endX, bottom)
                canvas.drawBitmap(bitmap, null, android.graphics.RectF(dx, dy, dx + sw, dy + sh), paint)
                canvas.restore()
            }
            2 -> {
                val bw = bitmap.width.toFloat()
                val bh = bitmap.height.toFloat()
                val fitScale = (rectWidth / bw).coerceAtLeast(rectHeight / bh) * scale
                val scaledW = bw * fitScale
                val scaledH = bh * fitScale
                val dx = startX + (rectWidth - scaledW) / 2f
                val dy = top + (rectHeight - scaledH) / 2f
                canvas.save()
                canvas.clipRect(startX, top, endX, bottom)
                canvas.drawBitmap(bitmap, null, android.graphics.RectF(dx, dy, dx + scaledW, dy + scaledH), paint)
                canvas.restore()
            }
            else -> {
                val tileBitmap = if (scale != 1f) {
                    val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    getScaledBitmap("${bgImage}_s${scale}", bitmap, sw, sh)
                } else {
                    bitmap
                }
                val shader = BitmapShader(tileBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val matrix = android.graphics.Matrix()
                matrix.setTranslate(startX, top)
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                canvas.drawRect(startX, top, endX, bottom, paint)
                paint.shader = null
            }
        }
        PaintPool.recycle(paint)
    }

    /**
     * 触发行重绘，同时刷新页面缓存
     */
    fun invalidate() {
        invalidateSelf()
        textPage.invalidate()
    }

    /**
     * 仅触发行自身缓存失效
     */
    fun invalidateSelf() {
        canvasRecorder.invalidate()
    }

    /**
     * 释放 Canvas 录制器资源
     */
    fun recycleRecorder() {
        canvasRecorder.recycle()
    }

    /**
     * 静态常量和兼容性检测
     */
    @SuppressLint("NewApi")
    companion object {
        val emptyTextLine = TextLine()
        private val atLeastApi26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val atLeastApi28 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        private val atLeastApi35 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        private val bgPaddingTop = 1.dpToPx().toFloat()
        private val bgPaddingBottom = 1.dpToPx().toFloat()
        private val waveAmplitude = 3.dpToPx().toFloat()
        private val waveLength = 12.dpToPx().toFloat()
        private val doubleLineGap = 3.dpToPx().toFloat()
        private val searchRadius = 5.dpToPx().toFloat()
        private val searchPadding = 1.dpToPx().toFloat()
        private val einkUnderlineWidth = 1.dpToPx().toFloat()
        private val bgBitmapCache = android.util.LruCache<String, Bitmap>(16 * 1024 * 1024)
        private val bgScaledBitmapCache = android.util.LruCache<String, Bitmap>(8 * 1024 * 1024)
        private val bgSampleWidth by lazy {
            appCtx.resources.displayMetrics.widthPixels
        }
        private val bgSampleHeight by lazy {
            appCtx.resources.displayMetrics.heightPixels
        }

        fun getBgBitmap(path: String): Bitmap? {
            if (path.isBlank()) return null
            bgBitmapCache.get(path)?.let { return it }
            val bitmap = loadBgBitmap(path) ?: return null
            bgBitmapCache.put(path, bitmap)
            return bitmap
        }

        private fun getScaledBitmap(path: String, source: Bitmap, width: Int, height: Int): Bitmap {
            if (width <= 0 || height <= 0) return source
            val key = "${path}_${width}_${height}"
            bgScaledBitmapCache.get(key)?.let { return it }
            val scaled = Bitmap.createScaledBitmap(source, width, height, true)
            bgScaledBitmapCache.put(key, scaled)
            return scaled
        }

        private fun loadBgBitmap(path: String): Bitmap? {
            return try {
                val ctx = appCtx
                if (path.startsWith("assets://")) {
                    val assetPath = path.removePrefix("assets://")
                    ctx.assets.open(assetPath).use { input ->
                        decodeSampledBitmap(input)
                    }
                } else if (path.startsWith("content://")) {
                    val uri = android.net.Uri.parse(path)
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        decodeSampledBitmap(input)
                    }
                } else {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        decodeSampledBitmapFile(path)
                    } else {
                        val assetPath = if (path.startsWith("bg/")) path else "bg/$path"
                        kotlin.runCatching {
                            ctx.assets.open(assetPath).use { input ->
                                decodeSampledBitmap(input)
                            }
                        }.getOrNull()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun decodeSampledBitmap(input: java.io.InputStream): Bitmap? {
            val buffered = if (input.markSupported()) input else java.io.BufferedInputStream(input)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            buffered.mark(buffered.available())
            BitmapFactory.decodeStream(buffered, null, options)
            options.inSampleSize = calculateInSampleSize(options, bgSampleWidth, bgSampleHeight)
            options.inJustDecodeBounds = false
            buffered.reset()
            return BitmapFactory.decodeStream(buffered, null, options)
        }

        private fun decodeSampledBitmapFile(path: String): Bitmap? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, bgSampleWidth, bgSampleHeight)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(path, options)
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height, width) = options.outHeight to options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        fun clearBgBitmapCache() {
            bgBitmapCache.evictAll()
            bgScaledBitmapCache.evictAll()
        }

        fun copyBgImageToInternal(context: android.content.Context, sourcePath: String): String? {
            if (sourcePath.startsWith("assets://")) return sourcePath
            return try {
                val dir = java.io.File(context.filesDir, "bg_images")
                if (!dir.exists()) dir.mkdirs()
                val hash = Integer.toHexString(sourcePath.hashCode()).replace("-", "n")
                val ext = sourcePath.substringAfterLast('.', "png")
                val fileName = "bg_$hash.$ext"
                val destFile = java.io.File(dir, fileName)
                if (destFile.exists()) return destFile.absolutePath
                if (sourcePath.startsWith("content://")) {
                    val uri = android.net.Uri.parse(sourcePath)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    val srcFile = java.io.File(sourcePath)
                    if (srcFile.exists()) {
                        srcFile.inputStream().use { input ->
                            java.io.FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        return sourcePath
                    }
                }
                destFile.absolutePath
            } catch (e: Exception) {
                sourcePath
            }
        }

        fun cleanupUnusedBgImages(context: android.content.Context, usedPaths: Set<String>) {
            val dir = java.io.File(context.filesDir, "bg_images")
            if (!dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (file.absolutePath !in usedPaths) {
                    bgBitmapCache.remove(file.absolutePath)
                    file.delete()
                }
            }
        }
        private val wordSpacingWorking by lazy {
            // issue 3785 3846
            val paint = PaintPool.obtain()
            val text = "一二 三"
            val width1 = paint.measureText(text)
            try {
                paint.wordSpacing = 10f
                val width2 = paint.measureText(text)
                width2 - width1 == 10f
            } catch (e: NoSuchMethodError) {
                false
            } finally {
                PaintPool.recycle(paint)
            }
        }
    }

}
