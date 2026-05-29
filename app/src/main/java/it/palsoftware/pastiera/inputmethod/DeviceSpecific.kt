package it.palsoftware.pastiera.inputmethod

import android.os.Build
import android.view.KeyEvent

object DeviceSpecific {
    data class RemappedHardwareEvent(
        val keyCode: Int,
        val event: KeyEvent?
    )

    private enum class KeyboardFamily {
        BLACKBERRY,
        UNIHERTZ,
        MINIMAL,
        UNKNOWN
    }

    private enum class KeyboardModel {
        Q25,
        KEY2,
        TITAN_2_ELITE_QWERTY,
        TITAN_2,
        TITAN_POCKET,
        TITAN_SLIM,
        TITAN_ORIGINAL,
        MINIMAL_PHONE,
        UNKNOWN
    }

    private data class DeviceProfile(
        val family: KeyboardFamily,
        val model: KeyboardModel,
        val physicalLayoutName: String,
        val needsEventRemapping: Boolean
    )

    private var testBuildFingerprintOverride: BuildFingerprint? = null

    // Unihertz scan codes (Titan2)
    private const val SCANCODE_TITAN2_CTRL: Int = 251
    private const val SCANCODE_TITAN2_SYM: Int = 253

    private const val KEYCODE_CTRL: Int = KeyEvent.KEYCODE_CTRL_LEFT
    private const val KEYCODE_SYM: Int = KeyEvent.KEYCODE_SYM
    private const val KEYCODE_Q25_CTRL: Int = KeyEvent.KEYCODE_SHIFT_RIGHT
    private const val KEYCODE_Q25_SYM: Int = KeyEvent.KEYCODE_ALT_RIGHT
    private const val SCANCODE_KEY2_W: Int = 17
    private const val SCANCODE_KEY2_Z: Int = 44
    private const val SCANCODE_KEY2_M: Int = 50

    private const val RELOADABLE_META_MASK: Int =
        KeyEvent.META_SHIFT_MASK or
            KeyEvent.META_ALT_MASK or
            KeyEvent.META_CTRL_MASK or
            KeyEvent.META_SYM_ON

    private const val META_Q25_SHIFT: Int = KeyEvent.META_SHIFT_LEFT_ON
    private const val META_Q25_ALT: Int = KeyEvent.META_ALT_LEFT_ON
    private const val META_Q25_CTRL: Int = KeyEvent.META_SHIFT_RIGHT_ON
    private const val META_Q25_SYM: Int = KeyEvent.META_ALT_RIGHT_ON
    private const val META_Q25_CTRL_OR_SYM: Int = META_Q25_CTRL or META_Q25_SYM

    private const val META_SHIFT: Int = KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON
    private const val META_ALT: Int = KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON
    private const val META_CTRL: Int = KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_ON
    private const val META_SYM: Int = KeyEvent.META_SYM_ON

    private var lastQ25MetaState: Int = 0

    fun needsRemapping(): Boolean = currentDeviceProfile().needsEventRemapping

