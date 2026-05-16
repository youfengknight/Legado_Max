package io.legado.app.ui.file

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.utils.openFileUri
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileManageViewModel(application: Application) : BaseViewModel(application) {

    val rootDoc = context.getExternalFilesDir(null)?.parentFile
    private val _subDocs = MutableStateFlow<MutableList<File>>(mutableListOf())
    val subDocsFlow: StateFlow<List<File>> = _subDocs.asStateFlow()
    
    private val _filesLiveData = MutableStateFlow<List<File>>(emptyList())
    val filesLiveData: StateFlow<List<File>> = _filesLiveData.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private var currentFiles = listOf<File>()

    val lastDir: File? get() = _subDocs.value.lastOrNull() ?: rootDoc

    init {
        upFiles(rootDoc)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterFiles()
    }

    private fun filterFiles() {
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            currentFiles.filter {
                it.name == ".." || it.name.contains(query)
            }.let {
                _filesLiveData.value = it
            }
        } else {
            _filesLiveData.value = currentFiles
        }
    }

    fun upFiles(parentFile: File?) {
        viewModelScope.launch {
            try {
                parentFile ?: return@launch
                val result = if (parentFile == rootDoc) {
                    parentFile.listFiles()?.sortedWith(
                        compareBy({ it.isFile }, { it.name })
                    ) ?: emptyList()
                } else {
                    val list = arrayListOf(parentFile)
                    parentFile.listFiles()?.sortedWith(
                        compareBy({ it.isFile }, { it.name })
                    )?.let {
                        list.addAll(it)
                    }
                    list
                }
                currentFiles = result
                _searchQuery.value = ""
                _filesLiveData.value = result
            } catch (e: Exception) {
                context.toastOnUi(e.localizedMessage)
            }
        }
    }

    fun goToRoot() {
        _subDocs.value = mutableListOf()
        upFiles(rootDoc)
    }

    fun goToPath(index: Int) {
        val newSubDocs = _subDocs.value.subList(0, index + 1).toMutableList()
        _subDocs.value = newSubDocs
        upFiles(newSubDocs.lastOrNull())
    }

    fun gotoLastDir() {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.removeLastOrNull()
        _subDocs.value = currentSubDocs
        upFiles(lastDir)
    }

    fun enterDir(file: File) {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.add(file)
        _subDocs.value = currentSubDocs
        upFiles(file)
    }

    fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                AppConst.authority,
                file
            )
            context.openFileUri(uri)
        } catch (e: Exception) {
            context.toastOnUi(e.localizedMessage)
        }
    }

    fun delFile(file: File) {
        viewModelScope.launch {
            try {
                file.delete()
                upFiles(lastDir)
            } catch (e: Exception) {
                context.toastOnUi(e.localizedMessage)
            }
        }
    }

}
