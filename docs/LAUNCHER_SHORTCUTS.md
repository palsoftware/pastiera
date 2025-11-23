# Launcher Shortcuts and Power Shortcuts

This document explains the behavior and implementation of launcher shortcuts and power shortcuts in Pastiera.

## Table of Contents

1. [Launcher Shortcuts](#launcher-shortcuts)
2. [Power Shortcuts](#power-shortcuts)
3. [Nav Mode Integration](#nav-mode-integration)
4. [Architecture](#architecture)
5. [Usage Flows](#usage-flows)

---

## Launcher Shortcuts

### What They Are

**Launcher Shortcuts** allow you to assign an app to an alphabetic key and launch it directly when you're on the launcher (home screen).

### When They Activate

- ✅ **Only when you're on the launcher** (home screen)
- ✅ **Only when there's no active text field** (no editable field)
- ✅ **Only if launcher shortcuts are enabled** in settings
- ✅ **Only if Ctrl is not active** (doesn't interfere with nav mode or other Ctrl shortcuts)

### How They Work

1. User presses an **alphabetic key** (Q-Z, A-M) while on the launcher
2. System checks if a shortcut is assigned to that key
3. If it exists:
   - The app is launched immediately
   - The event is consumed (not handled elsewhere)
4. If it **doesn't exist**:
   - A popup appears to assign an app to that key
   - User can select an app from the list
   - The app is saved and launched immediately

### Shortcut Assignment

Shortcuts are saved in `SharedPreferences` as JSON with the following structure:

```json
{
  "81": {
    "type": "app",
    "packageName": "com.example.app",
    "appName": "Example App"
  }
}
```

Where `81` is the keycode of the key (e.g., `KEYCODE_Q`).

### Supported Shortcut Types

Currently supported:
- **TYPE_APP**: Launches an app via package name

Future (TODO):
- **TYPE_SHORTCUT**: Android native shortcuts support

---

## Power Shortcuts

### What They Are

**Power Shortcuts** allow you to launch the same shortcuts assigned to keys **from any app**, not just from the launcher.

### When They Activate

- ✅ **From any app** (not just launcher)
- ✅ **Only when there's no active text field** (no editable field)
- ✅ **Only if power shortcuts are enabled** in settings
- ✅ **Only if Ctrl is not active** (doesn't interfere with nav mode)

### How They Work

1. User presses **SYM** to activate Power Shortcut mode
   - A toast appears: "Press a key to open the app"
   - The mode stays active for **5 seconds** (automatic timeout)
2. User presses an **alphabetic key** (Q-Z, A-M)
3. System checks if a shortcut is assigned to that key
4. If it exists:
   - The app is launched immediately
   - Power Shortcut mode is deactivated
5. If it **doesn't exist**:
   - The popup appears to assign an app
   - After assignment, Power Shortcut mode is deactivated

### Timeout

Power Shortcut mode has a **5-second timeout**:
- If the user doesn't press any key within 5 seconds, the mode deactivates automatically
- If the user presses SYM again while active, the mode deactivates immediately

### Code Reuse

Power Shortcuts **completely reuse** Launcher Shortcuts logic:
- Same assignments (same storage)
- Same `handleLauncherShortcut()` function
- Same assignment popup
- Same behavior

The only difference is the **activation method**:
- Launcher Shortcuts: direct key press (only on launcher)
- Power Shortcuts: SYM + key (from anywhere)

---

## Nav Mode Integration

### Edge Case: Nav Mode Active

When the user is in **Nav Mode** and presses **SYM** to activate Power Shortcuts:

1. **Nav Mode is disabled** automatically
2. **Power Shortcut mode is activated**
3. User can:
   - Launch an app with a shortcut
   - Select an app to assign
   - Exit (timeout or SYM again)
4. **Nav Mode is re-enabled** automatically when:
   - The shortcut is executed
   - The popup is closed
   - The timeout expires

### Complete Flow

```
1. User in Nav Mode
   ↓
2. User presses SYM
   ↓
3. Nav Mode DISABLED
   ↓
4. Power Shortcut ACTIVATED
   ↓
5. User presses alphabetic key
   ↓
6. App launched / Popup shown
   ↓
7. Power Shortcut DEACTIVATED
   ↓
8. Nav Mode RE-ENABLED
```

### Implementation

Nav Mode management is implemented via **callbacks**:

```kotlin
launcherShortcutController.setNavModeCallbacks(
    exitNavMode = { navModeController.exitNavMode() },
    enterNavMode = { navModeController.enterNavMode() }
)
```

The controller saves Nav Mode state when SYM is pressed and restores it when Power Shortcut ends.

---

## Architecture

### Main Components

#### `LauncherShortcutController`

Centralized controller that manages:
- Detection of installed launchers (with caching)
- App launching
- Power Shortcut state management
- Nav Mode integration
- Shows assignment popup

**Main methods:**
- `isLauncher(packageName)`: Checks if a package is a launcher
- `handleLauncherShortcut(keyCode)`: Handles a launcher shortcut
- `togglePowerShortcutMode(showToast, isNavModeActive)`: Activates/deactivates Power Shortcut
- `handlePowerShortcut(keyCode)`: Handles a power shortcut
- `setNavModeCallbacks(exitNavMode, enterNavMode)`: Configures Nav Mode callbacks

#### `InputEventRouter`

Router that handles event routing when there's no active text field:

**Priority order:**
1. **SYM** → Activates/deactivates Power Shortcut mode
2. **Nav Mode keys** → Handled by NavModeController
3. **Power Shortcuts** → If SYM was pressed and power shortcuts enabled
4. **Launcher Shortcuts** → If on launcher and launcher shortcuts enabled

#### `SettingsManager`

Manages saving and loading shortcuts:

- `getLauncherShortcut(context, keyCode)`: Gets a shortcut
- `setLauncherShortcut(context, keyCode, packageName, appName)`: Saves a shortcut
- `getLauncherShortcutsEnabled(context)`: Checks if enabled
- `getPowerShortcutsEnabled(context)`: Checks if enabled

#### `LauncherShortcutAssignmentActivity`

Activity that shows the popup to assign an app to a key:
- Transparent window (overlay)
- Material 3 bottom sheet
- App search
- Grid of installed apps
- Smart sorting (apps starting with the key letter on top)

---

## Usage Flows

### Scenario 1: Assign a Launcher Shortcut

```
1. User on launcher
2. User presses key "Q" (not assigned)
3. Popup appears with app list
4. User selects "WhatsApp"
5. WhatsApp is saved for key Q
6. WhatsApp is launched immediately
```

### Scenario 2: Use a Launcher Shortcut

```
1. User on launcher
2. User presses key "Q" (assigned to WhatsApp)
3. WhatsApp is launched immediately
```

### Scenario 3: Assign a Power Shortcut

```
1. User in any app
2. User presses SYM
3. Toast: "Press a key to open the app"
4. User presses key "W" (not assigned)
5. Popup appears with app list
6. User selects "Chrome"
7. Chrome is saved for key W
8. Chrome is launched immediately
```

### Scenario 4: Use a Power Shortcut

```
1. User in any app
2. User presses SYM
3. Toast: "Press a key to open the app"
4. User presses key "W" (assigned to Chrome)
5. Chrome is launched immediately
```

### Scenario 5: Power Shortcut with Nav Mode Active

```
1. User in Nav Mode
2. User presses SYM
3. Nav Mode DISABLED
4. Power Shortcut ACTIVATED
5. Toast: "Press a key to open the app"
6. User presses key "E" (assigned to Gmail)
7. Gmail is launched
8. Power Shortcut DEACTIVATED
9. Nav Mode RE-ENABLED
```

### Scenario 6: Power Shortcut Timeout

```
1. User presses SYM
2. Power Shortcut ACTIVATED
3. User doesn't press any key for 5 seconds
4. Power Shortcut DEACTIVATED automatically
5. If Nav Mode was active, it's RE-ENABLED
```

---

## Configuration

### Available Settings

Both features can be enabled/disabled in advanced settings:

- **Launcher Shortcuts**: Enabled/disabled (default: `false`)
- **Power Shortcuts**: Enabled/disabled (default: `false`)

### Storage

Shortcuts are saved in `SharedPreferences` with the key `launcher_shortcuts` as JSON.

Format:
```json
{
  "81": {
    "type": "app",
    "packageName": "com.whatsapp",
    "appName": "WhatsApp"
  },
  "87": {
    "type": "app",
    "packageName": "com.android.chrome",
    "appName": "Chrome"
  }
}
```

---

## Technical Notes

### Launcher Cache

Installed launchers are detected once and cached to avoid repeated PackageManager queries.

### Timeout Handler

Power Shortcut timeout uses `Handler` and `Runnable` to handle automatic reset after 5 seconds.

### Code Reuse

Power Shortcuts completely reuse `handleLauncherShortcut()`, ensuring:
- Same behavior
- Same assignments
- Same popup
- Simplified maintenance

### Separation of Concerns

- `LauncherShortcutController`: Business logic
- `InputEventRouter`: Event routing
- `SettingsManager`: Persistence
- `LauncherShortcutAssignmentActivity`: UI

---

## Future Extensions

### SHORTCUT Type

Currently only `TYPE_APP` is implemented. Future support may include:
- Android native shortcuts
- Custom actions
- Shell commands

### UI Improvements

- Smoother animations
- Preview of assigned apps
- Batch assignment management

