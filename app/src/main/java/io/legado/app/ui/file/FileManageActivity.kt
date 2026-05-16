package io.legado.app.ui.file

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 文件管理 Activity
 * 
 * 职责：
 * - 作为 Compose UI 的容器
 * - 初始化主题和系统栏
 * - 加载背景图片
 * - 配置 Material3 颜色方案
 */
class FileManageActivity : AppCompatActivity() {

    /** 背景图片 Drawable */
    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        setContent {
            FileManageContent(
                bgDrawable = bgDrawable,
                onBackClick = { finish() }
            )
        }
    }

    /**
     * 加载背景图片
     * 根据屏幕尺寸获取合适的背景图
     */
    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 初始化主题
     * 根据配置选择深色或浅色主题
     */
    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }
            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    /**
     * 设置系统栏（状态栏和导航栏）
     */
    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

/**
 * 文件管理内容 Composable
 * 
 * 职责：
 * - 从 ThemeStore 获取颜色配置
 * - 构建 Material3 颜色方案
 * - 包装背景和主界面
 */
@Composable
fun FileManageContent(
    bgDrawable: Drawable?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // 从 ThemeStore 获取颜色
    val isNightTheme = AppConfig.isNightTheme
    val primaryColor = ThemeStore.primaryColor(context)
    val accentColor = ThemeStore.accentColor(context)
    val bgColor = ThemeStore.backgroundColor(context)
    val textPrimaryColor = ThemeStore.textColorPrimary(context)
    val textSecondaryColor = ThemeStore.textColorSecondary(context)

    // 计算是否为浅色主题
    val isLight = !isNightTheme && ColorUtils.isColorLight(bgColor)
    
    // 转换为 Compose Color
    val background = Color(bgColor)
    val primary = Color(accentColor)
    val secondary = Color(primaryColor)
    val onBackground = Color(textPrimaryColor)
    val onBackgroundVariant = Color(textSecondaryColor)

    // 计算派生颜色
    val surface = lerp(background, if (isLight) Color.White else Color.Black, if (isLight) 0.04f else 0.10f)
    val surfaceVariant = lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    val outline = lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    val onSurfaceVariant = lerp(onBackground, if (isLight) Color.Black else Color.White, if (isLight) 0.2f else 0.2f)

    // 构建 Material3 颜色方案
    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = secondary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            secondaryContainer = surfaceVariant,
            tertiaryContainer = surfaceVariant,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.75f),
            onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
            onSecondary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
            onBackground = onBackground,
            onSurface = onBackground,
            onSurfaceVariant = onSurfaceVariant,
            error = Color(0xFFE53935),
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = secondary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            secondaryContainer = surfaceVariant,
            tertiaryContainer = surfaceVariant,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.8f),
            onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
            onSecondary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
            onBackground = onBackground,
            onSurface = onBackground,
            onSurfaceVariant = onSurfaceVariant,
            error = Color(0xFFFF5252),
            onError = Color.Black
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        FileManageBoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            FileManageScreen(onBackClick = onBackClick)
        }
    }
}

/**
 * 带背景的容器
 * 
 * 职责：
 * - 显示背景图片（如果有）
 * - 添加半透明遮罩层
 * - 包装实际内容
 */
@Composable
fun FileManageBoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            // 计算遮罩透明度：浅色背景用较低透明度，深色背景用较高透明度
            val overlayAlpha = if (bgColor.luminance() > 0.5f) 0.22f else 0.40f
            // 背景图片
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // 半透明遮罩层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = overlayAlpha))
            )
        } else {
            // 无背景图片时直接使用背景色
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        // 实际内容
        content()
    }
}
