package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.canvasrecorder.recordIfNeededThenDraw
import io.legado.app.utils.dpToPx

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    private val batteryTypeface by lazy {
        Typeface.createFromAsset(context.assets, "font/number.ttf")
    }
    private val batteryPaint = Paint()
    private val outFrame = Rect()
    private val polar = Rect()
    private val canvasRecorder = CanvasRecorderFactory.create()
    var isBattery = false
        set(value) {
            field = value
            if (value && !isInEditMode) {
                super.setTypeface(batteryTypeface)
                postInvalidate()
            }
        }
    private var battery: Int = 0

    init {
        setPadding(4.dpToPx(), 3.dpToPx(), 6.dpToPx(), 3.dpToPx())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isFallbackLineSpacing = false
        }
        batteryPaint.strokeWidth = 1f.dpToPx()
        batteryPaint.isAntiAlias = true
        batteryPaint.color = paint.color
    }

    override fun setTypeface(tf: Typeface?) {
        if (!isBattery) {
            super.setTypeface(tf)
        }
    }

    fun setColor(@ColorInt color: Int) {
        setTextColor(color)
        batteryPaint.color = color
        invalidate()
    }

    @SuppressLint("SetTextI18n")
    fun setBattery(battery: Int, text: String? = null) {
        this.battery = battery
        if (text.isNullOrEmpty()) {
            setText(battery.toString())
        } else {
            setText("$text  $battery")
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        canvasRecorder.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (AppConfig.optimizeRender) {
            canvasRecorder.recordIfNeededThenDraw(canvas, width, height) {
                super.onDraw(this)
                drawBattery(this)
            }
        } else {
            super.onDraw(canvas)
            drawBattery(canvas)
        }
    }

    private fun drawBattery(canvas: Canvas) {
        if (!isBattery) return
        layout.getLineBounds(0, outFrame)
        val batteryStart = layout
            .getPrimaryHorizontal(text.length - battery.toString().length)
            .toInt() + 2.dpToPx()
        val batteryEnd = batteryStart +
                StaticLayout.getDesiredWidth(battery.toString(), paint).toInt() + 4.dpToPx()
        
        val top = 3.dpToPx()
        val bottom = height - 3.dpToPx()
        val bodyHeight = bottom - top
        val cornerRadius = 2.dpToPx().toFloat()
        
        outFrame.set(
            batteryStart,
            top,
            batteryEnd,
            bottom
        )
        
        val polarHeight = bodyHeight * 0.4f
        val polarTop = top + (bodyHeight - polarHeight) / 2
        polar.set(
            batteryEnd,
            polarTop.toInt(),
            batteryEnd + 2.dpToPx(),
            (polarTop + polarHeight).toInt()
        )
        
        batteryPaint.style = Paint.Style.FILL
        val polarRadius = 1.dpToPx().toFloat()
        canvas.drawRoundRect(
            polar.left.toFloat(),
            polar.top.toFloat(),
            polar.right.toFloat(),
            polar.bottom.toFloat(),
            polarRadius,
            polarRadius,
            batteryPaint
        )
        
        batteryPaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(
            outFrame.left.toFloat(),
            outFrame.top.toFloat(),
            outFrame.right.toFloat(),
            outFrame.bottom.toFloat(),
            cornerRadius,
            cornerRadius,
            batteryPaint
        )
        
        if (battery > 0) {
            val padding = 2.dpToPx()
            val fillWidth = (outFrame.width() - padding * 2) * (battery.coerceIn(0, 100) / 100f)
            if (fillWidth > 0) {
                batteryPaint.style = Paint.Style.FILL
                val fillLeft = outFrame.left + padding
                val fillTop = outFrame.top + padding
                val fillRight = fillLeft + fillWidth
                val fillBottom = outFrame.bottom - padding
                val fillRadius = 1.dpToPx().toFloat()
                canvas.drawRoundRect(
                    fillLeft.toFloat(),
                    fillTop.toFloat(),
                    fillRight.toFloat(),
                    fillBottom.toFloat(),
                    fillRadius,
                    fillRadius,
                    batteryPaint
                )
            }
        }
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun invalidate() {
        super.invalidate()
        canvasRecorder?.invalidate()
    }

}