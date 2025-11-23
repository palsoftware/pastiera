package it.palsoftware.pastiera.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.inputmethod.AltSymManager

class SymLayoutController(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val altSymManager: AltSymManager
) {

    companion object {
        private const val PREF_CURRENT_SYM_PAGE = "current_sym_page"
    }

    private enum class SymPage {
        EMOJI,
        SYMBOLS
    }

    enum class SymKeyResult {
        NOT_HANDLED,
        CONSUME,
        CALL_SUPER
    }

    private var symPage: Int = prefs.getInt(PREF_CURRENT_SYM_PAGE, 0)

    init {
        alignSymPageToConfig(SettingsManager.getSymPagesConfig(context))
    }

    fun currentSymPage(): Int {
        alignSymPageToConfig()
        return symPage
    }

    fun isSymActive(): Boolean = currentSymPage() > 0

    fun toggleSymPage(): Int {
        val config = SettingsManager.getSymPagesConfig(context)
        alignSymPageToConfig(config)
        val pages = buildActivePages(config)
        val cycle = mutableListOf(0)
        cycle.addAll(pages.map { it.toPrefValue() })
        if (cycle.size > 1) {
            val currentIndex = cycle.indexOf(symPage).takeIf { it >= 0 } ?: 0
            val nextIndex = (currentIndex + 1) % cycle.size
            symPage = cycle[nextIndex]
        } else {
            symPage = 0
        }
        persistSymPage()
        return symPage
    }

    fun closeSymPage(): Boolean {
        if (symPage == 0) {
            return false
        }
        symPage = 0
        persistSymPage()
        return true
    }

    fun reset() {
        symPage = 0
        persistSymPage()
    }

    fun restoreSymPageIfNeeded(onStatusBarUpdate: () -> Unit) {
        val restoreSymPage = SettingsManager.getRestoreSymPage(context)
        if (restoreSymPage > 0) {
            val config = SettingsManager.getSymPagesConfig(context)
            val pages = buildActivePages(config)
            val allowedValues = pages.map { it.toPrefValue() }
            symPage = when {
                restoreSymPage in allowedValues -> restoreSymPage
                allowedValues.isNotEmpty() -> allowedValues.first()
                else -> 0
            }
            persistSymPage()
            SettingsManager.clearRestoreSymPage(context)
            Handler(Looper.getMainLooper()).post {
                onStatusBarUpdate()
            }
        }
    }

    fun emojiMapText(): String {
        return if (currentPageType() == SymPage.EMOJI) altSymManager.buildEmojiMapText() else ""
    }

    fun currentSymMappings(): Map<Int, String>? {
        return when (currentPageType()) {
            SymPage.EMOJI -> altSymManager.getSymMappings()
            SymPage.SYMBOLS -> altSymManager.getSymMappings2()
            else -> null
        }
    }

    fun handleKeyWhenActive(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        ctrlLatchActive: Boolean,
        altLatchActive: Boolean,
        updateStatusBar: () -> Unit
    ): SymKeyResult {
        val autoCloseEnabled = SettingsManager.getSymAutoClose(context)
        val page = currentPageType()

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.CALL_SUPER
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (autoCloseEnabled) {
                    closeSymAndUpdate(updateStatusBar)
                    return SymKeyResult.CALL_SUPER
                }
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.NOT_HANDLED
            }
        }

        val symChar = when (page) {
            SymPage.EMOJI -> altSymManager.getSymMappings()[keyCode]
            SymPage.SYMBOLS -> altSymManager.getSymMappings2()[keyCode]
            else -> null
        }

        if (symChar != null && inputConnection != null) {
            inputConnection.commitText(symChar, 1)
            if (autoCloseEnabled) {
                closeSymAndUpdate(updateStatusBar)
            }
            return SymKeyResult.CONSUME
        }

        return SymKeyResult.NOT_HANDLED
    }

    fun handleKeyUp(keyCode: Int, shiftPressed: Boolean): Boolean {
        return altSymManager.handleKeyUp(keyCode, isSymActive(), shiftPressed)
    }

    fun emojiMapTextForLayout(): String = altSymManager.buildEmojiMapText()

    private fun closeSymAndUpdate(updateStatusBar: () -> Unit) {
        if (closeSymPage()) {
            updateStatusBar()
        }
    }

    private fun buildActivePages(config: SymPagesConfig = SettingsManager.getSymPagesConfig(context)): List<SymPage> {
        val pages = mutableListOf<SymPage>()
        if (config.emojiEnabled) {
            pages.add(SymPage.EMOJI)
        }
        if (config.symbolsEnabled) {
            pages.add(SymPage.SYMBOLS)
        }
        if (!config.emojiFirst) {
            pages.reverse()
        }
        return pages
    }

    private fun currentPageType(): SymPage? {
        alignSymPageToConfig()
        return when (symPage) {
            1 -> SymPage.EMOJI
            2 -> SymPage.SYMBOLS
            else -> null
        }
    }

    private fun SymPage.toPrefValue(): Int = when (this) {
        SymPage.EMOJI -> 1
        SymPage.SYMBOLS -> 2
    }

    private fun alignSymPageToConfig(config: SymPagesConfig = SettingsManager.getSymPagesConfig(context)) {
        val allowedValues = buildActivePages(config).map { it.toPrefValue() }
        if (allowedValues.isEmpty()) {
            if (symPage != 0) {
                symPage = 0
                persistSymPage()
            }
            return
        }

        if (symPage == 0) {
            return
        }

        if (symPage !in allowedValues) {
            symPage = allowedValues.first()
            persistSymPage()
        }
    }

    private fun persistSymPage() {
        prefs.edit().putInt(PREF_CURRENT_SYM_PAGE, symPage).apply()
    }

}

