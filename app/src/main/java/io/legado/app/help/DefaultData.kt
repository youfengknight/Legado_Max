package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx
import java.io.File

object DefaultData {

    fun upVersion() {
        if (LocalConfig.versionCode < AppConst.appInfo.versionCode) {
            Coroutine.async {
                if (LocalConfig.needUpHttpTTS) {
                    importDefaultHttpTTS()
                }
                if (LocalConfig.needUpTxtTocRule) {
                    importDefaultTocRules()
                }
                if (LocalConfig.needUpRssSources) {
                    importDefaultRssSources()
                }
                if (LocalConfig.needUpDictRule) {
                    importDefaultDictRules()
                }
            }.onError {
                it.printOnDebug()
            }
        }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                appCtx.assets.open("defaultData${File.separator}httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ThemeConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfig.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    /**
     * 默认HTML封面模板
     * 
     * 用于生成自定义封面图片的HTML模板，特点：
     * - 自适应尺寸：100%宽高，适配预览区域
     * - 渐变背景：紫色系渐变
     * - 居中布局：书名和作者垂直居中
     * - 支持变量：{{bookName}}（书名）、{{author}}（作者）
     * 
     * 用户可以自定义此模板来创建个性化封面样式
     */
    val coverHtmlTemplate: String by lazy {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    width: 100%;
                    height: 100%;
                }
                body {
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    color: white;
                    overflow: hidden;
                }
                .container {
                    padding: 24px;
                    text-align: center;
                    width: 100%;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                }
                .title {
                    font-size: 36px;
                    font-weight: bold;
                    line-height: 1.3;
                    margin-bottom: 16px;
                    word-break: break-word;
                    text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.3);
                    max-width: 100%;
                }
                .author {
                    font-size: 18px;
                    opacity: 0.9;
                    font-weight: 300;
                    max-width: 100%;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="title" id="title">{{bookName}}</div>
                <div class="author" id="author">{{author}}</div>
            </div>
            <script>
                function fitText(el, maxSize, minSize) {
                    var size = maxSize;
                    el.style.fontSize = size + 'px';
                    while (size > minSize) {
                        if (el.scrollWidth <= el.clientWidth && el.scrollHeight <= el.clientHeight * 1.5) {
                            break;
                        }
                        size--;
                        el.style.fontSize = size + 'px';
                    }
                }
                var title = document.getElementById('title');
                var author = document.getElementById('author');
                fitText(title, 36, 14);
                fitText(author, 18, 10);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    fun importDefaultDictRules() {
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

}