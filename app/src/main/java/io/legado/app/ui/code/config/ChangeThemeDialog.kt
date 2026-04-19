package io.legado.app.ui.code.config

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogEditChangeThemeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getIndexById
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 更改主题对话框
 * 用于选择代码编辑器的配色主题
 * 支持深色/浅色主题自动切换
 */
class ChangeThemeDialog() : BaseDialogFragment(R.layout.dialog_edit_change_theme) {
    private val binding by viewBinding(DialogEditChangeThemeBinding::bind)
    private var callBack: CallBack? = null
    private var isClick = false
    private var editTemeAuto = AppConfig.editTemeAuto
    private val isDark
        get() = editTemeAuto && ThemeConfig.isDarkTheme()
    private var themeIndex = -1

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CallBack) {
            callBack = context
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * 片段创建时初始化
     * 加载当前主题设置
     */
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    /**
     * 初始化数据
     * 加载当前主题索引和自动切换设置
     */
    private fun initData() {
        binding.run {
            themeIndex = if (isDark) {
                AppConfig.editThemeDark
            } else {
                AppConfig.editTheme
            }
            if (themeIndex % 2 == 0) {
                chThemeL.checkByIndex(themeIndex / 2)
            } else {
                chThemeR.checkByIndex(themeIndex / 2)
            }
            switchSystemAuto.isChecked = editTemeAuto
            callBack?.upTheme(themeIndex)
        }
    }

    /**
     * 初始化视图
     * 设置主题选择和自动切换的监听器
     */
    private fun initView() {
        binding.run {
            chThemeL.setOnCheckedChangeListener { _, checkedId ->
                if (!isClick) {
                    isClick = true
                    chThemeR.clearCheck()
                    val int = chThemeL.getIndexById(checkedId) * 2
                    if (isDark) {
                        putPrefInt(PreferKey.editThemeDark, int)
                    } else {
                        putPrefInt(PreferKey.editTheme, int)
                    }
                    callBack?.upTheme(int)
                    isClick = false
                }
            }
            chThemeR.setOnCheckedChangeListener { _, checkedId ->
                if (!isClick) {
                    isClick = true
                    chThemeL.clearCheck()
                    val int = chThemeR.getIndexById(checkedId) * 2 + 1
                    if (isDark) {
                        putPrefInt(PreferKey.editThemeDark, int)
                    } else {
                        putPrefInt(PreferKey.editTheme, int)
                    }
                    callBack?.upTheme(int)
                    isClick = false
                }
            }
            switchSystemAuto.setOnCheckedChangeListener { _, isChecked ->
                putPrefBoolean(PreferKey.editTemeAuto, isChecked)
                editTemeAuto = isChecked
                initData()
            }
        }
    }

    /**
     * 回调接口
     * 用于通知主题更改
     */
    interface CallBack {
        fun upTheme(index: Int)
    }

}