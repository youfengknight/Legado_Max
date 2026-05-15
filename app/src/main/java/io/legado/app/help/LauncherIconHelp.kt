package io.legado.app.help

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.welcome.*
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * Created by GKF on 2018/2/27.
 * 更换图标
 */
object LauncherIconHelp {
    private val packageManager: PackageManager = appCtx.packageManager
    private val componentNames = arrayListOf(
        ComponentName(appCtx, Launcher1::class.java.name),
        ComponentName(appCtx, Launcher2::class.java.name),
        ComponentName(appCtx, Launcher3::class.java.name),
        ComponentName(appCtx, Launcher4::class.java.name),
        ComponentName(appCtx, Launcher5::class.java.name),
        ComponentName(appCtx, Launcher6::class.java.name),
        ComponentName(appCtx, Launcher7::class.java.name),
        ComponentName(appCtx, Launcher8::class.java.name)
    )

    private val welcomeComponent = ComponentName(appCtx, WelcomeActivity::class.java.name)

    /**
     * 校验并修正图标设置
     * 用于处理升级安装后 SharedPreferences 中保存的值与实际图标不一致的问题
     */
    fun fixLauncherIconPref() {
        if (Build.VERSION.SDK_INT < 26) return
        
        val savedIcon = getPrefString(PreferKey.launcherIcon)
        
        val isWelcomeEnabled = packageManager.getComponentEnabledSetting(
            welcomeComponent
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        
        if (isWelcomeEnabled) {
            if (savedIcon != "ic_launcher") {
                putPrefString(PreferKey.launcherIcon, "ic_launcher")
            }
            return
        }
        
        var actualEnabledIcon: String? = null
        for (component in componentNames) {
            val isEnabled = packageManager.getComponentEnabledSetting(
                component
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (isEnabled) {
                actualEnabledIcon = component.className.substringAfterLast(".")
                break
            }
        }
        
        if (actualEnabledIcon != null && savedIcon != actualEnabledIcon) {
            putPrefString(PreferKey.launcherIcon, actualEnabledIcon)
        }
    }

    fun changeIcon(icon: String?) {
        if (icon.isNullOrEmpty()) return
        if (Build.VERSION.SDK_INT < 26) {
            appCtx.toastOnUi(R.string.change_icon_error)
            return
        }
        var hasEnabled = false
        componentNames.forEach {
            if (icon.equals(it.className.substringAfterLast("."), true)) {
                hasEnabled = true
                //启用
                packageManager.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                //禁用
                packageManager.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
        if (hasEnabled) {
            packageManager.setComponentEnabledSetting(
                ComponentName(appCtx, WelcomeActivity::class.java.name),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                ComponentName(appCtx, WelcomeActivity::class.java.name),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}