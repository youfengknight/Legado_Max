package io.legado.app.ui.file

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.UploadHistory
import io.legado.app.help.DirectLinkUpload
import io.legado.app.model.upload.DirectLinkUploadRepository
import io.legado.app.utils.*

import java.io.File

class HandleFileViewModel(application: Application) : BaseViewModel(application) {

    val errorLiveData = MutableLiveData<String>()
    private val repository = DirectLinkUploadRepository()

    fun upload(
        fileName: String,
        file: Any,
        contentType: String,
        success: (url: String) -> Unit
    ) {
        execute {
            val rule = repository.getDefaultRule()
                ?: throw IllegalStateException("没有可用的上传规则")
            
            val startTime = System.currentTimeMillis()
            
            try {
                val downloadUrl = DirectLinkUpload.upLoad(
                    fileName = fileName,
                    file = file,
                    contentType = contentType,
                    rule = DirectLinkUpload.Rule(
                        uploadUrl = rule.uploadUrl,
                        downloadUrlRule = rule.downloadUrlRule,
                        summary = rule.summary,
                        compress = rule.compress
                    )
                )
                
                val duration = System.currentTimeMillis() - startTime
                
                val history = UploadHistory(
                    fileName = fileName,
                    fileSize = getFileSize(file),
                    contentType = contentType,
                    duration = duration,
                    downloadUrl = downloadUrl,
                    ruleId = rule.id,
                    ruleSummary = rule.summary,
                    success = true
                )
                
                repository.addHistory(history)
                repository.incrementUploadCount(rule.id)
                
                downloadUrl
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                
                val history = UploadHistory(
                    fileName = fileName,
                    fileSize = getFileSize(file),
                    contentType = contentType,
                    duration = duration,
                    downloadUrl = "",
                    ruleId = rule.id,
                    ruleSummary = rule.summary,
                    success = false,
                    errorMsg = e.localizedMessage
                )
                
                repository.addHistory(history)
                
                throw e
            }
        }.onSuccess {
            success.invoke(it)
        }.onError {
            AppLog.put("上传文件失败\n${it.localizedMessage}", it)
            it.printOnDebug()
            errorLiveData.postValue(it.localizedMessage)
        }
    }
    
    private fun getFileSize(file: Any): Long {
        return when (file) {
            is File -> file.length()
            is ByteArray -> file.size.toLong()
            is String -> file.toByteArray().size.toLong()
            else -> 0L
        }
    }

    fun saveToLocal(uri: Uri, fileName: String, data: Any, success: (uri: Uri) -> Unit) {
        execute {
            val bytes = when (data) {
                is File -> data.readBytes()
                is ByteArray -> data
                is String -> data.toByteArray()
                else -> GSON.toJson(data).toByteArray()
            }
            return@execute if (uri.isContentScheme()) {
                val doc = DocumentFile.fromTreeUri(context, uri)!!
                doc.findFile(fileName)?.delete()
                val newDoc = doc.createFile("", fileName)
                newDoc!!.writeBytes(context, bytes)
                newDoc.uri
            } else {
                val file = File(uri.path ?: uri.toString())
                val newFile = FileUtils.createFileIfNotExist(file, fileName)
                newFile.writeBytes(bytes)
                Uri.fromFile(newFile)
            }
        }.onError {
            it.printOnDebug()
            errorLiveData.postValue(it.localizedMessage)
        }.onSuccess {
            success.invoke(it)
        }
    }

}