package io.legado.app.model

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.legado.app.data.entities.LayoutMode
import io.legado.app.data.entities.MaterialMode
import io.legado.app.data.entities.NavigationBarConfig
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.lib.theme.view.ThemeBottomNavigationVIew
import io.legado.app.utils.dpToPx

/**
 * 底栏液态玻璃效果应用器
 *
 * 布局结构（XML 固定）：
 *   FrameLayout
 *     ├── LinearLayout（ViewPager match_parent 全屏）
 *     └── BottomNavigationView（layout_gravity=bottom）
 *
 * FIXED 模式：
 *   - ViewPager clipToPadding=true, bottomPadding=底栏高度
 *   - 底栏保持原始背景，无 margin
 *   - 无 glass/overlay
 *
 * FLOATING 模式：
 *   - ViewPager clipToPadding=false, bottomPadding=底栏高度+margin*2
 *   - 底栏背景透明(setBackgroundColor) + elevation=0 + 16dp margin
 *   - LiquidGlassView + overlay 叠加在底栏后面
 *
 * 不做任何运行时 View 移动，只修改属性。
 */
object NavigationBarEffectApplier {

    private const val TAG_OVERLAY = "navigation_bar_overlay"
    private const val TAG_GLASS_VIEW = "navigation_bar_glass_view"

    // ---- 原始状态 ----
    private var originalNavBgColor: Int = 0
    private var originalNavBackgroundTint: android.content.res.ColorStateList? = null
    private var originalNavElevation: Float = 0f
    private var originalNavLayoutParams: FrameLayout.LayoutParams? = null
    private var originalViewPagerPadding: IntArray? = null
    private var originalViewPagerClipToPadding: Boolean = true
    private var currentMode: LayoutMode? = null

    fun applyEffect(config: NavigationBarConfig, binding: ActivityMainBinding) {
        val navView = binding.bottomNavigationView
        val viewPager = binding.viewPagerMain
        val activity = navView.context as? Activity

        // 首次调用：保存原始状态 + 设置初始 padding
        saveOriginalState(binding)
        ensureInitialPadding(navView, viewPager)

        // 布局模式决定行为：
        // - FIXED：底栏保持原始不透明样式，铺满底部
        // - FLOATING：底栏悬浮+透明，无论 materialMode（SOLID/GLASS/FROSTED）都让底栏下方可见
        val isFloating = config.layoutMode == LayoutMode.FLOATING
        if (isFloating) {
            applyFloatingMode(binding, config)
            // FLOATING 模式：系统导航栏设为透明，让底栏内容延伸到系统导航栏区域
            activity?.let { makeSystemNavBarTransparent(it) }
        } else {
            applyFixedMode(binding)
            // FIXED 模式：恢复系统导航栏为不透明
            activity?.let { restoreSystemNavBarColor(it) }
        }

        currentMode = config.layoutMode
    }

    /**
     * FIXED / SOLID 模式：底栏保持原始样子
     */
    private fun applyFixedMode(binding: ActivityMainBinding) {
        val rootView = binding.root as? ViewGroup ?: return
        val navView = binding.bottomNavigationView
        val viewPager = binding.viewPagerMain

        // 移除 glass/overlay
        // 先将 LiquidGlassView 设为 GONE 并递归清除所有子 View 的 Handler 回调，
        // 防止 removeView 后内部 post() 投递的 Runnable 在 glass/impl 被
        // 置 null 后执行导致 NPE（LiquidGlassView 内部的 LiquidGlass 子 View
        // 通过 host.post() 投递的回调不会被 removeView 自动取消）
        rootView.findViewWithTag<LiquidGlassView>(TAG_GLASS_VIEW)?.let { glassView ->
            glassView.visibility = View.GONE
            removeAllCallbacks(glassView)
            rootView.removeView(glassView)
        }
        rootView.findViewWithTag<View>(TAG_OVERLAY)?.let { rootView.removeView(it) }

        // 恢复 ThemeBottomNavigationVIew 默认样式
        if (navView is ThemeBottomNavigationVIew) {
            navView.resetToDefaultStyle()
        } else {
            navView.setBackgroundColor(originalNavBgColor)
            if (originalNavBackgroundTint != null) {
                navView.backgroundTintList = originalNavBackgroundTint
            }
            navView.elevation = originalNavElevation
            navView.outlineProvider = ViewOutlineProvider.BOUNDS
        }

        // 恢复底栏 LayoutParams（强制 margin=0，避免原始保存的是带 margin 的悬浮参数）
        originalNavLayoutParams?.let { lp ->
            val fixedLp = FrameLayout.LayoutParams(lp).apply {
                setMargins(0, 0, 0, 0)
                gravity = lp.gravity
            }
            navView.layoutParams = fixedLp
        }

        // ViewPager: clipToPadding=true, bottomPadding=底栏高度
        viewPager.clipToPadding = true
        navView.post {
            val navHeight = navView.height
            if (navHeight > 0) {
                viewPager.setPadding(
                    originalViewPagerPadding!![0],
                    originalViewPagerPadding!![1],
                    originalViewPagerPadding!![2],
                    navHeight
                )
            }
        }
    }