    fun remapHardwareKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        physicalProfileOverride: String? = null
    ): RemappedHardwareEvent {
        return when (resolveKeyboardModel(physicalProfileOverride)) {
            KeyboardModel.Q25 -> remapQ25KeyEvent(keyCode, event)
            KeyboardModel.KEY2 -> remapKey2KeyEvent(keyCode, event)
            else -> RemappedHardwareEvent(keyCode, event)
        }
    }

    // Backward-compatible API used by existing callers.
    fun remapKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        physicalProfileOverride: String? = null
    ): Pair<Int, KeyEvent?>? {
        val remapped = remapHardwareKeyEvent(keyCode, event, physicalProfileOverride)
        if (remapped.keyCode == keyCode && remapped.event === event) {
            return null
        }
        return remapped.keyCode to remapped.event
    }

    private fun remapQ25KeyEvent(keyCode: Int, event: KeyEvent?): RemappedHardwareEvent {
        if (!shouldRemapQ25Event(keyCode, event)) {
            return RemappedHardwareEvent(keyCode, event)
        }

        val normalizedKeyCode = when (keyCode) {
            KEYCODE_Q25_CTRL -> KEYCODE_CTRL
            KEYCODE_Q25_SYM -> KEYCODE_SYM
            else -> keyCode
        }

        return RemappedHardwareEvent(
            keyCode = normalizedKeyCode,
            event = patchQ25MetaState(event)
        )
    }

    private fun remapKey2KeyEvent(keyCode: Int, event: KeyEvent?): RemappedHardwareEvent {
        val normalizedKeyCode = normalizeKey2KeyCode(keyCode, event?.scanCode ?: -1)
        if (event == null || normalizedKeyCode == keyCode) {
            return RemappedHardwareEvent(normalizedKeyCode, event)
        }

        return RemappedHardwareEvent(
            keyCode = normalizedKeyCode,
            event = KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                normalizedKeyCode,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source
            )
        )
    }

    private fun normalizeKey2KeyCode(keyCode: Int, scanCode: Int): Int {
        return when (scanCode) {
            // Some BBF100-4 / LineageOS builds report these keys with layout-shifted keycodes.
            // Normalize by scan code so physical key behavior stays consistent.
            SCANCODE_KEY2_M -> KeyEvent.KEYCODE_M
            SCANCODE_KEY2_W -> KeyEvent.KEYCODE_W
            SCANCODE_KEY2_Z -> KeyEvent.KEYCODE_Z
            else -> keyCode
        }
    }

    private fun shouldRemapQ25Event(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KEYCODE_Q25_CTRL || keyCode == KEYCODE_Q25_SYM) {
            return true
        }
        if (event == null) {
            return false
        }
        val combinedMetaState = event.metaState or lastQ25MetaState
        return (combinedMetaState and META_Q25_CTRL_OR_SYM) != 0
    }

    private fun patchQ25MetaState(event: KeyEvent?): KeyEvent? {
        if (event == null) {
            return null
        }

        val currentMetaState = event.metaState
        val combinedMetaState = currentMetaState or lastQ25MetaState
        if ((combinedMetaState and META_Q25_CTRL_OR_SYM) == 0) {
            lastQ25MetaState = currentMetaState
            return event
        }
        lastQ25MetaState = currentMetaState

        val normalizedMetaState = rebuildNormalizedMetaState(currentMetaState)
        val normalizedKeyCode = when (event.keyCode) {
            KEYCODE_Q25_CTRL -> KEYCODE_CTRL
            KEYCODE_Q25_SYM -> KEYCODE_SYM
            else -> event.keyCode
        }
        val normalizedScanCode = when (event.keyCode) {
            KEYCODE_Q25_CTRL -> SCANCODE_TITAN2_CTRL
            KEYCODE_Q25_SYM -> SCANCODE_TITAN2_SYM
            else -> event.scanCode
        }

        if (
            normalizedMetaState == currentMetaState &&
            normalizedKeyCode == event.keyCode &&
            normalizedScanCode == event.scanCode
        ) {
            return event
        }

        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            normalizedKeyCode,
            event.repeatCount,
            normalizedMetaState,
            event.deviceId,
            normalizedScanCode,
            event.flags,
            event.source
        )
    }

    private fun rebuildNormalizedMetaState(metaState: Int): Int {
        val mappedShift = if ((metaState and META_Q25_SHIFT) != 0) META_SHIFT else 0
        val mappedCtrl = if ((metaState and META_Q25_CTRL) != 0) META_CTRL else 0
        val mappedAlt = if ((metaState and META_Q25_ALT) != 0) META_ALT else 0
        val mappedSym = if ((metaState and META_Q25_SYM) != 0) META_SYM else 0
        val mappedMetaState = mappedShift or mappedCtrl or mappedAlt or mappedSym
        return (metaState and RELOADABLE_META_MASK.inv()) or mappedMetaState
    }

    private data class BuildFingerprint(
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val board: String,
        val display: String
    ) {
        fun containsAny(vararg tokens: String): Boolean {
            return tokens.any { token ->
                brand.contains(token) ||
                    manufacturer.contains(token) ||
                    model.contains(token) ||
                    device.contains(token) ||
                    product.contains(token) ||
                    board.contains(token) ||
                    display.contains(token)
            }
        }
    }

    private fun resolveDeviceProfile(): DeviceProfile {
        val fp = buildFingerprint()
        if (isQ25(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.BLACKBERRY,
                model = KeyboardModel.Q25,
                physicalLayoutName = "Q25",
                needsEventRemapping = true
            )
        }
        if (isKey2(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.BLACKBERRY,
                model = KeyboardModel.KEY2,
                physicalLayoutName = "key2",
                needsEventRemapping = false
            )
        }
        if (isTitan2EliteQwerty(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.UNIHERTZ,
                model = KeyboardModel.TITAN_2_ELITE_QWERTY,
                physicalLayoutName = "titan2elite_qwerty",
                needsEventRemapping = false
            )
        }
        if (isTitanFamily(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.UNIHERTZ,
                model = resolveTitanModel(fp),
                physicalLayoutName = "titan2",
                needsEventRemapping = false
            )
        }
        if (isMinimalPhone(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.MINIMAL,
                model = KeyboardModel.MINIMAL_PHONE,
                physicalLayoutName = "mp01",
                needsEventRemapping = false
            )
        }
        return DeviceProfile(
            family = KeyboardFamily.UNKNOWN,
            model = KeyboardModel.UNKNOWN,
            physicalLayoutName = "unknown",
            needsEventRemapping = false
        )
    }

    private fun buildFingerprint(): BuildFingerprint {
        testBuildFingerprintOverride?.let { return it }
        return BuildFingerprint(
            brand = Build.BRAND.orEmpty().lowercase(),
            manufacturer = Build.MANUFACTURER.orEmpty().lowercase(),
            model = Build.MODEL.orEmpty().lowercase(),
            device = Build.DEVICE.orEmpty().lowercase(),
            product = Build.PRODUCT.orEmpty().lowercase(),
            board = Build.BOARD.orEmpty().lowercase(),
            display = Build.DISPLAY.orEmpty().lowercase()
        )
    }

    private fun currentDeviceProfile(): DeviceProfile = resolveDeviceProfile()

    private fun resolveKeyboardModel(physicalProfileOverride: String?): KeyboardModel {
        return when (normalizePhysicalProfileOverride(physicalProfileOverride)) {
            "key2" -> KeyboardModel.KEY2
            "q25" -> KeyboardModel.Q25
            "titan2elite_qwerty" -> KeyboardModel.TITAN_2_ELITE_QWERTY
            "titan2" -> KeyboardModel.TITAN_2
            "mp01" -> KeyboardModel.MINIMAL_PHONE
            else -> currentDeviceProfile().model
        }
    }

    private fun normalizePhysicalProfileOverride(physicalProfileOverride: String?): String? {
        val normalized = physicalProfileOverride?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "", "auto" -> null
            "key2", "q25", "titan2", "titan2elite_qwerty", "mp01" -> normalized
            else -> null
        }
    }

    internal fun setBuildFingerprintForTests(
        brand: String,
        manufacturer: String,
        model: String,
        device: String,
        product: String,
        board: String = "",
        display: String = ""
    ) {
        testBuildFingerprintOverride = BuildFingerprint(
            brand = brand.lowercase(),
            manufacturer = manufacturer.lowercase(),
            model = model.lowercase(),
            device = device.lowercase(),
            product = product.lowercase(),
            board = board.lowercase(),
            display = display.lowercase()
        )
    }

    internal fun clearTestOverrides() {
        testBuildFingerprintOverride = null
        lastQ25MetaState = 0
    }

    private fun isQ25(fp: BuildFingerprint): Boolean {
        return fp.containsAny("q25") &&
            (fp.containsAny("zinwa", "blackberry", "q20") || fp.device == "q25" || fp.model == "q25")
    }

    private fun isKey2(fp: BuildFingerprint): Boolean {
        // LineageOS Key2 codenames:
        // - KEY2: athena
        // - KEY2 LE: luna
        if (fp.device == "athena" || fp.device == "luna") {
            return true
        }
        return fp.containsAny("blackberry key2", "key2", "bbf100")
    }

    private fun isMinimalPhone(fp: BuildFingerprint): Boolean {
        return fp.containsAny("minimal_phone") || (fp.containsAny("mp01") && fp.containsAny("along"))
    }

    private fun isTitanFamily(fp: BuildFingerprint): Boolean {
        return fp.containsAny("unihertz", "titan")
    }

    private fun isTitan2EliteQwerty(fp: BuildFingerprint): Boolean {
        val strictTokenMatch = fp.containsAny(
            "titan2elite_qwerty",
            "titan2elite-qwerty",
            "titan2eliteqwerty"
        )
        if (strictTokenMatch) {
            return true
        }

        // Reviewer devices may expose Titan 2-like model/product, but still leak Elite traits
        // via BOARD or DISPLAY. Use these only inside the Unihertz Titan family.
        val looksLikeTitanFamily = fp.containsAny("unihertz", "titan")
        val hasEliteDisplay = fp.display.contains("elite")
        val hasEliteBoard = fp.board.contains("g72")
        return looksLikeTitanFamily && (hasEliteDisplay || hasEliteBoard)
    }

    private fun resolveTitanModel(fp: BuildFingerprint): KeyboardModel {
        return when {
            fp.containsAny("titan pocket", "titan_pocket") -> KeyboardModel.TITAN_POCKET
            fp.containsAny("titan slim", "titan_slim") -> KeyboardModel.TITAN_SLIM
            fp.containsAny("titan 2", "titan2") -> KeyboardModel.TITAN_2
            fp.containsAny("titan") -> KeyboardModel.TITAN_ORIGINAL
            else -> KeyboardModel.UNKNOWN
        }
    }

    fun deviceName(): String {
        return Build.BRAND + " " + Build.MODEL
    }

    fun keyboardName(): String {
        return when (currentDeviceProfile().family) {
            KeyboardFamily.BLACKBERRY -> "Blackberry"
            KeyboardFamily.UNIHERTZ -> "Unihertz"
            KeyboardFamily.MINIMAL -> "Minimal"
            KeyboardFamily.UNKNOWN -> "unknown"
        }
    }

    // convert keycode to digit
    // used by the lockscreen PIN input accessibility service
    fun getDigitFromKeyCode(keyCode: Int): String? {
        // Blackberry keyboards, as well as the the Minimal phone, have a dedicated 0 key
        // and [123456789] are mapped to [WERSDFZXC]
        if (currentDeviceProfile().family == KeyboardFamily.BLACKBERRY ||
            currentDeviceProfile().family == KeyboardFamily.MINIMAL )
        {
            return when (keyCode) {
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_1 -> "1"
                KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_2 -> "2"
                KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_3 -> "3"
                KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_4 -> "4"
                KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_5 -> "5"
                KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_6 -> "6"
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_7 -> "7"
                KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_8 -> "8"
                KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_9 -> "9"
                                    KeyEvent.KEYCODE_0 -> "0"
                else -> null
            }
        }
        // Unihertz keyboards have a different mapping;
        // [1234567890] are mapped to [WERSDFXCVQ]
        if (currentDeviceProfile().family == KeyboardFamily.UNIHERTZ)
        {
            return when (keyCode) {
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_1 -> "1"
                KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_2 -> "2"
                KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_3 -> "3"
                KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_4 -> "4"
                KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_5 -> "5"
                KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_6 -> "6"
                KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_7 -> "7"
                KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_8 -> "8"
                KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_9 -> "9"
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_0 -> "0"
                else -> null
            }
        }
        // otherwise, mapping doesn't apply
        return null
    }
    fun physicalKeyboardName(): String {
        return currentDeviceProfile().physicalLayoutName
    }

    fun isTitan2Device(): Boolean {
        return when (currentDeviceProfile().model) {
            KeyboardModel.TITAN_2,
            KeyboardModel.TITAN_2_ELITE_QWERTY -> true
            else -> false
        }
    }

    fun isMinimalPhoneDevice(physicalProfileOverride: String? = null): Boolean {
        return resolveKeyboardModel(physicalProfileOverride) == KeyboardModel.MINIMAL_PHONE
    }

    fun isPhysicalKeyboardDevice(physicalProfileOverride: String? = null): Boolean {
        return when (resolveKeyboardModel(physicalProfileOverride)) {
            KeyboardModel.Q25,
            KeyboardModel.KEY2,
            KeyboardModel.TITAN_2_ELITE_QWERTY,
            KeyboardModel.TITAN_2,
            KeyboardModel.TITAN_POCKET,
            KeyboardModel.TITAN_SLIM,
            KeyboardModel.TITAN_ORIGINAL,
            KeyboardModel.MINIMAL_PHONE -> true
            KeyboardModel.UNKNOWN -> false
        }
    }
}
