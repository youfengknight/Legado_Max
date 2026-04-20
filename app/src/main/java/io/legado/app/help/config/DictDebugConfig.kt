package io.legado.app.help.config

import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import splitties.init.appCtx

object DictDebugConfig {
    private const val PREFS_NAME = "DictDebugConfig"
    private const val KEY_SEARCH_HISTORY = "searchHistory"
    private const val MAX_HISTORY_SIZE = 10

    private val sp = appCtx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    fun getSearchHistory(): List<String> {
        val historyStr = sp.getString(KEY_SEARCH_HISTORY, "") ?: ""
        return if (historyStr.isBlank()) {
            emptyList()
        } else {
            historyStr.split("|")
        }
    }

    fun addSearchHistory(word: String) {
        if (word.isBlank()) return
        val history = getSearchHistory().toMutableList()
        history.remove(word)
        history.add(0, word)
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        sp.edit {
            putString(KEY_SEARCH_HISTORY, history.joinToString("|"))
        }
    }

    fun clearSearchHistory() {
        sp.edit {
            remove(KEY_SEARCH_HISTORY)
        }
    }
}