# SymSpell Integration & Personal Dictionary Enhancement - December 1, 2025

## Overview
Implemented a SymSpell-based suggestion/autocorrection system for the Pastiera Android keyboard app, enhanced the personal dictionary to support case-sensitive replacements, and resolved merge conflicts for PR preparation.

## Key Components Implemented

### 1. SymSpell Engine Integration
- **File**: `app/src/main/java/it/palsoftware/pastiera/core/suggestions/SymSpellEngine.kt`
- Uses a 20k word frequency dictionary for fast loading (~200ms)
- Dictionary file: `app/src/main/assets/dictionaries/frequency_dictionary_en_82_765.txt`
- Provides spell suggestions with edit distance calculations
- `isKnownWord()` method to check if a word exists in the dictionary

### 2. SuggestionController
- **File**: `app/src/main/java/it/palsoftware/pastiera/core/suggestions/SuggestionController.kt`
- Coordinates between SymSpellEngine, PersonalDictionary, and CurrentWordTracker
- Handles autocorrection on space/enter with configurable max edit distance
- Supports undo via backspace (stores `lastCorrection` state)
- Tracks `ignoredWords` - words user has rejected via backspace undo
- Special case: "i" → "I" autocorrection

### 3. Personal Dictionary Enhancement
- **File**: `app/src/main/java/it/palsoftware/pastiera/core/suggestions/PersonalDictionary.kt`
- Rewrote to support case-sensitive word replacements
- Data model:
  ```kotlin
  data class PersonalEntry(
      val word: String,        // The word to match (case-insensitive lookup)
      val replacement: String? // Optional replacement (case-sensitive output)
  )
  ```
- Storage format: `word\treplacement` (tab-separated, one per line)
- Uses `StateFlow` for reactive UI updates
- Singleton pattern via `getInstance(context)`
- Priority over SymSpell - personal dictionary entries are checked first

### 4. User Dictionary UI
- **File**: `app/src/main/java/it/palsoftware/pastiera/AutoCorrectionCategoryScreen.kt`
- Two input fields: "Word to match" and "Replacement (optional)"
- `KeyboardOptions(capitalization = KeyboardCapitalization.None)` to disable auto-capitalize
- Entries display as "word → replacement" or with "Won't be autocorrected" subtitle
- Delete functionality for each entry

### 5. String Resources Added
- **File**: `app/src/main/res/values/strings.xml`
  ```xml
  <string name="user_dict_add_hint">Word to match</string>
  <string name="user_dict_replacement_hint">Replacement (optional)</string>
  <string name="user_dict_replacement_placeholder">Leave empty to skip correction</string>
  <string name="user_dict_no_correction">Won\'t be autocorrected</string>
  ```

## Settings Integration
- **File**: `app/src/main/java/it/palsoftware/pastiera/SettingsManager.kt`
- `KEY_AUTO_REPLACE_ON_SPACE_ENTER` - enables/disables autocorrect on space/enter
- `KEY_MAX_AUTO_REPLACE_DISTANCE` - max edit distance for autocorrect (0-3, default 1)
- `KEY_SUGGESTIONS_ENABLED` - show suggestion bar
- `KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED` - master toggle for new suggestion system

## Autocorrection Flow
1. User types a word
2. On space/enter (`onBoundaryKey`):
   - Check if word is in `ignoredWords` (user rejected) → skip
   - Check PersonalDictionary first:
     - If entry has replacement → apply it (case-sensitive)
     - If entry exists with no replacement → skip autocorrect
   - Special case "i" → "I"
   - Query SymSpell for suggestions
   - If top suggestion has `distance <= maxAutoReplaceDistance` and word is not known → replace
3. Store correction in `lastCorrection` for undo
4. On backspace after correction → undo and add to `ignoredWords`

## Merge Resolution for PR
After merging from upstream (`palsoftware/pastiera`), fixed these compilation errors:

### SuggestionController.kt
- Removed references to `autoReplaceController` (upstream's approach)
- Removed references to `dictionaryRepository` - use `symSpellEngine` directly

### InputEventRouter.kt
- Removed call to `sc.handleBackspaceUndo()` which doesn't exist
- The existing `onBackspace()` method handles undo functionality

### PhysicalKeyboardInputMethodService.kt
- Replaced `suggestionController.refreshUserDictionary()` with `onContextReset()`
- Replaced `suggestionController.updateLocale()` with `onContextReset()` (locale-based dictionary switching not implemented)

### SettingsManager.kt
- Added missing constant: `DEFAULT_MAX_AUTO_REPLACE_DISTANCE = 1`

## Files Modified/Created
- `app/src/main/java/it/palsoftware/pastiera/core/suggestions/SuggestionController.kt`
- `app/src/main/java/it/palsoftware/pastiera/core/suggestions/SymSpellEngine.kt`
- `app/src/main/java/it/palsoftware/pastiera/core/suggestions/PersonalDictionary.kt`
- `app/src/main/java/it/palsoftware/pastiera/core/suggestions/CurrentWordTracker.kt`
- `app/src/main/java/it/palsoftware/pastiera/core/suggestions/SuggestionSettings.kt`
- `app/src/main/java/it/palsoftware/pastiera/core/suggestions/SuggestionResult.kt`
- `app/src/main/java/it/palsoftware/pastiera/AutoCorrectionCategoryScreen.kt`
- `app/src/main/java/it/palsoftware/pastiera/SettingsManager.kt`
- `app/src/main/java/it/palsoftware/pastiera/inputmethod/InputEventRouter.kt`
- `app/src/main/java/it/palsoftware/pastiera/inputmethod/PhysicalKeyboardInputMethodService.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/assets/dictionaries/frequency_dictionary_en_82_765.txt`

## Build Status
- Build successful as of commit after merge fixes
- Build number: 1211
- Only deprecation warnings (pre-existing)

## Branch Information
- Working branch: `jmitch`
- Main branch: `main`
- Upstream: `palsoftware/pastiera`

## Notes
- The 20k dictionary was chosen for fast loading (~200ms vs several seconds for larger dictionaries)
- Personal dictionary takes priority over SymSpell to allow user overrides
- Case-insensitive matching with case-sensitive replacement output (e.g., "ngl" → "NGL")
- Haptic feedback triggers on autocorrection via `NotificationHelper.triggerHapticFeedback()`
