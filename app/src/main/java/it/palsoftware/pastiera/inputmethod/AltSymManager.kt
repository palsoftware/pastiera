package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.variation.VariationRepository
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.DeferredPunctuationSpaceTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Alt/SYM mappings, long press handling and special character insertion.
 */
class AltSymManager(
    private val assets: AssetManager,
    private val prefs: SharedPreferences,
    private val context: Context? = null,
    private val activeLayoutNameProvider: (() -> String?)? = null
) {
    // Callback invoked when an Alt character is inserted after a long press
    var onAltCharInserted: ((Char) -> Unit)? = null
    // Callback invoked when a normal character is confirmed (short press in Alt mode)
    var onNormalCharCommitted: ((String) -> Unit)? = null
    // Callback invoked before mapped punctuation is committed.
    var onBoundaryTextRequested: ((String, InputConnection) -> Boolean)? = null

    companion object {
        private const val TAG = "AltSymManager"
    }

    private fun autoSpacePunctuation(): String {
        return context?.let { SettingsManager.getAutoSpacePunctuation(it) }
            ?: it.palsoftware.pastiera.core.Punctuation.DEFAULT_AUTO_SPACE
    }

    private fun handleBoundaryTextBeforeCommit(
        text: String,
        inputConnection: InputConnection
    ): Boolean {
        if (text.length != 1) return false
        val boundary = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(text[0])
        if (boundary == '\'' || boundary !in it.palsoftware.pastiera.core.Punctuation.BOUNDARY) {
            return false
        }
        return onBoundaryTextRequested?.invoke(boundary.toString(), inputConnection) == true
    }

    private val handler = Handler(Looper.getMainLooper())

    private val altKeyMap = mutableMapOf<Int, String>()
    private val deviceSymKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap2 = mutableMapOf<Int, String>()
    private val symKeyMapUppercase = mutableMapOf<Int, String>()
    private val symKeyMap2Uppercase = mutableMapOf<Int, String>()

    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    private val longPressRunnables = ConcurrentHashMap<Int, Runnable>()
    private val longPressActivated = ConcurrentHashMap<Int, Boolean>()
    private val insertedNormalChars = ConcurrentHashMap<Int, String>()
    private val insertedTextAnchors = ConcurrentHashMap<Int, InsertedTextAnchor>()
    private val keyPressWasShifted = ConcurrentHashMap<Int, Boolean>()

    private var longPressThreshold: Long = 500L

    init {
        altKeyMap.putAll(KeyMappingLoader.loadAltKeyMappings(assets, context))
        context?.let { deviceSymKeyMap.putAll(KeyMappingLoader.loadDeviceSymKeyMappings(assets, it)) }
        symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
        symKeyMap2.putAll(KeyMappingLoader.loadSymKeyMappingsPage2(assets))
        symKeyMapUppercase.putAll(KeyMappingLoader.loadSymKeyMappingsUppercase(assets))
        symKeyMap2Uppercase.putAll(KeyMappingLoader.loadSymKeyMappingsPage2Uppercase(assets))
        reloadLongPressThreshold()
    }

    fun reloadLongPressThreshold() {
        longPressThreshold = prefs.getLong("long_press_threshold", 500L).coerceIn(50L, 1000L)
    }

    fun reloadAltMappings() {
        altKeyMap.clear()
        altKeyMap.putAll(KeyMappingLoader.loadAltKeyMappings(assets, context))
        deviceSymKeyMap.clear()
        context?.let { deviceSymKeyMap.putAll(KeyMappingLoader.loadDeviceSymKeyMappings(assets, it)) }
    }

    fun getAltMappings(): Map<Int, String> = altKeyMap

    fun getDeviceSymMappings(): Map<Int, String> = deviceSymKeyMap

    fun getSymMappings(): Map<Int, String> = symKeyMap
    
    fun getSymMappings2(): Map<Int, String> = symKeyMap2

    fun getSymMappingsUppercase(): Map<Int, String> = symKeyMapUppercase

    fun getSymMappings2Uppercase(): Map<Int, String> = symKeyMap2Uppercase
    
    /**
     * Ricarica le mappature SYM, controllando prima le personalizzazioni.
     */
    fun reloadSymMappings() {
        if (context != null) {
            val customMappings = it.palsoftware.pastiera.SettingsManager.getSymMappings(context)
            if (customMappings.isNotEmpty()) {
                symKeyMap.clear()
                symKeyMap.putAll(customMappings)
                symKeyMapUppercase.clear()
                Log.d(TAG, "Loaded custom SYM mappings: ${customMappings.size} entries")
            } else {
                // Use default mappings from JSON
                symKeyMap.clear()
                symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
                symKeyMapUppercase.clear()
                symKeyMapUppercase.putAll(KeyMappingLoader.loadSymKeyMappingsUppercase(assets))
                Log.d(TAG, "Loaded default SYM mappings")
            }
        }
    }
    
    /**
     * Reloads SYM mappings for page 2, checking for custom mappings first.
     */
    fun reloadSymMappings2() {
        if (context != null) {
            val customMappings = it.palsoftware.pastiera.SettingsManager.getSymMappingsPage2(context)
            if (customMappings.isNotEmpty()) {
                symKeyMap2.clear()
                symKeyMap2.putAll(customMappings)
                symKeyMap2Uppercase.clear()
                Log.d(TAG, "Loaded custom SYM page 2 mappings: ${customMappings.size} entries")
            } else {
                // Use default mappings from JSON
                symKeyMap2.clear()
                symKeyMap2.putAll(KeyMappingLoader.loadSymKeyMappingsPage2(assets))
                symKeyMap2Uppercase.clear()
                symKeyMap2Uppercase.putAll(KeyMappingLoader.loadSymKeyMappingsPage2Uppercase(assets))
                Log.d(TAG, "Loaded default SYM page 2 mappings")
            }
        }
    }

    fun hasAltMapping(keyCode: Int): Boolean = altKeyMap.containsKey(keyCode)

    fun hasSymLongPressMapping(keyCode: Int, shiftPressed: Boolean): Boolean {
        val page = context?.let(SettingsManager::resolveLongPressSymPage) ?: 1
        return if (page == 1) {
            if (shiftPressed && symKeyMapUppercase.containsKey(keyCode)) {
                true
            } else {
                symKeyMap.containsKey(keyCode)
            }
        } else {
            if (shiftPressed && symKeyMap2Uppercase.containsKey(keyCode)) {
                true
            } else {
                symKeyMap2.containsKey(keyCode)
            }
        }
    }

    fun hasPendingPress(keyCode: Int): Boolean = pressedKeys.containsKey(keyCode)

    fun addAltKeyMapping(keyCode: Int, character: String) {
        altKeyMap[keyCode] = character
    }

    fun removeAltKeyMapping(keyCode: Int) {
        altKeyMap.remove(keyCode)
    }

    fun resetTransientState() {
        longPressRunnables.values.forEach { handler.removeCallbacks(it) }
        longPressRunnables.clear()
        pressedKeys.clear()
        longPressActivated.clear()
        insertedNormalChars.clear()
        insertedTextAnchors.clear()
        keyPressWasShifted.clear()
    }

    fun buildEmojiMapText(): String {
        val keyLabels = mapOf(
            KeyEvent.KEYCODE_Q to "Q", KeyEvent.KEYCODE_W to "W", KeyEvent.KEYCODE_E to "E",
            KeyEvent.KEYCODE_R to "R", KeyEvent.KEYCODE_T to "T", KeyEvent.KEYCODE_Y to "Y",
            KeyEvent.KEYCODE_U to "U", KeyEvent.KEYCODE_I to "I", KeyEvent.KEYCODE_O to "O",
            KeyEvent.KEYCODE_P to "P", KeyEvent.KEYCODE_A to "A", KeyEvent.KEYCODE_S to "S",
            KeyEvent.KEYCODE_D to "D", KeyEvent.KEYCODE_F to "F", KeyEvent.KEYCODE_G to "G",
            KeyEvent.KEYCODE_H to "H", KeyEvent.KEYCODE_J to "J", KeyEvent.KEYCODE_K to "K",
            KeyEvent.KEYCODE_L to "L", KeyEvent.KEYCODE_Z to "Z", KeyEvent.KEYCODE_X to "X",
            KeyEvent.KEYCODE_C to "C", KeyEvent.KEYCODE_V to "V", KeyEvent.KEYCODE_B to "B",
            KeyEvent.KEYCODE_N to "N", KeyEvent.KEYCODE_M to "M"
        )

        val rows = mutableListOf<String>()
        val keys = listOf(
            listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P),
            listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L),
            listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M)
        )

        for (row in keys) {
            val rowText = row.joinToString("  ") { keyCode ->
                val label = keyLabels[keyCode] ?: ""
                val emoji = symKeyMap[keyCode] ?: ""
                "$label:$emoji"
            }
            rows.add(rowText)
        }

        return rows.joinToString("\n")
    }

    fun handleKeyWithAltMapping(
        keyCode: Int,
        event: KeyEvent?,
        capsLockEnabled: Boolean,
        inputConnection: InputConnection,
        shiftOneShot: Boolean = false,
        layoutChar: Char? = null // Optional character from keyboard layout
    ): Boolean {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false

        // Use centralized character retrieval from layout manager when key is mapped
        var normalChar = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
        } else {
            // Fallback: use layout character if provided, otherwise fall back to event's unicode character
            if (layoutChar != null) {
                layoutChar.toString()
            } else if (event != null && event.unicodeChar != 0) {
                event.unicodeChar.toChar().toString()
            } else {
                ""
            }
        }

        // For unmapped keys, apply case conversion if needed (fallback only)
        if (normalChar.isNotEmpty() && !LayoutMappingRepository.isMapped(keyCode)) {
            // Gestisci shiftOneShot: se è attivo e il carattere è una lettera, rendilo maiuscolo
            if (shiftOneShot && normalChar.isNotEmpty() && normalChar[0].isLetter()) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed != true) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed == true) {
                normalChar = normalChar.lowercase()
            }
        }

        if (normalChar.isNotEmpty()) {
            if (handleBoundaryTextBeforeCommit(normalChar, inputConnection)) {
                normalChar.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
                return true
            }
            inputConnection.commitText(normalChar, 1)
            insertedNormalChars[keyCode] = normalChar
            insertedTextAnchors.remove(keyCode)
            captureInsertedTextAnchor(inputConnection, normalChar)?.let { anchor ->
                insertedTextAnchors[keyCode] = anchor
            }
            keyPressWasShifted[keyCode] = shiftOneShot || event?.isShiftPressed == true
        }

        // Check if this key should support long press
        val longPressMode = context?.let {
            SettingsManager.getLongPressModifier(it)
        } ?: "alt"

        val shouldScheduleLongPress = when (longPressMode) {
            "variations" -> {
                if (normalChar.isEmpty()) {
                    false
                } else {
                    val variations = context?.let { ctx ->
                        VariationRepository.loadVariations(
                            assets = ctx.assets,
                            context = ctx,
                            activeLayoutName = activeLayoutNameProvider?.invoke()
                        )
                    } ?: emptyMap()
                    variations[normalChar.firstOrNull()]?.isNotEmpty() == true
                }
            }
            "sym", "sym_symbols", "sym_emoji" -> hasSymLongPressMapping(
                keyCode = keyCode,
                shiftPressed = keyPressWasShifted[keyCode] == true
            )
            "shift" -> LayoutMappingRepository.isMapped(keyCode) && normalChar.isNotEmpty()
            else -> altKeyMap.containsKey(keyCode)
        }
        
        if (shouldScheduleLongPress) {
            scheduleLongPress(keyCode, inputConnection)
        }
        
        return true
    }

    fun handleAltCombination(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        mappingsOverride: Map<Int, String>? = null,
        defaultHandler: (Int, KeyEvent?) -> Boolean
    ): Boolean {
        val altChar = (mappingsOverride ?: altKeyMap)[keyCode]
        return if (altChar != null) {
            if (handleBoundaryTextBeforeCommit(altChar, inputConnection)) {
                altChar.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
                return true
            }
            context?.let {
                DeferredPunctuationSpaceTracker.prepareForTextCommit(it, inputConnection, altChar)
            }
            val frenchSpacedPunctuation = altChar.length == 1 &&
                context?.let { SettingsManager.shouldApplyFrenchPunctuationSpacing(it) } == true &&
                it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, altChar[0])
            if (frenchSpacedPunctuation) {
                onAltCharInserted?.invoke(altChar[0])
                return true
            }
            val punctuationSet = autoSpacePunctuation()
            if (altChar.isNotEmpty() && altChar[0] in punctuationSet) {
                val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, altChar)
                if (applied) {
                    Log.d(TAG, "Alt mapping applied with auto-space replacement for '$altChar'")
                    onAltCharInserted?.invoke(altChar[0])
                    return true
                }
            }
            AutoSpaceTracker.clear()
            inputConnection.commitText(altChar, 1)
            if (altChar.isNotEmpty()) {
                onAltCharInserted?.invoke(altChar[0])
            }
            true
        } else {
            defaultHandler(keyCode, event)
        }
    }

    fun handleKeyUp(keyCode: Int, symKeyActive: Boolean, shiftPressed: Boolean = false): Boolean {
        val pressStartTime = pressedKeys.remove(keyCode)
        val wasLongPressActivated = longPressActivated.remove(keyCode) ?: false
        val insertedChar = insertedNormalChars.remove(keyCode)
        insertedTextAnchors.remove(keyCode)
        keyPressWasShifted.remove(keyCode)
        
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }

        // If the long press did NOT trigger and we had inserted a normal char, notify tracking
        if (!wasLongPressActivated && insertedChar != null) {
            onNormalCharCommitted?.invoke(insertedChar)
        }

        return pressStartTime != null && !symKeyActive
    }

    fun cancelPendingLongPress(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    /**
     * Schedules a long-press without committing a new character, reusing the
     * same runnable logic used by handleKeyWithAltMapping. This is used so
     * multi-tap commits can still trigger Alt/Shift long-press behaviour.
     */
    fun scheduleLongPressOnly(
        keyCode: Int,
        inputConnection: InputConnection,
        insertedChar: String
    ) {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false
        insertedNormalChars[keyCode] = insertedChar
        insertedTextAnchors.remove(keyCode)
        captureInsertedTextAnchor(inputConnection, insertedChar)?.let { anchor ->
            insertedTextAnchors[keyCode] = anchor
        }
        keyPressWasShifted[keyCode] = insertedChar.firstOrNull()?.isUpperCase() == true
        scheduleLongPress(keyCode, inputConnection)
    }

    private fun scheduleLongPress(
        keyCode: Int,
        inputConnection: InputConnection
    ) {
        reloadLongPressThreshold()

        val longPressMode = context?.let {
            SettingsManager.getLongPressModifier(it)
        } ?: "alt"

        val runnable = Runnable {
            if (pressedKeys.containsKey(keyCode)) {
                val insertedChar = insertedNormalChars[keyCode]

                when (longPressMode) {
                    "variations" -> {
                        if (!insertedChar.isNullOrEmpty()) {
                            val wasShifted = keyPressWasShifted[keyCode] ?: false
                            val baseChar = insertedChar[0]
                            val lookupChar = if (wasShifted && baseChar.isLowerCase()) {
                                baseChar.uppercaseChar()
                            } else if (!wasShifted && baseChar.isUpperCase()) {
                                baseChar.lowercaseChar()
                            } else {
                                baseChar
                            }

                            val variations = context?.let { ctx ->
                                VariationRepository.loadVariations(
                                    assets = ctx.assets,
                                    context = ctx,
                                    activeLayoutName = activeLayoutNameProvider?.invoke()
                                )[lookupChar]
                            }
                            if (!variations.isNullOrEmpty()) {
                                val firstVariation = variations.first()
                                val replaced = replaceInsertedTextWithVariation(
                                    inputConnection = inputConnection,
                                    expectedText = insertedChar,
                                    anchor = insertedTextAnchors[keyCode],
                                    variation = firstVariation
                                )
                                longPressRunnables.remove(keyCode)
                                if (replaced) {
                                    longPressActivated[keyCode] = true
                                    insertedNormalChars.remove(keyCode)
                                    insertedTextAnchors.remove(keyCode)
                                    keyPressWasShifted.remove(keyCode)
                                    Log.d(TAG, "Long press Variations per keyCode $keyCode -> $firstVariation")
                                    firstVariation.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
                                } else {
                                    Log.d(TAG, "Skipped Variations long press for keyCode $keyCode: original text changed")
                                }
                            }
                        }
                    }

                    "sym", "sym_symbols", "sym_emoji" -> {
                        val page = context?.let(SettingsManager::resolveLongPressSymPage) ?: 1
                        val wasShifted = keyPressWasShifted[keyCode] ?: false
                        val symChar = if (page == 1) {
                            if (wasShifted && symKeyMapUppercase.containsKey(keyCode)) {
                                symKeyMapUppercase[keyCode]
                            } else {
                                symKeyMap[keyCode]
                            }
                        } else {
                            if (wasShifted && symKeyMap2Uppercase.containsKey(keyCode)) {
                                symKeyMap2Uppercase[keyCode]
                            } else {
                                symKeyMap2[keyCode]
                            }
                        }

                        if (!symChar.isNullOrEmpty()) {
                            longPressActivated[keyCode] = true

                            if (!insertedChar.isNullOrEmpty()) {
                                inputConnection.deleteSurroundingText(1, 0)
                            }

                            if (handleBoundaryTextBeforeCommit(symChar, inputConnection)) {
                                onAltCharInserted?.invoke(symChar[0])
                                insertedNormalChars.remove(keyCode)
                                keyPressWasShifted.remove(keyCode)
                                longPressRunnables.remove(keyCode)
                                return@Runnable
                            }

                            val frenchSpacedPunctuation = symChar.length == 1 &&
                                context?.let { SettingsManager.shouldApplyFrenchPunctuationSpacing(it) } == true &&
                                it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, symChar[0])
                            if (frenchSpacedPunctuation) {
                                Log.d(TAG, "Long press Sym mapping applied with French spacing for '$symChar'")
                                onAltCharInserted?.invoke(symChar[0])
                                insertedNormalChars.remove(keyCode)
                                keyPressWasShifted.remove(keyCode)
                                longPressRunnables.remove(keyCode)
                                return@Runnable
                            }

                            val punctuationSet = autoSpacePunctuation()
                            if (symChar[0] in punctuationSet) {
                                val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, symChar)
                                if (applied) {
                                    Log.d(TAG, "Long press Sym mapping applied with auto-space replacement for '$symChar'")
                                    onAltCharInserted?.invoke(symChar[0])
                                    insertedNormalChars.remove(keyCode)
                                    keyPressWasShifted.remove(keyCode)
                                    longPressRunnables.remove(keyCode)
                                    return@Runnable
                                }
                            }

                            AutoSpaceTracker.clear()
                            inputConnection.commitText(symChar, 1)
                            insertedNormalChars.remove(keyCode)
                            keyPressWasShifted.remove(keyCode)
                            longPressRunnables.remove(keyCode)
                            Log.d(TAG, "Long press Sym per keyCode $keyCode -> $symChar")
                            onAltCharInserted?.invoke(symChar[0])
                        }
                    }

                    "shift" -> {
                        // Long press with Shift: get uppercase from layout (always use JSON for mapped keys)
                        if (LayoutMappingRepository.isMapped(keyCode)) {
                            val upperChar = LayoutMappingRepository.getUppercase(keyCode)
                            if (upperChar != null) {
                                longPressActivated[keyCode] = true
                                val upperCharString = upperChar

                                inputConnection.deleteSurroundingText(1, 0)
                                if (!handleBoundaryTextBeforeCommit(upperCharString, inputConnection)) {
                                    inputConnection.commitText(upperCharString, 1)
                                }

                                insertedNormalChars.remove(keyCode)
                                keyPressWasShifted.remove(keyCode)
                                longPressRunnables.remove(keyCode)
                                Log.d(TAG, "Long press Shift per keyCode $keyCode -> $upperCharString")
                                upperChar.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
                            }
                        } else if (insertedChar != null && insertedChar.isNotEmpty() && insertedChar[0].isLetter()) {
                            // Fallback for unmapped keys only: use Kotlin uppercase.
                            longPressActivated[keyCode] = true
                            val upperChar = insertedChar.uppercase()

                            inputConnection.deleteSurroundingText(1, 0)
                            inputConnection.commitText(upperChar, 1)

                            insertedNormalChars.remove(keyCode)
                            keyPressWasShifted.remove(keyCode)
                            longPressRunnables.remove(keyCode)
                            Log.d(TAG, "Long press Shift per keyCode $keyCode -> $upperChar (fallback)")
                            if (upperChar.isNotEmpty()) {
                                onAltCharInserted?.invoke(upperChar[0])
                            }
                        }
                    }

                    else -> {
                        // Long press with Alt: use existing Alt mapping (default).
                        val altChar = altKeyMap[keyCode]

                        if (altChar != null) {
                            longPressActivated[keyCode] = true

                            if (insertedChar != null && insertedChar.isNotEmpty()) {
                                inputConnection.deleteSurroundingText(1, 0)
                            }

                            if (handleBoundaryTextBeforeCommit(altChar, inputConnection)) {
                                altChar.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
                                insertedNormalChars.remove(keyCode)
                                keyPressWasShifted.remove(keyCode)
                                longPressRunnables.remove(keyCode)
                                return@Runnable
                            }

                            val frenchSpacedPunctuation = altChar.length == 1 &&
                                context?.let { SettingsManager.shouldApplyFrenchPunctuationSpacing(it) } == true &&
                                it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, altChar[0])
                            if (frenchSpacedPunctuation) {
                                Log.d(TAG, "Long press Alt mapping applied with French spacing for '$altChar'")
                                onAltCharInserted?.invoke(altChar[0])
                                insertedNormalChars.remove(keyCode)
                                keyPressWasShifted.remove(keyCode)
                                longPressRunnables.remove(keyCode)
                                return@Runnable
                            }

                            val punctuationSet = autoSpacePunctuation()
                            if (altChar.isNotEmpty() && altChar[0] in punctuationSet) {
                                val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, altChar)
                                if (applied) {
                                    Log.d(TAG, "Long press Alt mapping applied with auto-space replacement for '$altChar'")
                                    onAltCharInserted?.invoke(altChar[0])
                                    insertedNormalChars.remove(keyCode)
                                    keyPressWasShifted.remove(keyCode)
                                    longPressRunnables.remove(keyCode)
                                    return@Runnable
                                }
                            }

                            AutoSpaceTracker.clear()
                            inputConnection.commitText(altChar, 1)
                            insertedNormalChars.remove(keyCode)
                            keyPressWasShifted.remove(keyCode)
                            longPressRunnables.remove(keyCode)
                            Log.d(TAG, "Long press Alt per keyCode $keyCode -> $altChar")
                            if (altChar.isNotEmpty()) {
                                onAltCharInserted?.invoke(altChar[0])
                            }
                        }
                    }
                }
            }
        }

        longPressRunnables[keyCode] = runnable
        handler.postDelayed(runnable, longPressThreshold)
    }

    private data class EditorSnapshot(
        val text: String,
        val textStart: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    private data class InsertedTextAnchor(
        val inputConnection: InputConnection,
        val textStart: Int,
        val targetStart: Int,
        val targetEnd: Int,
        val prefixThroughTarget: String,
        val expectedText: String
    )

    private fun captureInsertedTextAnchor(
        inputConnection: InputConnection,
        insertedText: String
    ): InsertedTextAnchor? {
        if (insertedText.isEmpty()) return null

        val snapshot = readEditorSnapshot(inputConnection) ?: return null
        if (snapshot.selectionStart != snapshot.selectionEnd) return null

        val targetEnd = snapshot.selectionStart
        val targetStart = targetEnd - insertedText.length
        val relativeStart = targetStart - snapshot.textStart
        val relativeEnd = targetEnd - snapshot.textStart
        if (relativeStart < 0 || relativeEnd > snapshot.text.length) return null
        if (snapshot.text.substring(relativeStart, relativeEnd) != insertedText) return null

        return InsertedTextAnchor(
            inputConnection = inputConnection,
            textStart = snapshot.textStart,
            targetStart = targetStart,
            targetEnd = targetEnd,
            prefixThroughTarget = snapshot.text.substring(0, relativeEnd),
            expectedText = insertedText
        )
    }

    private fun replaceInsertedTextWithVariation(
        inputConnection: InputConnection,
        expectedText: String,
        anchor: InsertedTextAnchor?,
        variation: String
    ): Boolean {
        if (expectedText.isEmpty() || variation.isEmpty()) return false

        val snapshot = readEditorSnapshot(inputConnection) ?: return false
        val target = when {
            anchor != null -> resolveAnchoredTarget(inputConnection, snapshot, anchor)
            else -> resolveImmediatelyPrecedingTarget(snapshot, expectedText)
        } ?: return false

        val selectionStart = snapshot.selectionStart
        val selectionEnd = snapshot.selectionEnd
        val lengthDelta = variation.length - expectedText.length
        inputConnection.beginBatchEdit()
        inputConnection.finishComposingText()
        val composingRegionSet = inputConnection.setComposingRegion(target.first, target.second)
        if (!composingRegionSet) {
            inputConnection.endBatchEdit()
            return false
        }
        val committed = inputConnection.commitText(variation, 1)
        if (committed) {
            inputConnection.finishComposingText()
            inputConnection.setSelection(
                adjustSelectionAfterReplacement(selectionStart, target, lengthDelta, variation.length),
                adjustSelectionAfterReplacement(selectionEnd, target, lengthDelta, variation.length)
            )
        }
        inputConnection.endBatchEdit()
        return committed
    }

    private fun resolveAnchoredTarget(
        inputConnection: InputConnection,
        snapshot: EditorSnapshot,
        anchor: InsertedTextAnchor
    ): Pair<Int, Int>? {
        if (anchor.inputConnection !== inputConnection) return null
        if (snapshot.selectionStart != snapshot.selectionEnd) return null
        if (snapshot.selectionStart < anchor.targetEnd) return null
        if (snapshot.textStart != anchor.textStart) return null

        val relativeStart = anchor.targetStart - snapshot.textStart
        val relativeEnd = anchor.targetEnd - snapshot.textStart
        if (relativeStart < 0 || relativeEnd > snapshot.text.length) return null
        if (snapshot.text.substring(relativeStart, relativeEnd) != anchor.expectedText) return null
        if (snapshot.text.substring(0, relativeEnd) != anchor.prefixThroughTarget) return null
        return anchor.targetStart to anchor.targetEnd
    }

    private fun resolveImmediatelyPrecedingTarget(
        snapshot: EditorSnapshot,
        expectedText: String
    ): Pair<Int, Int>? {
        if (snapshot.selectionStart != snapshot.selectionEnd) return null
        val targetEnd = snapshot.selectionStart
        val targetStart = targetEnd - expectedText.length
        val relativeStart = targetStart - snapshot.textStart
        val relativeEnd = targetEnd - snapshot.textStart
        if (relativeStart < 0 || relativeEnd > snapshot.text.length) return null
        if (snapshot.text.substring(relativeStart, relativeEnd) != expectedText) return null
        return targetStart to targetEnd
    }

    private fun readEditorSnapshot(inputConnection: InputConnection): EditorSnapshot? {
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val text = extracted.text?.toString() ?: return null
        if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) return null
        return EditorSnapshot(
            text = text,
            textStart = extracted.startOffset,
            selectionStart = extracted.startOffset + extracted.selectionStart,
            selectionEnd = extracted.startOffset + extracted.selectionEnd
        )
    }

    private fun adjustSelectionAfterReplacement(
        position: Int,
        target: Pair<Int, Int>,
        lengthDelta: Int,
        replacementLength: Int
    ): Int {
        return when {
            position <= target.first -> position
            position >= target.second -> position + lengthDelta
            else -> target.first + replacementLength
        }
    }
}
