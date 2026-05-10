package io.legado.app.help.storage

import android.util.Xml
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.utils.GSON
import io.legado.app.utils.isJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class ValidationState {
    NOT_VALIDATED,
    VALIDATING,
    VALID,
    WARNING,
    ERROR
}

data class ValidationResult(
    val fileName: String,
    val state: ValidationState,
    val message: String = "",
    val details: String = "",
    val missingFields: List<String> = emptyList(),
    val exception: Throwable? = null
) {
    val canRestore: Boolean
        get() = state == ValidationState.VALID || state == ValidationState.WARNING
}

object BackupFileValidator {
    
    private const val LARGE_FILE_THRESHOLD = 1024 * 1024L
    
    fun isLargeFile(file: File): Boolean = file.length() > LARGE_FILE_THRESHOLD
    
    suspend fun validateFiles(
        path: String,
        fileNames: List<String>,
        onProgress: (String, ValidationResult) -> Unit
    ): List<ValidationResult> {
        val results = mutableListOf<ValidationResult>()
        val largeFiles = mutableListOf<String>()
        val smallFiles = mutableListOf<String>()
        
        fileNames.forEach { fileName ->
            val file = File(path, fileName)
            if (file.exists()) {
                if (isLargeFile(file)) {
                    largeFiles.add(fileName)
                } else {
                    smallFiles.add(fileName)
                }
            }
        }
        
        withContext(Dispatchers.IO) {
            coroutineScope {
                smallFiles.map { fileName ->
                    async {
                        val result = validateFile(path, fileName)
                        withContext(Dispatchers.Main) {
                            onProgress(fileName, result)
                        }
                        result
                    }
                }.awaitAll().let {
                    results.addAll(it)
                }
            }
            
            for (fileName in largeFiles) {
                val result = validateFile(path, fileName)
                withContext(Dispatchers.Main) {
                    onProgress(fileName, result)
                }
                results.add(result)
            }
        }
        
        return results
    }
    
