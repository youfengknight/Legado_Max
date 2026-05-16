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

/**
 * 文件管理 ViewModel
 * 
 * 职责：
 * - 管理当前目录路径和文件列表
 * - 处理目录导航（进入、返回、跳转）
 * - 处理文件搜索过滤
 * - 处理文件删除和打开
 */
class FileManageViewModel(application: Application) : BaseViewModel(application) {

    /** 根目录：应用外部存储目录的父目录 */
    val rootDoc = context.getExternalFilesDir(null)?.parentFile

    /** 子目录列表（用于路径导航条显示） */
    private val _subDocs = MutableStateFlow<MutableList<File>>(mutableListOf())
    val subDocsFlow: StateFlow<List<File>> = _subDocs.asStateFlow()
    
    /** 当前目录下的文件列表 */
    private val _filesLiveData = MutableStateFlow<List<File>>(emptyList())
    val filesLiveData: StateFlow<List<File>> = _filesLiveData.asStateFlow()
    
    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    /** 当前未过滤的文件列表（用于搜索过滤） */
    private var currentFiles = listOf<File>()

    /** 当前目录的上级目录（用于显示 ".." 项） */
    val lastDir: File? get() = _subDocs.value.lastOrNull() ?: rootDoc

    init {
        // 初始化时加载根目录
        upFiles(rootDoc)
    }

    /**
     * 更新搜索关键词并过滤文件列表
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterFiles()
    }

    /**
     * 根据搜索关键词过滤文件列表
     * 过滤规则：保留 ".." 项和名称包含关键词的文件
     */
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

    /**
     * 加载指定目录下的文件列表
     * 
     * @param parentFile 目标目录，为 null 时不执行
     * 
     * 文件排序规则：
     * - 文件夹优先于文件
     * - 同类型按名称排序
     * - 非根目录时，列表第一项为上级目录（用于返回）
     */
    fun upFiles(parentFile: File?) {
        viewModelScope.launch {
            try {
                parentFile ?: return@launch
                val result = if (parentFile == rootDoc) {
                    // 根目录：直接列出文件
                    parentFile.listFiles()?.sortedWith(
                        compareBy({ it.isFile }, { it.name })
                    ) ?: emptyList()
                } else {
                    // 非根目录：第一项为上级目录，后面是当前目录内容
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

    /**
     * 返回根目录
     */
    fun goToRoot() {
        _subDocs.value = mutableListOf()
        upFiles(rootDoc)
    }

    /**
     * 跳转到指定索引的路径
     * 用于路径导航条点击跳转
     * 
     * @param index 子目录列表中的索引
     */
    fun goToPath(index: Int) {
        val newSubDocs = _subDocs.value.subList(0, index + 1).toMutableList()
        _subDocs.value = newSubDocs
        upFiles(newSubDocs.lastOrNull())
    }

    /**
     * 返回上级目录
     * 点击 ".." 项时调用
     */
    fun gotoLastDir() {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.removeLastOrNull()
        _subDocs.value = currentSubDocs
        upFiles(lastDir)
    }

    /**
     * 进入子目录
     * 
     * @param file 目标子目录
     */
    fun enterDir(file: File) {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.add(file)
        _subDocs.value = currentSubDocs
        upFiles(file)
    }

    /**
     * 打开文件
     * 使用 FileProvider 生成 URI 并调用系统打开
     * 
     * @param file 要打开的文件
     */
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

    /**
     * 删除文件
     * 删除后刷新当前目录
     * 
     * @param file 要删除的文件
     */
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
