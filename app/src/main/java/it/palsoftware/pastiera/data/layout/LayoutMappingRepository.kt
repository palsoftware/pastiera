package it.palsoftware.pastiera.data.layout

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent

/**
 * Central repository holding the currently selected layout mapping and
 * providing helper methods to retrieve characters based on modifiers.
 */
object LayoutMappingRepository {
    private const val TAG = "LayoutMappingRepo"

    private val defaultLayout = mapOf(
        KeyEvent.KEYCODE_Q to LayoutMapping('q', 'Q'),
        KeyEvent.KEYCODE_W to LayoutMapping('w', 'W'),
        KeyEvent.KEYCODE_E to LayoutMapping('e', 'E'),
        KeyEvent.KEYCODE_R to LayoutMapping('r', 'R'),
        KeyEvent.KEYCODE_T to LayoutMapping('t', 'T'),
        KeyEvent.KEYCODE_Y to LayoutMapping('y', 'Y'),
        KeyEvent.KEYCODE_U to LayoutMapping('u', 'U'),
        KeyEvent.KEYCODE_I to LayoutMapping('i', 'I'),
        KeyEvent.KEYCODE_O to LayoutMapping('o', 'O'),
        KeyEvent.KEYCODE_P to LayoutMapping('p', 'P'),
        KeyEvent.KEYCODE_A to LayoutMapping('a', 'A'),
        KeyEvent.KEYCODE_S to LayoutMapping('s', 'S'),
        KeyEvent.KEYCODE_D to LayoutMapping('d', 'D'),
        KeyEvent.KEYCODE_F to LayoutMapping('f', 'F'),
        KeyEvent.KEYCODE_G to LayoutMapping('g', 'G'),
        KeyEvent.KEYCODE_H to LayoutMapping('h', 'H'),
        KeyEvent.KEYCODE_J to LayoutMapping('j', 'J'),
        KeyEvent.KEYCODE_K to LayoutMapping('k', 'K'),
        KeyEvent.KEYCODE_L to LayoutMapping('l', 'L'),
        KeyEvent.KEYCODE_Z to LayoutMapping('z', 'Z'),
        KeyEvent.KEYCODE_X to LayoutMapping('x', 'X'),
        KeyEvent.KEYCODE_C to LayoutMapping('c', 'C'),
        KeyEvent.KEYCODE_V to LayoutMapping('v', 'V'),
        KeyEvent.KEYCODE_B to LayoutMapping('b', 'B'),
        KeyEvent.KEYCODE_N to LayoutMapping('n', 'N'),
        KeyEvent.KEYCODE_M to LayoutMapping('m', 'M')
    )

    private var currentLayout: Map<Int, LayoutMapping> = defaultLayout

    fun loadLayout(
        assets: AssetManager,
        layoutName: String,
        context: Context? = null
    ): Map<Int, LayoutMapping> {
        val layout = JsonLayoutLoader.loadLayout(assets, layoutName, context) ?: defaultLayout
        currentLayout = layout
        Log.d(TAG, "Keyboard layout loaded: $layoutName")
        return layout
    }

    fun setLayout(layout: Map<Int, LayoutMapping>) {
        currentLayout = layout
    }

    fun getLayout(): Map<Int, LayoutMapping> = currentLayout

    fun getCharacter(keyCode: Int, isShift: Boolean): Char? {
        val mapping = currentLayout[keyCode] ?: return null
        return if (isShift) mapping.uppercase else mapping.lowercase
    }

    fun getLowercase(keyCode: Int): Char? = currentLayout[keyCode]?.lowercase

    fun getUppercase(keyCode: Int): Char? = currentLayout[keyCode]?.uppercase

    fun getCharacterWithModifiers(
        keyCode: Int,
        isShiftPressed: Boolean,
        capsLockEnabled: Boolean,
        shiftOneShot: Boolean
    ): Char? {
        val mapping = currentLayout[keyCode] ?: return null
        val needsUppercase = when {
            shiftOneShot -> true
            capsLockEnabled && !isShiftPressed -> true
            isShiftPressed -> true
            else -> false
        }
        return if (needsUppercase) mapping.uppercase else mapping.lowercase
    }

    fun getCharacterStringWithModifiers(
        keyCode: Int,
        isShiftPressed: Boolean,
        capsLockEnabled: Boolean,
        shiftOneShot: Boolean
    ): String {
        return getCharacterWithModifiers(keyCode, isShiftPressed, capsLockEnabled, shiftOneShot)?.toString() ?: ""
    }

    fun isMapped(keyCode: Int): Boolean = currentLayout.containsKey(keyCode)

    fun getAvailableLayouts(
        assets: AssetManager,
        context: Context? = null
    ): List<String> = JsonLayoutLoader.getAvailableLayouts(assets, context)
}

