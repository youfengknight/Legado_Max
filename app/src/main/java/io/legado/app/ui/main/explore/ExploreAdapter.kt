package io.legado.app.ui.main.explore

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.collection.LruCache
import androidx.core.view.children
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.flexbox.FlexboxLayout
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemFindBookBinding
import io.legado.app.databinding.ItemFilletSelectorSingleBinding
import io.legado.app.databinding.ItemFilletCompleteTextBinding
import io.legado.app.databinding.ItemExploreHtmlBinding
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.WebCacheManager
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.buildUseWebInjection
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebJsExtensions.Companion.wrapUseWebHtml
import io.legado.app.help.webView.WebViewPool
import io.legado.app.help.webView.WebViewPool.fitInlineContent
import io.legado.app.help.webView.WebViewPool.installInlineContentRefitOnTouch
import io.legado.app.help.webView.WebViewPool.prepareForInlineContent
import io.legado.app.help.webView.WebViewPool.scheduleInlineContentFit
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.text.AccentTextView
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.InfoMap
import io.legado.app.utils.GSON
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.removeLastElement
import io.legado.app.utils.setHtml
import io.legado.app.utils.setSelectionSafely
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.text.isNullOrEmpty

class ExploreAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<BookSourcePart, ItemFindBookBinding>(context) {
    companion object {
        val exploreInfoMapList = LruCache<String, InfoMap>(99)
        private val exploreWebViewHeightCache = LruCache<String, Int>(99)
    }
    private val recycler = arrayListOf<TextView>()
    private val textRecycler = arrayListOf<AutoCompleteTextView>()
    private val selectRecycler = arrayListOf<LinearLayout>()
    private val htmlRecycler = arrayListOf<FrameLayout>()

    private var expandedSourceUrl: String? = null
    private var scrollToSourceUrl: String? = null
    private var lastClickTime: Long = 0
    private val sourceKinds = ConcurrentHashMap<String, List<ExploreKind>>()
    private val activeWebViews = linkedMapOf<FrameLayout, PooledWebView>()
    private var saveInfoMapJob: Job? = null

