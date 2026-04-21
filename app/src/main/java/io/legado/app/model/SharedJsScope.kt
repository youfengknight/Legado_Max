package io.legado.app.model

import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import splitties.init.appCtx
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

object SharedJsScope {

    private val cacheFolder = File(appCtx.cacheDir, "shareJs")
    private val aCache = ACache.get(cacheFolder)

    private val scopeMap = LruCache<String, WeakReference<Scriptable>>(16)

    private fun resolveJsLibString(jsLib: String?): String? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        return if (jsLib.isJsonObject()) {
            val jsMap: Map<String, String> = GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            )
            buildString {
                jsMap.values.forEach { value ->
                    val js = when {
                        value.isAbsUrl() -> {
                            val fileName = MD5Utils.md5Encode(value)
                            var cacheJs = aCache.getAsString(fileName)
                            if (cacheJs == null) {
                                cacheJs = runBlocking {
                                    okHttpClient.newCallStrResponse {
                                        url(value)
                                    }.body
                                }
                                if (cacheJs != null) {
                                    aCache.put(fileName, cacheJs)
                                } else {
                                    throw NoStackTraceException("涓嬭浇jsLib-${value}澶辫触")
                                }
                            }
                            cacheJs
                        }

                        else -> value
                    }
                    if (!js.isNullOrBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(js)
                    }
                }
            }.takeIf { it.isNotBlank() }
        } else {
            jsLib
        }
    }

    fun getScope(jsLib: String?, coroutineContext: CoroutineContext?): Scriptable? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        val key = MD5Utils.md5Encode(jsLib)
        var scope = scopeMap[key]?.get()
        if (scope == null) {
            scope = RhinoScriptEngine.run {
                getRuntimeScope(ScriptBindings())
            }
            resolveJsLibString(jsLib)?.let {
                RhinoScriptEngine.eval(it, scope, coroutineContext)
            }
            if (scope is ScriptableObject) {
                /**
                 * 闃绘鏂板叏灞€澧炲姞锛堝嵆鍑芥暟鍐呮湭鐢╲ar鐨勯殣鎬у叏灞€鍙橀噺鍒涘缓锛変細鐩存帴闅愭€у垱寤哄け璐ワ紝鎻愮ず鍙橀噺鏈畾涔?
                 */
                scope.preventExtensions()
            }
            scopeMap.put(key, WeakReference(scope))
        }
        return scope
    }

    fun getJsLibString(jsLib: String?): String? {
        return resolveJsLibString(jsLib)
    }

    fun remove(jsLib: String?) {
        if (jsLib.isNullOrBlank()) {
            return
        }
        if (jsLib.isJsonObject()) {
            val jsMap: Map<String, String> = GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            )
            jsMap.values.forEach { value ->
                if (value.isAbsUrl()) {
                    val fileName = MD5Utils.md5Encode(value)
                    aCache.remove(fileName)
                }
            }
        }
        val key = MD5Utils.md5Encode(jsLib)
        scopeMap.remove(key)
    }

}