    /**
     * FLOATING 模式：底栏悬浮 + 玻璃效果
     */
    private fun applyFloatingMode(binding: ActivityMainBinding, config: NavigationBarConfig) {
        val rootView = binding.root as? ViewGroup ?: return
        val navView = binding.bottomNavigationView
        val viewPager = binding.viewPagerMain

        // 底栏背景完全透明 + 去掉阴影
        if (navView is ThemeBottomNavigationVIew) {
            navView.setGlassTransparent()
        } else {
            navView.backgroundTintList = null
            navView.setBackgroundColor(Color.TRANSPARENT)
            navView.elevation = 0f
            navView.outlineProvider = null
        }

        // 底栏加 margin
        val margin = 16.dpToPx()
        val lp = navView.layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            lp.setMargins(margin, 0, margin, margin)
            lp.gravity = Gravity.BOTTOM
            navView.layoutParams = lp
        }

        // ViewPager: 不设置 paddingBottom
        // Fragment 内容（match_parent）填满 ViewPager 整个高度，延伸到屏幕底部。
        // 底栏是悬浮+半透明的，会覆盖在 Fragment 内容之上，
        // Fragment 内的滚动控件（如 RecyclerView）应自行处理 bottom padding 避免内容被底栏遮挡。
        viewPager.clipToPadding = false
        navView.post {
            val navHeight = navView.height
            if (navHeight > 0) {
                viewPager.setPadding(
                    originalViewPagerPadding!![0],
                    originalViewPagerPadding!![1],
                    originalViewPagerPadding!![2],
                    0
                )

                // 更新 glass/overlay 高度
                rootView.findViewWithTag<LiquidGlassView>(TAG_GLASS_VIEW)?.let { glass ->
                    glass.layoutParams = (glass.layoutParams as? FrameLayout.LayoutParams)?.apply {
                        height = navHeight
                    }
                }
                rootView.findViewWithTag<View>(TAG_OVERLAY)?.let { overlay ->
                    overlay.layoutParams = (overlay.layoutParams as? FrameLayout.LayoutParams)?.apply {
                        height = navHeight
                    }
                }
            }
        }

        // 添加/更新 glass 和 overlay
        val useFallback = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

        var glassView = rootView.findViewWithTag<LiquidGlassView>(TAG_GLASS_VIEW)
        var overlay = rootView.findViewWithTag<View>(TAG_OVERLAY)