    override fun getViewBinding(parent: ViewGroup): ItemFindBookBinding {
        return ItemFindBookBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFindBookBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (holder.layoutPosition == itemCount - 1) {
                root.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            } else {
                root.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 0)
            }
            if (payloads.isEmpty()) {
                tvName.text = item.bookSourceName
            }
            if (expandedSourceUrl == item.bookSourceUrl) {
                ivStatus.setImageResource(R.drawable.ic_arrow_down)
                rotateLoading.loadingColor = context.accentColor
                rotateLoading.visible()
                Coroutine.async(callBack.scope) {
                    sourceKinds[item.bookSourceUrl]?.also {
                        return@async it
                    }
                    item.exploreKinds().also {
                        sourceKinds[item.bookSourceUrl] = it
                    }
                }.onSuccess { kindList ->
                    upKindList(this@run, item, kindList, item)
                }.onFinally {
                    rotateLoading.gone()
                    scrollToSourceUrl?.let { sourceUrl ->
                        findSourcePosition(sourceUrl)?.let(callBack::scrollTo)
                        if (sourceUrl == item.bookSourceUrl) {
                            scrollToSourceUrl = null
                        }
                    }
                }
            } else kotlin.runCatching {
                ivStatus.setImageResource(R.drawable.ic_arrow_right)
                rotateLoading.gone()
                recyclerFlexbox(flexbox)
                flexbox.gone()
            }
        }
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun upKindList(binding: ItemFindBookBinding, item: BookSourcePart, kinds: List<ExploreKind>, expandedItem: BookSourcePart) {
        if (kinds.isEmpty()) {
            return
        }
        val flexbox = binding.flexbox
        val sourceUrl = item.bookSourceUrl
        kotlin.runCatching {
            recyclerFlexbox(flexbox)
            flexbox.visible()
            val source by lazy { appDb.bookSourceDao.getBookSource(sourceUrl) }
            val infoMap by lazy {
                exploreInfoMapList[sourceUrl] ?:  InfoMap(sourceUrl).also {
                    exploreInfoMapList.put(sourceUrl, it)
                }
            }
            val sourceJsExtensions by lazy {
                SourceLoginJsExtensions(context as? AppCompatActivity, source,
                    callback = object : SourceLoginJsExtensions.Callback {
                        override fun upUiData(data: Map<String, Any?>?) {
                        }

                        override fun reUiView(deltaUp: Boolean) {
                            refreshExplore(expandedItem, binding)
                        }
                    })
            }
            kinds.forEach { kind ->
                val type = kind.type
                val title = kind.title
                val viewName = kind.viewName
                when (type) {
                    Type.url -> {
                        val tv = getFlexboxChild(flexbox)
                        flexbox.addView(tv)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> tv.gravity = Gravity.START
                                "flex_end" -> tv.gravity = Gravity.END
                                else -> tv.gravity = Gravity.CENTER
                            }
                            apply(tv)
                        }
                        if (viewName == null) {
                            tv.text = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            tv.text = n
                        } else {
                            tv.text = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    tv.text = "null"
                                } else {
                                    tv.text = n
                                }
                            }.onError { _ ->
                                tv.text = "err"
                            }
                        }
                        tv.setOnClickListener {// 辅助触发无障碍功能正常
                            val url = kind.url ?: return@setOnClickListener
                            if (kind.title.startsWith("ERROR:")) {
                                it.activity?.showDialogFragment(TextDialog("ERROR", url))
                            } else {
                                callBack.openExplore(sourceUrl, kind.title, url)
                            }
                        }
                        tv.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.isSelected = true
                                }
                                MotionEvent.ACTION_UP -> {
                                    view.isSelected = false
                                    val upTime = System.currentTimeMillis()
                                    if (upTime - lastClickTime < 200) {
                                        return@setOnTouchListener true
                                    }
                                    lastClickTime = upTime
                                    val url = kind.url?.takeIf { it.isNotBlank() } ?: return@setOnTouchListener true
                                    if (kind.title.startsWith("ERROR:")) {
                                        view.activity?.showDialogFragment(TextDialog("ERROR", url))
                                    } else {
                                        callBack.openExplore(sourceUrl, kind.title, url)
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    view.isSelected = false
                                }
                            }
                            return@setOnTouchListener true
                        }
                    }

                    Type.html -> {
                        val container = getFlexboxChildHtml(flexbox)
                        flexbox.addView(container)
                        kind.style().apply(container)
                        bindHtmlView(container, kind, source, infoMap, title, sourceJsExtensions)
                    }

                    Type.button -> {
                        val tv = getFlexboxChild(flexbox)
                        flexbox.addView(tv)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> tv.gravity = Gravity.START
                                "flex_end" -> tv.gravity = Gravity.END
                                else -> tv.gravity = Gravity.CENTER
                            }
                            apply(tv)
                        }
                        if (viewName == null) {
                            tv.text = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            tv.text = n
                        } else {
                            tv.text = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    tv.text = "null"
                                } else {
                                    tv.text = n
                                }
                            }.onError{ _ ->
                                tv.text = "err"
                            }
                        }
                        tv.setOnClickListener {
                            val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
                            callBack.scope.launch(IO) {
                                evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                            }
                        }
                        tv.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.isSelected = true
                                }
                                MotionEvent.ACTION_UP -> {
                                    view.isSelected = false
                                    val upTime = System.currentTimeMillis()
                                    if (upTime - lastClickTime < 200) {
                                        return@setOnTouchListener true
                                    }
                                    lastClickTime = upTime
                                    val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnTouchListener true
                                    callBack.scope.launch(IO) {
                                        evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    view.isSelected = false
                                }
                            }
                            return@setOnTouchListener true
                        }
                    }

                    Type.text -> {
                        val ti = getFlexboxChildText(flexbox)
                        flexbox.addView(ti)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "center" -> ti.gravity = Gravity.CENTER
                                "flex_end" -> ti.gravity = Gravity.END
                                else -> ti.gravity = Gravity.START
                            }
                            apply(ti)
                        }
                        if (viewName == null) {
                            ti.hint = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            ti.hint = n
                        } else {
                            ti.hint = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    ti.hint = "null"
                                } else {
                                    ti.hint = n
                                }
                            }.onError{ _ ->
                                ti.hint = "err"
                            }
                        }
                        ti.setText(infoMap[title])
                        var actionJob: Job? = null
                        val watcher = object : TextWatcher {
                            var content: String? = null
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                                content = s.toString()
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                val reContent = s.toString()
                                infoMap[title] = reContent
                                if (kind.action != null && reContent != content) {
                                    actionJob?.cancel()
                                    actionJob = callBack.scope.launch(IO) {
                                        delay(600) //防抖
                                        evalButtonClick(kind.action, source, infoMap, title, sourceJsExtensions)
                                        content = reContent
                                    }
                                }
                            }
                        }
                        ti.setTag(R.id.text_watcher, watcher)
                        ti.addTextChangedListener(watcher)
                    }

                    Type.toggle -> {
                        var newName = title
                        var left = true
                        val tv = getFlexboxChild(flexbox)
                        flexbox.addView(tv)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> tv.gravity = Gravity.START
                                "flex_end" -> tv.gravity = Gravity.END
                                "right" -> left = false
                                else -> tv.gravity = Gravity.CENTER
                            }
                            apply(tv)
                        }
                        val chars = kind.chars?.filterNotNull() ?: listOf("chars","is null")
                        val infoV = infoMap[title]
                        var char = if (infoV.isNullOrEmpty()) {
                            (kind.default ?: chars[0]).also {
                                infoMap[title] = it
                            }
                        } else {
                            infoV
                        }
                        if (viewName == null) {
                            tv.text = if (left) char + title else title + char
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            newName = n
                            tv.text = if (left) char + n else n + char
                        } else {
                            tv.text = if (left) char + title else title + char
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    tv.text = char + "null"
                                } else {
                                    newName = n
                                    tv.text = if (left) char + n else n + char
                                }
                            }.onError{ _ ->
                                tv.text = char + "err"
                            }
                        }
                        tv.setOnClickListener {
                            val currentIndex = chars.indexOf(char)
                            val nextIndex = (currentIndex + 1) % chars.size
                            char = chars.getOrNull(nextIndex) ?: ""
                            infoMap[title] = char
                            tv.text = if (left) char + newName else newName + char
                            val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
                            callBack.scope.launch(IO) {
                                evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                            }
                        }
                        tv.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.isSelected = true
                                }
                                MotionEvent.ACTION_UP -> {
                                    view.isSelected = false
                                    val upTime = System.currentTimeMillis()
                                    if (upTime - lastClickTime < 200) {
                                        return@setOnTouchListener true
                                    }
                                    lastClickTime = upTime
                                    val currentIndex = chars.indexOf(char)
                                    val nextIndex = (currentIndex + 1) % chars.size
                                    char = chars.getOrNull(nextIndex) ?: ""
                                    infoMap[title] = char
                                    tv.text = if (left) char + newName else newName + char
                                    val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnTouchListener true
                                    callBack.scope.launch(IO) {
                                        evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    view.isSelected = false
                                }
                            }
                            return@setOnTouchListener true
                        }
                    }

                    Type.select -> {
                        val sl = getFlexboxChildSelect(flexbox)
                        flexbox.addView(sl)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> sl.gravity = Gravity.START
                                "flex_end" -> sl.gravity = Gravity.END
                                else -> sl.gravity = Gravity.CENTER
                            }
                            apply(sl)
                        }
                        val spName = sl.findViewById<AccentTextView>(R.id.sp_name)
                        if (viewName == null) {
                            spName.text = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            spName.text = n
                        } else {
                            spName.text = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    spName.text = "null"
                                } else {
                                    spName.text = n
                                }
                            }.onError{ _ ->
                                spName.text = "err"
                            }
                        }
                        val chars = kind.chars?.filterNotNull() ?: listOf("chars","is null")
                        val adapter = ArrayAdapter(
                            context,
                            R.layout.item_text_common,
                            chars
                        )
                        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                        val selector = sl.findViewById<AppCompatSpinner>(R.id.sp_type)
                        selector.adapter = adapter
                        val infoV = infoMap[title]
                        val char = if (infoV.isNullOrEmpty()) {
                            (kind.default ?: chars[0]).also {
                                infoMap[title] = it
                            }
                        } else {
                            infoV
                        }
                        val i = chars.indexOf(char)
                        selector.setSelectionSafely(i)
                        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            var isInitializing = true
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                if (isInitializing) { //忽略初始化选择
                                    isInitializing = false
                                    return
                                }
                                infoMap[title] = chars[position]
                                if (kind.action != null) {
                                    callBack.scope.launch(IO) {
                                        evalButtonClick(kind.action, source, infoMap, title, sourceJsExtensions)
                                    }
                                }
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun evalUiJs(jsStr: String, source: BookSource?, infoMap: InfoMap): String? {
        val source = source ?: return null
        return try {
            runScriptWithContext {
                source.evalJS(jsStr) {
                    put("infoMap", infoMap)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " exploreUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    private suspend fun evalButtonClick(jsStr: String, source: BaseSource?, infoMap: InfoMap, name: String, java: SourceLoginJsExtensions) {
        val source = source ?: return
        try {
            runScriptWithContext {
                source.evalJS(jsStr) {
                    put("java", java)
                    put("infoMap", infoMap)
                }
            }
        } catch (e: Exception) {
            AppLog.put("ExploreUI Button $name JavaScript error", e)
        }
    }

    @Synchronized
    private fun getFlexboxChild(flexbox: FlexboxLayout): TextView {
        return if (recycler.isEmpty()) {
            ItemFilletTextBinding.inflate(inflater, flexbox, false).root
        } else {
            recycler.removeLastElement()
        }
    }

    @Synchronized
    private fun getFlexboxChildText(flexbox: FlexboxLayout): AutoCompleteTextView {
        return if (textRecycler.isEmpty()) {
            ItemFilletCompleteTextBinding.inflate(inflater, flexbox, false).root
        } else {
            textRecycler.removeLastElement()
        }
    }

    @Synchronized
    private fun getFlexboxChildSelect(flexbox: FlexboxLayout): LinearLayout {
        return if (selectRecycler.isEmpty()) {
            ItemFilletSelectorSingleBinding.inflate(inflater, flexbox, false).root
        } else {
            selectRecycler.removeLastElement()
        }
    }

    @Synchronized
    private fun getFlexboxChildHtml(flexbox: FlexboxLayout): FrameLayout {
        return if (htmlRecycler.isEmpty()) {
            ItemExploreHtmlBinding.inflate(inflater, flexbox, false).root
        } else {
            htmlRecycler.removeLastElement()
        }
    }

    private fun bindHtmlView(
        container: FrameLayout,
        kind: ExploreKind,
        source: BookSource?,
        infoMap: InfoMap,
        title: String,
        sourceJsExtensions: SourceLoginJsExtensions
    ) {
        releaseWebView(container)
        container.removeAllViews()
        resolveHtmlContent(kind, source, infoMap, title).onSuccess { rawContent ->
            val content = rawContent?.trim().orEmpty()
            when {
                content.startsWith("<useweb>") -> bindExploreWebView(container, content, source, infoMap)
                content.startsWith("<usehtml>") -> bindExploreTextView(
                    container,
                    content,
                    source,
                    infoMap,
                    title,
                    sourceJsExtensions
                )
                else -> {
                    container.gone()
                }
            }
        }.onError {
            container.gone()
        }
    }

    private fun resolveHtmlContent(
        kind: ExploreKind,
        source: BookSource?,
        infoMap: InfoMap,
        title: String
    ) = Coroutine.async(callBack.scope, IO) {
        kind.url?.takeIf { it.startsWith("<usehtml>") || it.startsWith("<useweb>") }
            ?: title.takeIf { it.startsWith("<usehtml>") || it.startsWith("<useweb>") }
            ?: kind.viewName?.let { viewName ->
                when {
                    viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'' -> {
                        viewName.substring(1, viewName.length - 1)
                    }
                    else -> evalUiJs(viewName, source, infoMap)
                }
            }
    }

    private fun bindExploreTextView(
        container: FrameLayout,
        content: String,
        source: BookSource?,
        infoMap: InfoMap,
        title: String,
        sourceJsExtensions: SourceLoginJsExtensions
    ) {
        val endIndex = content.lastIndexOf("<")
        if (endIndex < 9) {
            container.gone()
            return
        }
        val html = content.substring(9, endIndex)
        val textView = ScrollTextView(context, null).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            minHeight = 48.dpToPx()
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            textSize = 14F
        }
        val lifecycle = container.findViewTreeLifecycleOwner()?.lifecycle
        val imageGetter = lifecycle?.let {
            GlideImageGetter(
                context,
                textView,
                it,
                context.resources.displayMetrics.widthPixels - 48.dpToPx(),
                source?.bookSourceUrl
            )
        }
        val tagHandler = TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                callBack.scope.launch(IO) {
                    evalButtonClick(click, source, infoMap, "$title $name", sourceJsExtensions)
                }
            }
        })
        textView.setHtml(
            html,
            imageGetter,
            tagHandler,
            imgOnLongClickListener = {
                container.activity?.showDialogFragment(PhotoDialog(it, source?.bookSourceUrl))
            },
            imgOnClickListener = { click ->
                callBack.scope.launch(IO) {
                    evalButtonClick(click, source, infoMap, "$title image", sourceJsExtensions)
                }
            }
        )
        container.addView(textView)
        container.visible()
    }

    private fun bindExploreWebView(
        container: FrameLayout,
        content: String,
        source: BookSource?,
        infoMap: InfoMap
    ) {
        val endIndex = content.lastIndexOf("<")
        if (endIndex < 8) {
            container.gone()
            return
        }
        val useWebHtml = content.substring(8, endIndex)
        val pageKey = buildExploreUseWebPageKey(source, useWebHtml)
        val pageJs = buildExploreUseWebPageInjection(
            pageKey = pageKey,
            initialPage = infoMap["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        )
        val html = wrapExploreUseWebHtml(useWebHtml, source, pageJs)
        val pooledWebView = WebViewPool.acquire(context)
        val webView = pooledWebView.realWebView
        container.setBackgroundColor(context.backgroundColor)
        webView.onResume()
        prepareForInlineContent(webView)
        exploreWebViewHeightCache[pageKey]?.takeIf { it > 1 }?.let { cachedHeight ->
            webView.layoutParams = (webView.layoutParams ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                cachedHeight
            )).also {
                it.width = FrameLayout.LayoutParams.MATCH_PARENT
                it.height = cachedHeight
            }
        }
        installInlineContentRefitOnTouch(webView) {
            container.requestLayout()
        }
        webView.webViewClient = ExploreHtmlWebViewClient(container, source, pageJs, pageKey)
        webView.addJavascriptInterface(WebCacheManager, nameCache)
        source?.let {
            webView.addJavascriptInterface(it as BaseSource, nameSource)
            val webJsExtensions = WebJsExtensions(it, context as? AppCompatActivity, webView)
            webView.addJavascriptInterface(webJsExtensions, nameJava)
        }
        container.addView(webView)
        activeWebViews[container] = pooledWebView
        val baseUrl = source?.bookSourceUrl?.takeIf { it.startsWith("http", true) }
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", baseUrl)
        container.visible()
    }

    private fun releaseWebView(container: FrameLayout) {
        activeWebViews.remove(container)?.let {
            WebViewPool.release(it)
        }
    }

    private fun buildExploreUseWebPageKey(source: BookSource?, html: String): String {
        return buildString {
            append("useweb_page_")
            append(source?.bookSourceUrl ?: "default")
            append('_')
            append(html.hashCode())
        }
    }

    private fun buildExploreUseWebPageInjection(pageKey: String, initialPage: Int): String {
        val safePage = initialPage.coerceAtLeast(1)
        val keyJson = GSON.toJson(pageKey)
        return """
            try{
                const __useWebPageKey = $keyJson;
                const __useWebDefaultPage = $safePage;
                const __readUseWebPage = () => {
                    const cachedPage = parseInt(cache.getFromMemory(__useWebPageKey), 10);
                    return Number.isFinite(cachedPage) && cachedPage > 0 ? cachedPage : __useWebDefaultPage;
                };
                const __writeUseWebPage = value => {
                    const nextPage = parseInt(value, 10);
                    const safeNextPage = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : __useWebDefaultPage;
                    cache.putMemory(__useWebPageKey, String(safeNextPage));
                    return safeNextPage;
                };
                Object.defineProperty(window, 'page', {
                    configurable: true,
                    get() {
                        return __readUseWebPage();
                    },
                    set(value) {
                        __writeUseWebPage(value);
                    }
                });
                if (typeof Element !== 'undefined' && !Object.getOwnPropertyDescriptor(Element.prototype, 'page')) {
                    Object.defineProperty(Element.prototype, 'page', {
                        configurable: true,
                        get() {
                            return window.page;
                        },
                        set(value) {
                            window.page = value;
                        }
                    });
                }
                if (java && typeof java.open === 'function' && !java.__exploreUseWebPageWrapped) {
                    const __rawOpen = java.open.bind(java);
                    java.open = function(name, url, title, origin) {
                        if (name === 'explore' && typeof url === 'string') {
                            const match = url.match(/[?&]page=(\d+)/i);
                            if (match) {
                                __writeUseWebPage(parseInt(match[1], 10) + 1);
                            }
                        }
                        return __rawOpen(name, url, title, origin);
                    };
                    java.__exploreUseWebPageWrapped = true;
                }
            }catch(e){}
        """.trimIndent()
    }

    private fun wrapExploreUseWebHtml(html: String, source: BookSource?, pageJs: String): String {
        val inlineStyle = """
            <style>
            html,body{background:transparent;}
            </style>
        """.trimIndent()
        val injection = buildString {
            val baseJs = buildUseWebInjection(source).trim()
            if (baseJs.isNotEmpty()) {
                append(baseJs)
            }
            if (pageJs.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(pageJs.trim())
            }
        }
        if (injection.isBlank()) {
            return "$inlineStyle\n$html"
        }
        val safeInjection = Regex("(?i)</script>").replace(injection, "<\\\\/script>")
        return "$inlineStyle\n<script>\n$safeInjection\n</script>\n$html"
    }

    @Synchronized
    private fun recyclerFlexbox(flexbox: FlexboxLayout) {
        val children = flexbox.children.toList()
        if (children.isEmpty()) return
        flexbox.removeAllViews()
        callBack.scope.launch {
            for (child in children) {
                when (child) {
                    is AutoCompleteTextView -> {
                        val watcher = child.getTag(R.id.text_watcher) as? TextWatcher
                        if (watcher != null) {
                            child.removeTextChangedListener(watcher)
                        }
                        textRecycler.add(child)
                    }
                    is TextView -> {
                        child.setOnTouchListener(null)
                        child.setOnClickListener(null)
                        recycler.add(child)
                    }
                    is LinearLayout -> {
                        child.findViewById<AppCompatSpinner>(R.id.sp_type)?.onItemSelectedListener = null
                        selectRecycler.add(child)
                    }
                    is FrameLayout -> {
                        releaseWebView(child)
                        child.removeAllViews()
                        htmlRecycler.add(child)
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFindBookBinding) {
        binding.apply {
            llTitle.setOnClickListener {
                val position = holder.bindingAdapterPosition.takeIf { it >= 0 } ?: return@setOnClickListener
                val item = getItem(position) ?: return@setOnClickListener
                val oldExpandedSourceUrl = expandedSourceUrl
                expandedSourceUrl = if (oldExpandedSourceUrl == item.bookSourceUrl) null else item.bookSourceUrl
                oldExpandedSourceUrl?.let { sourceUrl ->
                    findSourcePosition(sourceUrl)?.let {
                        notifyItemChanged(it, false)
                    }
                }
                expandedSourceUrl?.let { sourceUrl ->
                    scrollToSourceUrl = sourceUrl
                    findSourcePosition(sourceUrl)?.let {
                        callBack.scrollTo(it)
                        notifyItemChanged(it, false)
                    }
                }
            }
            llTitle.onLongClick {
                val position = holder.bindingAdapterPosition
                if (position >= 0) {
                    showMenu(binding, position)
                } else {
                    true
                }
            }
        }
    }

    fun compressExplore(): Boolean {
        val sourceUrl = expandedSourceUrl
        return if (sourceUrl == null) {
            false
        } else {
            expandedSourceUrl = null
            findSourcePosition(sourceUrl)?.let {
                notifyItemChanged(it)
            }
            true
        }
    }

    fun refreshExpandedItem() {
        expandedSourceUrl?.let { sourceUrl ->
            findSourcePosition(sourceUrl)?.let {
                notifyItemChanged(it)
            }
        }
    }

    fun onPause() {
        sourceKinds.clear()
        activeWebViews.keys.toList().forEach(::releaseWebView)
        saveInfoMapJob?.cancel()
        saveInfoMapJob = callBack.scope.launch {
            exploreInfoMapList.snapshot().filter { (_, infoMap) -> infoMap.needSave }.map { (_, infoMap) ->
                launch {
                    infoMap.saveNow()
                }
            }.joinAll()
        }
    }

    private fun refreshExplore(source: BookSourcePart, binding: ItemFindBookBinding) {
        binding.rotateLoading.visible()
        Coroutine.async(callBack.scope) {
            source.clearExploreKindsCache()
            sourceKinds[source.bookSourceUrl] = source.exploreKinds()
        }.onSuccess {
            findSourcePosition(source.bookSourceUrl)?.let {
                notifyItemChanged(it, false)
            }
        }.onFinally {
            binding.rotateLoading.gone()
        }
    }

    private fun showMenu(binding: ItemFindBookBinding, position: Int): Boolean {
        val source = getItem(position) ?: return true
        val popupMenu = PopupMenu(context, binding.llTitle)
        popupMenu.inflate(R.menu.explore_item)
        popupMenu.menu.findItem(R.id.menu_login).isVisible = source.hasLoginUrl
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_edit -> callBack.editSource(source.bookSourceUrl)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_query -> callBack.showKindQueryDialog(source)
                R.id.menu_search -> callBack.searchBook(source)
                R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }

                R.id.menu_refresh -> refreshExplore(source, binding)

                R.id.menu_del -> callBack.deleteSource(source)
            }
            true
        }
        popupMenu.show()
        return true
    }

    interface CallBack {
        val scope: CoroutineScope
        fun scrollTo(pos: Int)
        fun openExplore(sourceUrl: String, title: String, exploreUrl: String?)
        fun editSource(sourceUrl: String)
        fun toTop(source: BookSourcePart)
        fun deleteSource(source: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
        fun showKindQueryDialog(source: BookSourcePart)
    }

    private fun findSourcePosition(sourceUrl: String): Int? {
        for (index in 0 until itemCount) {
            val item = getItem(index) ?: continue
            if (item.bookSourceUrl == sourceUrl) {
                return index
            }
        }
        return null
    }

    private inner class ExploreHtmlWebViewClient(
        private val container: FrameLayout,
        private val source: BaseSource?,
        private val pageJs: String,
        private val pageKey: String
    ) : WebViewClient() {
        private val jsStr = buildString {
            append(buildUseWebInjection(source))
            if (pageJs.isNotBlank()) {
                append('\n')
                append(pageJs)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            request?.let {
                val uri = it.url
                return when (uri.scheme) {
                    "http", "https" -> false
                    "legado", "yuedu" -> {
                        context.startActivity<OnLineImportActivity> {
                            data = uri
                        }
                        true
                    }

                    else -> {
                        container.activity?.findViewById<View>(android.R.id.content)
                            ?.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                                container.activity?.openUrl(uri)
                            }
                        true
                    }
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.evaluateJavascript(jsStr, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.let {
                scheduleInlineContentFit(it, afterLayout = {
                    val height = it.layoutParams?.height ?: 0
                    if (height > 1) {
                        exploreWebViewHeightCache.put(pageKey, height)
                    }
                    container.requestLayout()
                })
            }
        }
    }
}
