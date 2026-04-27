package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.postDelayed
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogEditCodeBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppFreezeMonitor
import io.legado.app.help.DispatchersMonitor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.CheckSource
import io.legado.app.model.ImageProvider
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.service.WebService
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.video.config.SettingsDialog
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.LogUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.restart
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import splitties.init.appCtx

/**
 * 其它设置
 */
class OtherConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MenuProvider {

    private val viewModel by activityViewModels<ConfigViewModel>()
    private val packageManager = appCtx.packageManager
    private val componentName = ComponentName(
        appCtx,
        SharedReceiverActivity::class.java.name
    )
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }

    private var onlyUpdateReadPref: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        putPrefBoolean(PreferKey.processText, isProcessTextEnabled())
        addPreferencesFromResource(R.xml.pref_config_other)
        upPreferenceSummary(PreferKey.userAgent, AppConfig.userAgent)
        upPreferenceSummary(PreferKey.preDownloadNum, AppConfig.preDownloadNum.toString())
        upPreferenceSummary(PreferKey.threadCount, AppConfig.threadCount.toString())
        upPreferenceSummary(PreferKey.webPort, AppConfig.webPort.toString())
        upWebServiceTokenSummary()
        AppConfig.defaultBookTreeUri?.let {
            upPreferenceSummary(PreferKey.defaultBookTreeUri, it)
        }
        upPreferenceSummary(PreferKey.checkSource, CheckSource.summary)
        upPreferenceSummary(PreferKey.bitmapCacheSize, AppConfig.bitmapCacheSize.toString())
        upPreferenceSummary(PreferKey.imageRetainNum, AppConfig.imageRetainNum.toString())
        upPreferenceSummary(PreferKey.sourceEditMaxLine, AppConfig.sourceEditMaxLine.toString())
        onlyUpdateReadPref = findPreference<Preference>(PreferKey.onlyUpdateRead)?.also {
            it.isVisible = AppConfig.autoRefreshBook
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.other_setting)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
        activity?.addMenuProvider(this, viewLifecycleOwner)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PreferKey.userAgent -> showUserAgentDialog()
            PreferKey.customHosts -> showCustomHostsDialog()
            PreferKey.videoSetting -> showDialogFragment(SettingsDialog(requireActivity()))
            PreferKey.defaultBookTreeUri -> localBookTreeSelect.launch {
                title = getString(R.string.select_book_folder)
                mode = HandleFileContract.DIR_SYS
            }

            PreferKey.preDownloadNum -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.pre_download))
                .setMaxValue(9999)
                .setMinValue(0)
                .setValue(AppConfig.preDownloadNum)
                .show {
                    AppConfig.preDownloadNum = it
                }

            PreferKey.threadCount -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.threads_num_title))
                .setMaxValue(999)
                .setMinValue(1)
                .setValue(AppConfig.threadCount)
                .show {
                    AppConfig.threadCount = it
                }

            PreferKey.webPort -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.web_port_title))
                .setMaxValue(60000)
                .setMinValue(1024)
                .setValue(AppConfig.webPort)
                .show {
                    AppConfig.webPort = it
                }

            PreferKey.webServiceToken -> showWebServiceTokenDialog()

            PreferKey.cleanCache -> clearCache()
            PreferKey.uploadRule -> showDialogFragment<DirectLinkUploadConfig>()
            PreferKey.checkSource -> showDialogFragment<CheckSourceConfig>()
            PreferKey.bitmapCacheSize -> {
                NumberPickerDialog(requireContext())
                    .setTitle(getString(R.string.bitmap_cache_size))
                    .setMaxValue(1024)
                    .setMinValue(1)
                    .setValue(AppConfig.bitmapCacheSize)
                    .show {
                        AppConfig.bitmapCacheSize = it
                        ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
                    }
            }
            PreferKey.imageRetainNum -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.image_retain_number))
                .setMaxValue(999)
                .setMinValue(0)
                .setValue(AppConfig.imageRetainNum)
                .show {
                    AppConfig.imageRetainNum = it
                }

            PreferKey.sourceEditMaxLine -> {
                NumberPickerDialog(requireContext())
                    .setTitle(getString(R.string.source_edit_text_max_line))
                    .setMaxValue(Int.MAX_VALUE)
                    .setMinValue(10)
                    .setValue(AppConfig.sourceEditMaxLine)
                    .show {
                        AppConfig.sourceEditMaxLine = it
                    }
            }

            PreferKey.clearWebViewData -> clearWebViewData()
            "localPassword" -> alertLocalPassword()
            PreferKey.shrinkDatabase -> shrinkDatabase()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.preDownloadNum -> {
                upPreferenceSummary(key, AppConfig.preDownloadNum.toString())
            }

            PreferKey.threadCount -> {
                upPreferenceSummary(key, AppConfig.threadCount.toString())
                postEvent(PreferKey.threadCount, "")
            }

            PreferKey.webPort -> {
                upPreferenceSummary(key, AppConfig.webPort.toString())
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }

            PreferKey.webServiceAuthEnabled -> {
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }

            PreferKey.webServiceToken -> {
                upWebServiceTokenSummary()
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }

            PreferKey.defaultBookTreeUri -> {
                upPreferenceSummary(key, AppConfig.defaultBookTreeUri)
            }

            PreferKey.recordLog -> {
                AppConfig.recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)
                LogUtils.upLevel()
                LogUtils.logDeviceInfo()
                LiveEventBus.config().enableLogger(AppConfig.recordLog)
                AppFreezeMonitor.init(appCtx)
                DispatchersMonitor.init()
            }

            PreferKey.processText -> sharedPreferences?.let {
                setProcessTextEnable(it.getBoolean(key, true))
            }

            PreferKey.showDiscovery, PreferKey.showRss -> postEvent(EventBus.NOTIFY_MAIN, true)
            // 调试模式变化时发送事件，通知"我的"页面更新调试工具入口显示
            PreferKey.debugMode -> postEvent(EventBus.DEBUG_MODE_CHANGED, true)
            PreferKey.language -> listView.postDelayed(1000) {
                appCtx.restart()
            }

            PreferKey.userAgent -> listView.post {
                upPreferenceSummary(PreferKey.userAgent, AppConfig.userAgent)
            }

            PreferKey.checkSource -> listView.post {
                upPreferenceSummary(PreferKey.checkSource, CheckSource.summary)
            }

            PreferKey.bitmapCacheSize -> {
                upPreferenceSummary(key, AppConfig.bitmapCacheSize.toString())
            }

            PreferKey.imageRetainNum -> {
                upPreferenceSummary(key, AppConfig.imageRetainNum.toString())
            }

            PreferKey.sourceEditMaxLine -> {
                upPreferenceSummary(key, AppConfig.sourceEditMaxLine.toString())
            }

            PreferKey.autoRefresh -> {
                val isEnabled = sharedPreferences?.getBoolean(key, false) ?: false
                onlyUpdateReadPref?.isVisible = isEnabled
            }

            PreferKey.unsafeSsl -> {
                context?.alert(R.string.sure, R.string.need_restart_app) {
                    okButton {
                        appCtx.restart()
                    }
                    cancelButton()
                }
            }
        }
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.preDownloadNum -> preference.summary =
                getString(R.string.pre_download_s, value)

            PreferKey.threadCount -> preference.summary = getString(R.string.threads_num, value)
            PreferKey.webPort -> preference.summary = getString(R.string.web_port_summary, value)
            PreferKey.bitmapCacheSize -> preference.summary =
                getString(R.string.bitmap_cache_size_summary, value)
            PreferKey.imageRetainNum -> preference.summary =
                getString(R.string.image_retain_number_summary, value)

            PreferKey.sourceEditMaxLine -> preference.summary =
                getString(R.string.source_edit_max_line_summary, value)

            else -> if (preference is ListPreference) {
                val index = preference.findIndexOfValue(value)
                // Set the summary to reflect the new value.
                preference.summary = if (index >= 0) preference.entries[index] else null
            } else {
                preference.summary = value
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showUserAgentDialog() {
        alert(getString(R.string.user_agent)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = getString(R.string.user_agent)
                editView.setText(AppConfig.userAgent)
            }
            customView { alertBinding.root }
            okButton {
                val userAgent = alertBinding.editView.text?.toString()
                if (userAgent.isNullOrBlank()) {
                    removePref(PreferKey.userAgent)
                } else {
                    putPrefString(PreferKey.userAgent, userAgent)
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun showCustomHostsDialog() {
        alert(getString(R.string.custom_hosts)) {
            val alertBinding = DialogEditCodeBinding.inflate(layoutInflater).apply {
                editViewC.hint = getString(R.string.json_format)
                editView.addJsonPattern()
                editView.setText(AppConfig.customHosts)
            }
            customView { alertBinding.root }
            okButton {
                val customHosts = alertBinding.editView.text?.toString()
                if (customHosts.isJsonObject()) {
                    putPrefString(PreferKey.customHosts, customHosts!!)
                } else {
                    removePref(PreferKey.customHosts)
                }
            }
            cancelButton()
        }
    }

    private fun clearCache() {
        requireContext().alert(
            titleResource = R.string.clear_cache,
            messageResource = R.string.sure_del
        ) {
            okButton {
                viewModel.clearCache()
            }
            noButton()
        }
    }

    private fun shrinkDatabase() {
        alert(R.string.sure, R.string.shrink_database) {
            okButton {
                viewModel.shrinkDatabase()
            }
            noButton()
        }
    }

    private fun clearWebViewData() {
        alert(R.string.clear_webview_data, R.string.sure_del) {
            okButton {
                viewModel.clearWebViewData()
            }
            noButton()
        }
    }

    private fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setProcessTextEnable(enable: Boolean) {
        if (enable) {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun alertLocalPassword() {
        context?.alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun showWebServiceTokenDialog() {
        alert(getString(R.string.web_service_token_title)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = getString(R.string.web_service_token_title)
                editView.setText(AppConfig.webServiceToken)
            }
            customView { alertBinding.root }
            okButton {
                val token = alertBinding.editView.text?.toString()
                if (token.isNullOrBlank()) {
                    removePref(PreferKey.webServiceToken)
                } else {
                    AppConfig.webServiceToken = token
                }
                upWebServiceTokenSummary()
            }
            cancelButton()
        }
    }

    private fun upWebServiceTokenSummary() {
        val pref = findPreference<Preference>(PreferKey.webServiceToken) ?: return
        val token = AppConfig.webServiceToken
        pref.summary = if (token.length > 8) {
            "${token.take(4)}****${token.takeLast(4)}"
        } else {
            "****"
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.config_other, menu)
        menu.applyTint(requireContext())
        val searchItem = menu.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterPreferences(newText ?: "")
                    return true
                }
            })
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false
    }

    private fun filterPreferences(query: String) {
        val lowerQuery = query.lowercase()
        var firstMatchIndex = -1
        var currentIndex = 0

        for (i in 0 until preferenceScreen.preferenceCount) {
            val category = preferenceScreen.getPreference(i)
            if (category is PreferenceCategory) {
                var hasVisibleChild = false
                for (j in 0 until category.preferenceCount) {
                    val preference = category.getPreference(j)
                    val title = preference.title?.toString()?.lowercase() ?: ""
                    val summary = preference.summary?.toString()?.lowercase() ?: ""
                    val matches = query.isEmpty() || title.contains(lowerQuery) || summary.contains(lowerQuery)
                    preference.isVisible = matches
                    if (matches) {
                        hasVisibleChild = true
                        if (firstMatchIndex < 0) {
                            firstMatchIndex = currentIndex
                        }
                    }
                    currentIndex++
                }
                category.isVisible = hasVisibleChild || query.isEmpty()
                if (category.isVisible) {
                    currentIndex++
                }
            } else {
                val title = category.title?.toString()?.lowercase() ?: ""
                val summary = category.summary?.toString()?.lowercase() ?: ""
                val matches = query.isEmpty() || title.contains(lowerQuery) || summary.contains(lowerQuery)
                category.isVisible = matches
                if (matches && firstMatchIndex < 0) {
                    firstMatchIndex = currentIndex
                }
                currentIndex++
            }
        }

        if (firstMatchIndex >= 0) {
            listView.post {
                listView.smoothScrollToPosition(firstMatchIndex)
            }
        }
    }

}