package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.HttpTTS
import io.legado.app.databinding.ActivityTtsDebugBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.source.debug.BookSourceDebugAdapter
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import splitties.views.onClick
import splitties.views.onLongClick

class TtsDebugActivity : VMBaseActivity<ActivityTtsDebugBinding, TtsDebugModel>() {

    override val binding by viewBinding(ActivityTtsDebugBinding::inflate)
    override val viewModel by viewModels<TtsDebugModel>()

    private val adapter by lazy { BookSourceDebugAdapter(this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    private var currentSpeed = 5
    private var currentPitch = 0
    private var currentSpeaker = ""
    private val loginInfo = mutableMapOf<String, String>()
    private var mediaPlayer: android.media.MediaPlayer? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        viewModel.init(intent.getLongExtra("ttsId", 0)) {
            initHelpView()
        }
        viewModel.observe { state, msg ->
            lifecycleScope.launch {
                adapter.addItem(msg)
                if (state == -1 || state == 1) {
                    binding.rotateLoading.gone()
                }
            }
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        binding.rotateLoading.loadingColor = accentColor
    }

    private fun initSearchView() {
        searchView.onActionViewExpanded()
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = "输入测试文本"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                openOrCloseHelp(false)
                startTest(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            openOrCloseHelp(hasFocus)
        }
        openOrCloseHelp(true)
    }

    private fun initHelpView() {
        binding.textTestDefault.onClick {
            searchView.setQuery("这是一段测试文本", true)
        }
        binding.textTestPoem.onClick {
            searchView.setQuery("床前明月光，疑是地上霜。举头望明月，低头思故乡。", true)
        }
        binding.textSpeedNormal.onClick {
            currentSpeed = 5
            binding.textSpeedNormal.text = "语速:$currentSpeed"
            toastOnUi("语速已设为: $currentSpeed")
        }
        binding.textSpeedNormal.onLongClick {
            showInputDialog("设置语速", "请输入语速值 (-10 到 10)", currentSpeed.toString()) { value ->
                val speed = value.toIntOrNull()
                if (speed != null && speed in -10..10) {
                    currentSpeed = speed
                    binding.textSpeedNormal.text = "语速:$currentSpeed"
                    toastOnUi("语速已设为: $currentSpeed")
                } else {
                    toastOnUi("请输入有效的语速值 (-10 到 10)")
                }
            }
        }
        binding.textPitchNormal.onClick {
            currentPitch = 0
            binding.textPitchNormal.text = "音调:$currentPitch"
            toastOnUi("音调已设为: $currentPitch")
        }
        binding.textPitchNormal.onLongClick {
            showInputDialog("设置音调", "请输入音调值 (-100 到 100)", currentPitch.toString()) { value ->
                val pitch = value.toIntOrNull()
                if (pitch != null && pitch in -100..100) {
                    currentPitch = pitch
                    binding.textPitchNormal.text = "音调:$currentPitch"
                    toastOnUi("音调已设为: $currentPitch")
                } else {
                    toastOnUi("请输入有效的音调值 (-100 到 100)")
                }
            }
        }
        binding.textReset.onClick {
            currentSpeed = 5
            currentPitch = 0
            currentSpeaker = ""
            adapter.clearItems()
            toastOnUi("参数已重置")
        }
        binding.textClearLog.onClick {
            adapter.clearItems()
            toastOnUi("日志已清空")
        }

        viewModel.httpTTS?.let { tts ->
            initLoginUi(tts)
            initSpeakers(tts)
        }
    }

    private fun initLoginUi(tts: HttpTTS) {
        tts.getLoginInfoMap().let { loginInfo.putAll(it) }
        tts.loginUi?.let { _ ->
        }
    }

    private fun initSpeakers(tts: HttpTTS) {
        tts.jsLib?.let { jsLib ->
            parseSpeakersFromJsLib(jsLib)?.let { speakers ->
                if (speakers.isNotEmpty()) {
                    currentSpeaker = speakers[0]
                }
            }
        }
    }

    private fun parseSpeakersFromJsLib(jsLib: String): List<String>? {
        return try {
            val speakerMapRegex = Regex("""var\s+speakerMap\s*=\s*\{([^}]+)\}""")
            val match = speakerMapRegex.find(jsLib) ?: return null
            val mapContent = match.groupValues[1]
            val speakerRegex = Regex("""'([^']+)'\s*:""")
            speakerRegex.findAll(mapContent).map { it.groupValues[1] }.toList()
        } catch (e: Exception) {
            null
        }
    }

    private fun openOrCloseHelp(open: Boolean) {
        binding.help.visibility = if (open) View.VISIBLE else View.GONE
    }

    private fun startTest(text: String) {
        if (text.isBlank()) {
            toastOnUi("请输入测试文本")
            return
        }

        adapter.clearItems()
        viewModel.startDebug(text, currentSpeed, currentPitch, currentSpeaker, loginInfo) {
            binding.rotateLoading.visible()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tts_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_play_audio -> {
                viewModel.audioUrl?.let { url ->
                    playAudio(url)
                } ?: toastOnUi("暂无音频URL")
            }
            R.id.menu_view_js_lib -> {
                viewModel.jsLibCode?.let {
                    showDialogFragment(TextDialog("jsLib代码", it))
                } ?: toastOnUi("暂无jsLib代码")
            }
            R.id.menu_view_result -> {
                viewModel.resultData?.let {
                    showDialogFragment(TextDialog("result参数", it))
                } ?: toastOnUi("暂无result数据")
            }
            R.id.menu_open_editor -> {
                val sourceCode = buildString {
                    viewModel.jsLibCode?.let { append("=== jsLib代码 ===\n$it\n\n") }
                    viewModel.resultData?.let { append("=== result参数 ===\n$it") }
                }
                val intent = android.content.Intent(this, CodeEditActivity::class.java).apply {
                    putExtra("text", sourceCode)
                    putExtra("title", "TTS调试源码")
                }
                startActivity(intent)
            }
            R.id.menu_help -> showHelp("httpTTSHelp")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun playAudio(url: String) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()

            val mp = android.media.MediaPlayer()
            mp.setDataSource(url)
            mp.prepareAsync()
            mp.setOnPreparedListener {
                mp.start()
                toastOnUi("开始播放")
            }
            mp.setOnCompletionListener {
                mp.release()
                mediaPlayer = null
                toastOnUi("播放完成")
            }
            mp.setOnErrorListener { _, what, extra ->
                toastOnUi("播放失败: $what, $extra")
                mp.release()
                mediaPlayer = null
                true
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            toastOnUi("播放失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun showInputDialog(title: String, hintStr: String, default: String, onConfirm: (String) -> Unit) {
        val inputView = android.widget.EditText(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 0, 24, 0)
            }
            hint = hintStr
            setText(default)
            selectAll()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(inputView)
            .setPositiveButton("确定") { _, _ ->
                onConfirm(inputView.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

}