    suspend fun validateFile(path: String, fileName: String): ValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path, fileName)
                if (!file.exists()) {
                    return@withContext ValidationResult(
                        fileName = fileName,
                        state = ValidationState.ERROR,
                        message = "文件不存在",
                        details = "备份文件中找不到 $fileName"
                    )
                }
                
                if (file.length() == 0L) {
                    return@withContext ValidationResult(
                        fileName = fileName,
                        state = ValidationState.ERROR,
                        message = "文件为空",
                        details = "$fileName 文件大小为 0 字节"
                    )
                }
                
                when {
                    fileName.endsWith(".json") -> validateJsonFile(file, fileName)
                    fileName.endsWith(".xml") -> validateXmlFile(file, fileName)
                    else -> ValidationResult(
                        fileName = fileName,
                        state = ValidationState.WARNING,
                        message = "未知文件格式",
                        details = "无法验证 $fileName 的格式"
                    )
                }
            } catch (e: Exception) {
                ValidationResult(
                    fileName = fileName,
                    state = ValidationState.ERROR,
                    message = "验证异常: ${e.message}",
                    details = e.stackTraceToString(),
                    exception = e
                )
            }
        }
    }
    
    private fun validateJsonFile(file: File, fileName: String): ValidationResult {
        return try {
            val jsonText = file.readText()
            
            if (!jsonText.isJsonArray()) {
                return ValidationResult(
                    fileName = fileName,
                    state = ValidationState.ERROR,
                    message = "JSON 格式错误",
                    details = "$fileName 不是有效的 JSON 数组格式"
                )
            }
            
            val structureResult = validateDataStructure(fileName, jsonText)
            if (structureResult.state != ValidationState.VALID) {
                return structureResult
            }
            
            ValidationResult(
                fileName = fileName,
                state = ValidationState.VALID,
                message = "格式正确"
            )
        } catch (e: Exception) {
            ValidationResult(
                fileName = fileName,
                state = ValidationState.ERROR,
                message = "JSON 解析失败",
                details = "解析 $fileName 时出错: ${e.message}",
                exception = e
            )
        }
    }
    
    private fun validateXmlFile(file: File, fileName: String): ValidationResult {
        return try {
            val inputStream = file.inputStream()
            inputStream.use {
                val parser = Xml.newPullParser()
                parser.setInput(it, "utf-8")
                
                var event = parser.eventType
                var hasValidTags = false
                
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val name = parser.getAttributeValue(null, "name")
                        if (!name.isNullOrBlank()) {
                            hasValidTags = true
                            break
                        }
                    }
                    event = parser.next()
                }
                
                if (!hasValidTags) {
                    return ValidationResult(
                        fileName = fileName,
                        state = ValidationState.WARNING,
                        message = "XML 格式不完整",
                        details = "$fileName 缺少有效的配置项"
                    )
                }
                
                ValidationResult(
                    fileName = fileName,
                    state = ValidationState.VALID,
                    message = "格式正确"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                fileName = fileName,
                state = ValidationState.ERROR,
                message = "XML 解析失败",
                details = "解析 $fileName 时出错: ${e.message}",
                exception = e
            )
        }
    }
    
    private fun validateDataStructure(fileName: String, jsonText: String): ValidationResult {
        return try {
            when (fileName) {
                "bookshelf.json" -> validateEntityStructure<Book>(jsonText, listOf("name", "author"))
                "bookmark.json" -> validateEntityStructure<Bookmark>(jsonText, listOf("bookName", "chapterPos"))
                "bookGroup.json" -> validateEntityStructure<BookGroup>(jsonText, listOf("name"))
                "bookSource.json" -> validateEntityStructure<BookSource>(jsonText, listOf("bookSourceUrl", "bookSourceName"))
                "rssSources.json" -> validateEntityStructure<RssSource>(jsonText, listOf("sourceUrl", "sourceName"))
                "rssStar.json" -> validateEntityStructure<RssStar>(jsonText, listOf("origin"))
                "replaceRule.json" -> validateEntityStructure<ReplaceRule>(jsonText, listOf("name"))
                "readRecord.json" -> validateEntityStructure<ReadRecord>(jsonText, listOf("bookName"))
                "readRecordDetail.json" -> validateEntityStructure<ReadRecordDetail>(jsonText, listOf("bookName"))
                "readRecordSession.json" -> validateEntityStructure<ReadRecordSession>(jsonText, listOf("bookName"))
                "searchHistory.json" -> validateEntityStructure<SearchKeyword>(jsonText, listOf("word"))
                "txtTocRule.json" -> validateEntityStructure<TxtTocRule>(jsonText, listOf("name"))
                "httpTTS.json" -> validateEntityStructure<HttpTTS>(jsonText, listOf("name"))
                "keyboardAssists.json" -> validateEntityStructure<KeyboardAssist>(jsonText, listOf("name"))
                "dictRule.json" -> validateEntityStructure<DictRule>(jsonText, listOf("name"))
                "servers.json" -> validateEntityStructure<Server>(jsonText, listOf("name"))
                else -> ValidationResult(fileName, ValidationState.VALID, "格式正确")
            }
        } catch (e: Exception) {
            ValidationResult(
                fileName = fileName,
                state = ValidationState.ERROR,
                message = "数据结构验证失败",
                details = "验证 $fileName 数据结构时出错: ${e.message}",
                exception = e
            )
        }
    }
    
    private inline fun <reified T> validateEntityStructure(
        jsonText: String,
        requiredFields: List<String>
    ): ValidationResult {
        return try {
            val jsonArray = org.json.JSONArray(jsonText)
            if (jsonArray.length() == 0) {
                return ValidationResult(
                    fileName = "",
                    state = ValidationState.WARNING,
                    message = "数据为空",
                    details = "JSON 数组为空，没有数据需要验证"
                )
            }
            
            val firstItem = jsonArray.optJSONObject(0)
            if (firstItem == null) {
                return ValidationResult(
                    fileName = "",
                    state = ValidationState.WARNING,
                    message = "数据格式不完整",
                    details = "第一条数据不是有效的 JSON 对象"
                )
            }
            
            val missingFields = mutableListOf<String>()
            requiredFields.forEach { field ->
                if (!firstItem.has(field) || firstItem.isNull(field)) {
                    missingFields.add(field)
                }
            }
            
            if (missingFields.isNotEmpty()) {
                ValidationResult(
                    fileName = "",
                    state = ValidationState.WARNING,
                    message = "缺少必需字段",
                    details = "缺少字段: ${missingFields.joinToString(", ")}",
                    missingFields = missingFields
                )
            } else {
                ValidationResult(
                    fileName = "",
                    state = ValidationState.VALID,
                    message = "格式正确"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                fileName = "",
                state = ValidationState.ERROR,
                message = "数据结构解析失败",
                details = "解析数据结构时出错: ${e.message}",
                exception = e
            )
        }
    }
}