        if (useFallback) {
            // 降级：移除 glass，只用 overlay
            glassView?.let {
                it.visibility = View.GONE
                removeAllCallbacks(it)
                rootView.removeView(it)
            }
            glassView = null

            if (overlay == null) {
                overlay = createOverlayView(binding.root.context, config)
                rootView.addView(overlay, rootView.indexOfChild(navView))
            } else {
                overlay.background = createOverlayDrawable(config)
            }
            (overlay.layoutParams as? FrameLayout.LayoutParams)?.setMargins(margin, 0, margin, margin)
        } else {
            // 正常：glass + overlay
            if (glassView == null) {
                glassView = createGlassView(binding, config)
                rootView.addView(glassView, rootView.indexOfChild(navView))
            } else {
                setupGlassView(glassView, binding, config)
            }
            (glassView.layoutParams as? FrameLayout.LayoutParams)?.setMargins(margin, 0, margin, margin)

            if (overlay == null) {
                overlay = createOverlayView(binding.root.context, config)
                rootView.addView(overlay, rootView.indexOfChild(navView))
            } else {
                overlay.background = createOverlayDrawable(config)
            }
            (overlay.layoutParams as? FrameLayout.LayoutParams)?.setMargins(margin, 0, margin, margin)
        }
    }

    /**
     * 首次调用时设置初始 padding（让 ViewPager 内容不被底栏遮挡）
     */
    private fun ensureInitialPadding(navView: View, viewPager: android.view.ViewGroup) {
        if (currentMode != null) return
        navView.post {
            val navHeight = navView.height
            if (navHeight > 0 && viewPager.paddingBottom < navHeight) {
                viewPager.setPadding(
                    viewPager.paddingLeft,
                    viewPager.paddingTop,
                    viewPager.paddingRight,
                    navHeight
                )
                viewPager.clipToPadding = true
            }
        }
    }

    private fun saveOriginalState(binding: ActivityMainBinding) {
        val navView = binding.bottomNavigationView
        val viewPager = binding.viewPagerMain

        if (originalNavLayoutParams == null) {
            // ThemeBottomNavigationVIew 用 setBackgroundColor 设置背景，
            // 所以 background 是 ColorDrawable，可以提取颜色
            val bg = navView.background
            if (bg is android.graphics.drawable.ColorDrawable) {
                originalNavBgColor = bg.color
            }
            // Material3 BottomNavigationView 还有 backgroundTint（surface color），
            // 必须同时保存，否则恢复时颜色不对
            originalNavBackgroundTint = navView.backgroundTintList
            originalNavElevation = navView.elevation
            originalNavLayoutParams = navView.layoutParams as? FrameLayout.LayoutParams
        }
        if (originalViewPagerPadding == null) {
            originalViewPagerPadding = intArrayOf(
                viewPager.paddingLeft,
                viewPager.paddingTop,
                viewPager.paddingRight,
                viewPager.paddingBottom
            )
            originalViewPagerClipToPadding = viewPager.clipToPadding
        }
    }

    // ---- View 创建 ----

    private fun createOverlayView(context: android.content.Context, config: NavigationBarConfig): View {
        return View(context).apply {
            tag = TAG_OVERLAY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            isClickable = false
            isFocusable = false
            background = createOverlayDrawable(config)
        }
    }

    private fun createGlassView(binding: ActivityMainBinding, config: NavigationBarConfig): LiquidGlassView {
        return LiquidGlassView(binding.root.context).apply {
            tag = TAG_GLASS_VIEW
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            isClickable = false
            isFocusable = false
            setupGlassView(this, binding, config)
        }
    }

    // ---- Overlay Drawable ----

    private fun createOverlayDrawable(config: NavigationBarConfig): GradientDrawable {
        val cornerRadius = 24f.dpToPx().toFloat()

        val (topAlpha, bottomAlpha) = when (config.materialMode) {
            MaterialMode.GLASS -> {
                val opacityFactor = config.opacity / 100f
                val top = (0.35f * opacityFactor * 255).toInt().coerceIn(0, 255)
                val bottom = (0.12f * opacityFactor * 255).toInt().coerceIn(0, 255)
                Pair(top, bottom)
            }
            MaterialMode.FROSTED -> {
                val opacityFactor = config.opacity / 100f
                val top = (0.70f * opacityFactor * 255).toInt().coerceIn(0, 255)
                val bottom = (0.50f * opacityFactor * 255).toInt().coerceIn(0, 255)
                Pair(top, bottom)
            }
            else -> Pair(0, 0)
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius

            colors = intArrayOf(
                (topAlpha shl 24) or 0xFFFFFF,
                (bottomAlpha shl 24) or 0xFFFFFF
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM

            val borderAlpha = (config.borderOpacity / 100f * 255).toInt()
            val borderColorWithAlpha = (borderAlpha shl 24) or (config.borderColor and 0x00FFFFFF)
            setStroke(2.dpToPx(), borderColorWithAlpha)
        }
    }

    // ---- LiquidGlassView 配置 ----

    /**
     * 配置 LiquidGlassView
     *
     * 绑定 LinearLayout（ViewPager 的父容器）作为采样源，
     * 因为 LiquidGlassView.bind() 要求 source 是同一父容器的兄弟 View。
     * ViewPager 在 LinearLayout 内，不是 LiquidGlassView 的兄弟，
     * 而 LinearLayout 是 LiquidGlassView 的兄弟（都是 FrameLayout 的子元素）。
     */
    private fun setupGlassView(
        glassView: LiquidGlassView,
        binding: ActivityMainBinding,
        config: NavigationBarConfig
    ) {
        try {
            glassView.visibility = View.VISIBLE

            // 绑定 LinearLayout（兄弟 View）而非 ViewPager（非兄弟）
            val linearLayout = binding.viewPagerMain.parent as? ViewGroup
            if (linearLayout != null) {
                glassView.bind(linearLayout)
            }

            glassView.setCornerRadius(24f.dpToPx().toFloat())

            when (config.materialMode) {
                MaterialMode.GLASS -> {
                    glassView.setRefractionHeight(30f.dpToPx().toFloat())
                    glassView.setRefractionOffset(90f.dpToPx().toFloat())
                    glassView.setBlurRadius(12f.dpToPx().toFloat())
                    glassView.setDispersion(0.5f)
                    glassView.setTintColorRed(1.0f)
                    glassView.setTintColorGreen(1.0f)
                    glassView.setTintColorBlue(1.0f)
                    glassView.setTintAlpha(0.05f)
                }
                MaterialMode.FROSTED -> {
                    glassView.setRefractionHeight(0f)
                    glassView.setRefractionOffset(0f)
                    glassView.setBlurRadius(40f.dpToPx().toFloat())
                    glassView.setDispersion(0f)
                    glassView.setTintColorRed(1.0f)
                    glassView.setTintColorGreen(1.0f)
                    glassView.setTintColorBlue(1.0f)
                    glassView.setTintAlpha(0.25f)
                }
                else -> {
                    glassView.setRefractionHeight(0f)
                    glassView.setBlurRadius(0f)
                    glassView.setTintAlpha(0f)
                }
            }

            glassView.setDraggableEnabled(false)
            glassView.setElasticEnabled(false)
            glassView.setTouchEffectEnabled(false)
            glassView.invalidate()
        } catch (e: Exception) {
            glassView.visibility = View.GONE
        }
    }

    // ---- 系统导航栏颜色控制 ----

    /**
     * 将系统导航栏设为透明
     *
     * BaseActivity.upNavigationBarColor() 会设置 window.navigationBarColor 为不透明颜色，
     * 这会导致浮动底栏下方出现一块不透明的系统导航栏区域。
     * 在 FLOATING 模式下需要将其设为透明，让内容延伸到系统导航栏区域。
     */
    private fun makeSystemNavBarTransparent(activity: Activity) {
        // 设置为完全透明
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 关闭系统手势条自动添加的对比度 scrim（Android 10+）
            activity.window.isNavigationBarContrastEnforced = false
        }
    }

    /**
     * 恢复系统导航栏为不透明
     *
     * FIXED 模式下恢复原始行为，由 MainActivity.upNavigationBarColor() 控制。
     */
    private fun restoreSystemNavBarColor(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = true
        }
        // 重新触发 MainActivity.upNavigationBarColor() 来设置正确的颜色
        if (activity is io.legado.app.ui.main.MainActivity) {
            activity.upNavigationBarColor()
        }
    }

    /**
     * 递归清除 View 及其所有子 View 的 Handler 待执行回调
     *
     * LiquidGlassView 内部包含 LiquidGlass 子 View，LiquidGlass 的构造函数和
     * updateParameters() 都通过 host.post() 投递 Runnable。当 LiquidGlassView
     * 被从视图树移除时，onDetachedFromWindow 会将 glass 置 null，但已投递的
     * Runnable 仍在消息队列中，执行时访问已为 null 的引用导致 NPE。
     * 通过在 removeView 前递归清除所有子 View 的回调来避免此问题。
     */
    private fun removeAllCallbacks(view: View) {
        view.removeCallbacks(null)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                removeAllCallbacks(view.getChildAt(i))
            }
        }
    }
}
