package io.legado.app.ui.book.read.config

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TtsDebugModel(application: Application) : BaseViewModel(application) {

    var httpTTS: HttpTTS? = null
    var callback: ((Int, String) -> Unit)? = null
    var audioUrl: String? = null
    var jsLibCode: String? = null
    var resultData: String? = null
    var executeLogs: String? = null

    fun init(ttsId: Long, finally: () -> Unit) {
        execute {
            httpTTS = appDb.httpTTSDao.get(ttsId)
        }.onFinally {
            finally.invoke()
        }
    }

    fun observe(callback: (Int, String) -> Unit) {
        this.callback = callback
    }

    fun startDebug(text: String, speed: Int, pitch: Int, speaker: String, 
                   loginInfo: Map<String, String>, start: (() -> Unit)? = null) {
        execute {
            val tts = httpTTS ?: return@execute
            start?.invoke()
            
            callback?.invoke(0, "=== 开始测试 TTS 引擎 ===")
            callback?.invoke(0, "引擎名称: ${tts.name}")
            callback?.invoke(0, "测试文本: $text")
            callback?.invoke(0, "语速: $speed, 音调: $pitch, 音色: $speaker")
            
            val result = mutableMapOf<String, Any>()
            result.putAll(loginInfo)
            result["音色"] = speaker
            result["音调"] = pitch
            resultData = GSON.toJson(result)
            
            callback?.invoke(0, "登录信息: ${resultData}")
            
            tts.putLoginInfo(GSON.toJson(result))
            callback?.invoke(0, "已保存登录信息")
            
            val analyzeUrl = AnalyzeUrl(
                tts.url,
                speakText = text,
                speakSpeed = speed,
                source = tts,
                readTimeout = 30 * 1000L
            )
            
            val url = analyzeUrl.url
            callback?.invoke(0, "请求URL: $url")
            
            val response = analyzeUrl.getResponseAwait()
            callback?.invoke(0, "响应状态码: ${response.code}")
            callback?.invoke(0, "响应Content-Type: ${response.headers["Content-Type"]}")
            
            val contentType = response.headers["Content-Type"]?.substringBefore(";")
            
            when {
                contentType?.startsWith("audio/") == true -> {
                    callback?.invoke(0, "直接返回音频流")
                    audioUrl = url.toString()
                    response.body.close()
                    callback?.invoke(1, "测试成功！音频URL: $audioUrl")
                }
                
                contentType == "application/json" || contentType?.startsWith("text/") == true -> {
                    callback?.invoke(0, "返回JSON数据")
                    val responseBody = response.body.string()
                    callback?.invoke(0, "响应内容: $responseBody")
                    
                    val json = io.legado.app.utils.jsonPath.parse(responseBody)
                    val extractedAudioUrl = json.read<String>("$.data.audio_url") 
                        ?: json.read<String>("$.audio_url")
                        ?: json.read<String>("$.url")
                    
                    if (!extractedAudioUrl.isNullOrBlank()) {
                        audioUrl = extractedAudioUrl
                        callback?.invoke(1, "测试成功！提取的音频URL: $audioUrl")
                    } else {
                        callback?.invoke(-1, "测试失败: JSON中未找到音频URL")
                    }
                }
                
                else -> {
                    val responseBody = response.body.string()
                    callback?.invoke(-1, "测试失败: 未知的Content-Type: $contentType")
                    callback?.invoke(-1, "响应内容: $responseBody")
                }
            }
            
            jsLibCode = tts.jsLib
            
        }.onError { e ->
            callback?.invoke(-1, "测试失败: ${e.message}")
            callback?.invoke(-1, e.stackTraceToString())
        }
    }

}