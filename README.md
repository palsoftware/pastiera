[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)
# Pastiera

A physical keyboard input method for Android, made for devices like the Unihertz Titan 2. Built as a hobby project to make typing on physical keyboards more efficient and enjoyable.

## Features

### Long Press for Special Characters or Capital Letters

Long-press any key to get its Alt+key combination. For example:
- Long-press Q → 0
- Long-press A → @
- Long-press Z → !

Alternatively, the long press can be set to type capital letters. For example:
- Long-press a → A

The long-press duration is configurable in settings (default 500ms).
### Modifier Keys

**Shift, Ctrl, and Alt** work in two modes:

- **One-shot**: Tap once to activate for the next key only (so you don't need to press 2 keys to do a combination)
- **Latch**: Double-tap to keep it active until you tap it again

**Caps Lock**: Double-tap Shift to enable/disable caps lock. Tap Shift once while caps lock is on to turn it off.

### Keyboard Shortcuts

Standard shortcuts work as you'd expect:
- `Ctrl+C` / `Ctrl+X` / `Ctrl+V` - Copy, cut, paste
- `Ctrl+A` - Select all
- `Ctrl+Z` - Undo
- `Ctrl+Backspace` - Delete last word
- `Ctrl+E, S, D, F or I, J, K, L` - Navigate (E=Up, D=Down, S=Left, F=Right)
- `Ctrl+W` / `Ctrl+R` - Expand text selection left/right
- `Ctrl+T` - Tab
- `Ctrl+Y` / `Ctrl+H` - Page up/down
- `Ctrl+Q` - Escape

### Navigation Mode

Double-tap Ctrl when not in a text field to enter navigation mode. This lets you use ESDF or IJKL keys to navigate around the UI, T to tab and other commands when there's no text input active. Press Back or Ctrl again to exit.

### Launcher Shortcuts (Experimental)

When you're on the home screen (launcher) and not in a text field, you can assign keys to launch apps. Press any unassigned key to open a dialog where you can select which app to launch with that key. This feature is experimental and can be enabled/disabled in settings.

### Character Variations

After typing a letter, the status bar shows available accent variations (à, é, ñ, ç, etc.). Tap any variation to replace the character.

### SYM Key for Emojis and other characters

Press the SYM key (if your keyboard has one) to activate emoji mode. Then press any letter key to insert its mapped emoji. You can customize which emoji each key produces in the settings.
Press again the SYM key to enter the second page of the SYM layout, reserved to special characters (you can customize this layout too)


### Voice Input

A microphone button appears in the status bar when typing. Tap it to launch Google Voice Typing for speech-to-text input.

### Auto-Capitalization

Automatically capitalizes the first letter when you start typing in an empty text field. Can be disabled in settings. (Automatically disabled in password fields)

### Auto-Correction

Built-in auto-correction only for punctuation and similar (for example im → I'm) with support for multiple languages. You can add custom languages and corrections in the settings, and easily add your own custom words. Dictionaries are stored in an easily customizable JSON format.

### Other Conveniences

- **Double space to period**: Tap space twice to insert a period, space, and capitalize the next letter
- **Swipe to delete**: Swipe left on the keyboard to delete à word (if enabled, works on Unihertz Titan 2)
- **Compact status bar**: The status bar is minimal and takes up very little vertical space

## Installation

1. Build the app or install the APK
2. Open the app and go to Settings → System → Languages & input → Virtual keyboard → Manage keyboards
3. Enable "Pastiera Physical Keyboard"
4. When typing, switch to Pastiera from your keyboard selector

## Configuration

Open the Pastiera app to access settings:

- **Long Press Duration**: Adjust how long you need to hold a key for long-press to activate
- **Auto-Capitalize First Letter**: Toggle automatic capitalization
- **Double Space to Period**: Toggle the double-space feature
- **Swipe to Delete**: Enable/disable swipe-to-delete
- **Auto-Correction Languages**: Configure which languages to use for auto-correction
- **Customize SYM Keyboard**: Change which emoji each key produces when SYM is active
- **Long Press Modifier**: Choose whether long-press simulates Alt+key or Shift+key
- **Launcher Shortcuts**: Enable/disable and configure keyboard shortcuts to launch apps from the home screen

## Requirements

- Android 10 (API 29) or higher
- A device with a physical keyboard (optimized for Unihertz Titan 2, can easily be customized via json map for other devices)

## Building

This is an Android project built with Kotlin and Jetpack Compose. Open it in Android Studio and build as usual.

## License

This is a personal hobby project. Use it as you like, but no guarantees.
Made in Italy :D
