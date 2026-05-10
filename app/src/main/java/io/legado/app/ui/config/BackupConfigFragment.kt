package io.legado.app.ui.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupConfig
import io.legado.app.help.storage.BackupFileValidator
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.BackupSelectorConfig
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.help.storage.ValidationResult
import io.legado.app.help.storage.ValidationState
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.dialog.BackupInfoDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.checkWrite
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.launch
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toEditable
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx


class BackupConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MenuProvider {

    private val viewModel by activityViewModels<ConfigViewModel>()
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private var backupJob: Job? = null
    private var restoreJob: Job? = null

    private val selectBackupPath = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                AppConfig.backupPath = uri.toString()
            } else {
                AppConfig.backupPath = uri.path
            }
        }
    }
    private val backupDir = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            if (uri.isContentScheme()) {
                AppConfig.backupPath = uri.toString()
                backup(uri.toString())
            } else {
                uri.path?.let { path ->
                    AppConfig.backupPath = path
                    backup(path)
                }
            }
        }
    }
    private val restoreDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (AppConfig.restoreShowSelector) {
                showRestoreFileSelector(uri)
            } else {
                waitDialog.setText("恢复中…")
                waitDialog.show()
                val task = Coroutine.async {
                    Restore.restore(appCtx, uri)
                }.onFinally {
                    waitDialog.dismiss()
                }
                waitDialog.setOnCancelListener {
                    task.cancel()
                }
            }
        }
    }
    private val restoreOld = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ImportOldData.importUri(appCtx, uri)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_backup)
        findPreference<EditTextPreference>(PreferKey.webDavPassword)?.let {
            it.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                editText.setSelection(editText.text.length)
            }
        }
        findPreference<EditTextPreference>(PreferKey.webDavDir)?.let {
            it.setOnBindEditTextListener { editText ->
                editText.text = AppConfig.webDavDir?.toEditable()
                editText.setSelection(editText.text.length)
            }
        }
        findPreference<EditTextPreference>(PreferKey.webDavDeviceName)?.let {
            it.setOnBindEditTextListener { editText ->
                editText.text = AppConfig.webDavDeviceName?.toEditable()
                editText.setSelection(editText.text.length)
            }
        }
        upPreferenceSummary(PreferKey.webDavUrl, getPrefString(PreferKey.webDavUrl))
        upPreferenceSummary(PreferKey.webDavAccount, getPrefString(PreferKey.webDavAccount))
        upPreferenceSummary(PreferKey.webDavPassword, getPrefString(PreferKey.webDavPassword))
        upPreferenceSummary(PreferKey.webDavDir, AppConfig.webDavDir)
        upPreferenceSummary(PreferKey.webDavDeviceName, AppConfig.webDavDeviceName)
        upPreferenceSummary(PreferKey.backupPath, getPrefString(PreferKey.backupPath))
        findPreference<io.legado.app.lib.prefs.Preference>("web_dav_restore")
            ?.onLongClick {
                restoreFromLocal()
                true
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.backup_restore)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
        activity?.addMenuProvider(this, viewLifecycleOwner)
        if (!LocalConfig.backupHelpVersionIsLast) {
            showHelp("webDavHelp")
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.backup_restore, menu)
        menu.applyTint(requireContext())
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_help -> {
                showHelp("webDavHelp")
                return true
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.backupPath -> upPreferenceSummary(key, getPrefString(key))
            PreferKey.webDavUrl,
            PreferKey.webDavAccount,
            PreferKey.webDavPassword,
            PreferKey.webDavDir -> listView.post {
                upPreferenceSummary(key, appCtx.getPrefString(key))
                viewModel.upWebDavConfig()
            }

            PreferKey.webDavDeviceName -> upPreferenceSummary(key, getPrefString(key))
        }
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.webDavUrl ->
                if (value.isNullOrBlank()) {
                    preference.summary = getString(R.string.web_dav_url_s)
                } else {
                    preference.summary = value
                }

            PreferKey.webDavAccount ->
                if (value.isNullOrBlank()) {
                    preference.summary = getString(R.string.web_dav_account_s)
                } else {
                    preference.summary = value
                }

            PreferKey.webDavPassword ->
                if (value.isNullOrEmpty()) {
                    preference.summary = getString(R.string.web_dav_pw_s)
                } else {
                    preference.summary = "*".repeat(value.length)
                }

            PreferKey.webDavDir -> preference.summary = when (value) {
                null -> "legado"
                else -> value
            }

            else -> {
                if (preference is ListPreference) {
                    val index = preference.findIndexOfValue(value)
                    // Set the summary to reflect the new value.
                    preference.summary = if (index >= 0) preference.entries[index] else null
                } else {
                    preference.summary = value
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PreferKey.backupPath -> selectBackupPath.launch()
            PreferKey.restoreIgnore -> backupIgnore()
            "backup_selector" -> showBackupSelector()
            "web_dav_backup" -> backup()
            "web_dav_restore" -> restore()
            "import_old" -> restoreOld.launch()
            "viewBackupInfo" -> showBackupInfo()
        }
        return super.onPreferenceTreeClick(preference)
    }

    /**
     * 备份忽略设置
     */
    private fun backupIgnore() {
        val checkedItems = BooleanArray(BackupConfig.ignoreKeys.size) {
            BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[it]] ?: false
        }
        alert(R.string.restore_ignore) {
            multiChoiceItems(BackupConfig.ignoreTitle, checkedItems) { _, which, isChecked ->
                BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[which]] = isChecked
            }
            onDismiss {
                BackupConfig.saveIgnoreConfig()
            }
        }
    }

    /**
     * 显示备份选择器
     */
    private fun showBackupSelector() {
        val items = BackupSelectorConfig.allItems
        val titles = items.map { "[${it.group}] ${it.title}" }.toTypedArray()
        val checkedItems = BooleanArray(items.size) { index ->
            BackupSelectorConfig.isSelected(items[index].key)
        }
        
        alert(R.string.backup_selector) {
            multiChoiceItems(titles, checkedItems) { _, which, isChecked ->
                BackupSelectorConfig.setSelected(items[which].key, isChecked)
            }
            positiveButton(R.string.select_all) {
                BackupSelectorConfig.selectAll()
                showBackupSelector()
            }
            negativeButton(R.string.un_select_all) {
                BackupSelectorConfig.deselectAll()
                showBackupSelector()
            }
            neutralButton(R.string.ok) {
                BackupSelectorConfig.save()
            }
            onDismiss {
                BackupSelectorConfig.save()
            }
        }
    }

    /**
     * 显示备份信息
     */
    private fun showBackupInfo() {
        BackupInfoDialog.newInstance().show(childFragmentManager, "backupInfo")
    }


    fun backup() {
        val backupPath = AppConfig.backupPath
        if (backupPath.isNullOrEmpty()) {
            backupDir.launch()
        } else {
            if (backupPath.isContentScheme()) {
                lifecycleScope.launch {
                    val canWrite = withContext(IO) {
                        FileDoc.fromDir(backupPath).checkWrite()
                    }
                    if (canWrite) {
                        backup(backupPath)
                    } else {
                        backupDir.launch()
                    }
                }
            } else {
                backupUsePermission(backupPath)
            }
        }
    }

    private fun backup(backupPath: String) {
        waitDialog.setText("备份中…")
        waitDialog.setOnCancelListener {
            backupJob?.cancel()
        }
        waitDialog.show()
        backupJob?.cancel()
        backupJob = lifecycleScope.launch {
            try {
                Backup.backupLocked(requireContext(), backupPath)
                appCtx.toastOnUi(R.string.backup_success)
            } catch (e: Throwable) {
                ensureActive()
                AppLog.put("备份出错\n${e.localizedMessage}", e)
                appCtx.toastOnUi(
                    appCtx.getString(
                        R.string.backup_fail,
                        e.localizedMessage
                    )
                )
            } finally {
                ensureActive()
                waitDialog.dismiss()
            }
        }
    }

    private fun backupUsePermission(path: String) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                backup(path)
            }
            .request()
    }

    fun restore() {
        waitDialog.setText(R.string.loading)
        waitDialog.setOnCancelListener {
            restoreJob?.cancel()
        }
        waitDialog.show()
        Coroutine.async {
            restoreJob = coroutineContext[Job]
            showRestoreDialog(requireContext())
        }.onError {
            AppLog.put("恢复备份出错WebDavError\n${it.localizedMessage}", it)
            if (context == null) {
                return@onError
            }
            alert {
                setTitle(R.string.restore)
                setMessage("WebDavError\n${it.localizedMessage}\n将从本地备份恢复。")
                okButton {
                    restoreFromLocal()
                }
                cancelButton()
            }
        }.onFinally {
            waitDialog.dismiss()
        }
    }

    private suspend fun showRestoreDialog(context: Context) {
        val names = withContext(IO) { AppWebDav.getBackupNames() }
        if (AppWebDav.isJianGuoYun && names.size > 700) {
            context.toastOnUi("由于坚果云限制列出文件数量，部分备份可能未显示，请及时清理旧备份")
        }
        if (names.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            withContext(Main) {
                context.selector(
                    title = context.getString(R.string.select_restore_file),
                    items = names
                ) { _, index ->
                    if (index in 0 until names.size) {
                        listView.post {
                            restoreWebDav(names[index])
                        }
                    }
                }
            }
        } else {
            throw NoStackTraceException("Web dav no back up file")
        }
    }

    private fun restoreWebDav(name: String) {
        waitDialog.setText("恢复中…")
        waitDialog.show()
        val task = Coroutine.async {
            AppWebDav.restoreWebDav(name)
        }.onError {
            AppLog.put("WebDav恢复出错\n${it.localizedMessage}", it)
            appCtx.toastOnUi("WebDav恢复出错\n${it.localizedMessage}")
        }.onFinally {
            waitDialog.dismiss()
        }
        waitDialog.setOnCancelListener {
            task.cancel()
        }
    }

    private fun restoreFromLocal() {
        restoreDoc.launch {
            title = getString(R.string.select_restore_file)
            mode = HandleFileContract.FILE
            allowExtensions = arrayOf("zip")
        }
    }

    /**
     * 显示恢复文件选择对话框
     * 解压ZIP并列出文件供用户选择
     */
    private fun showRestoreFileSelector(uri: android.net.Uri) {
        waitDialog.setText("读取备份文件...")
        waitDialog.show()
        
        lifecycleScope.launch {
            try {
                // 解压到临时目录
                val tempPath = Backup.backupPath
                FileUtils.delete(tempPath)
                
                withContext(IO) {
                    val contentResolver = requireContext().contentResolver
                    val inputStream = contentResolver.openInputStream(uri)
                    inputStream?.use {
                        ZipUtils.unZipToPath(it, tempPath)
                    }
                }
                
                // 获取文件列表
                val files = withContext(IO) {
                    java.io.File(tempPath).listFiles()
                        ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".xml")) }
                        ?.map { file ->
                            BackupInfoHelper.BackupFileInfo(
                                fileName = file.name,
                                displayName = BackupInfoHelper.displayNameMap[file.name] ?: file.name,
                                size = file.length(),
                                selected = true
                            )
                        } ?: emptyList()
                }
                
                waitDialog.dismiss()
                
                if (files.isEmpty()) {
                    appCtx.toastOnUi("备份文件中没有可恢复的内容")
                    return@launch
                }
                
                // 显示选择对话框
                showFileSelectionDialog(files, tempPath)
                
            } catch (e: Exception) {
                waitDialog.dismiss()
                AppLog.put("读取备份文件出错\n${e.localizedMessage}", e)
                appCtx.toastOnUi("读取备份文件出错\n${e.localizedMessage}")
            }
        }
    }
    
    private var validationResults = mutableMapOf<String, ValidationResult>()
    private var validationJob: Job? = null
    private var composeDialogView: View? = null
    
    private fun showFileSelectionDialog(
        files: List<BackupInfoHelper.BackupFileInfo>,
        backupPath: String
    ) {
        validationResults.clear()
        dismissComposeDialog()
        
        val activity = requireActivity()
        val rootView = activity.window.decorView as? ViewGroup ?: return
        
        var showDialog by mutableStateOf(true)
        var showErrorDialog by mutableStateOf<ValidationResult?>(null)
        val results = mutableStateMapOf<String, ValidationResult>()
        
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    if (showDialog) {
                        FileValidationDialog(
                            files = files,
                            validationResults = results,
                            onValidate = {
                                validationJob?.cancel()
                                validationJob = lifecycleScope.launch {
                                    try {
                                        BackupFileValidator.validateFiles(backupPath, files.map { it.fileName }) { fileName, result ->
                                            results[fileName] = result
                                            validationResults[fileName] = result
                                        }
                                    } catch (e: Exception) {
                                        appCtx.toastOnUi("格式检测出错: ${e.message}")
                                    }
                                }
                            },
                            onConfirm = { selectedFiles ->
                                if (selectedFiles.isEmpty()) {
                                    appCtx.toastOnUi("请至少选择一个文件")
                                    return@FileValidationDialog
                                }
                                showDialog = false
                                dismissComposeDialog()
                                
                                validationJob?.cancel()
                                waitDialog.setText("恢复中…")
                                waitDialog.show()
                                val task = Coroutine.async {
                                    Restore.restoreSelected(appCtx, backupPath, selectedFiles)
                                }.onFinally {
                                    waitDialog.dismiss()
                                }
                                waitDialog.setOnCancelListener {
                                    task.cancel()
                                }
                            },
                            onDismiss = {
                                showDialog = false
                                dismissComposeDialog()
                                validationJob?.cancel()
                            },
                            onInfoClick = { result ->
                                showErrorDialog = result
                            }
                        )
                        
                        showErrorDialog?.let { result ->
                            ValidationErrorDetailDialog(
                                result = result,
                                onDismiss = { showErrorDialog = null }
                            )
                        }
                    }
                }
            }
        }
        
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootView.addView(composeView, layoutParams)
        composeDialogView = composeView
    }
    
    private fun dismissComposeDialog() {
        composeDialogView?.let { view ->
            val parent = view.parent as? ViewGroup
            parent?.removeView(view)
        }
        composeDialogView = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissComposeDialog()
        waitDialog.dismiss()
    }

}