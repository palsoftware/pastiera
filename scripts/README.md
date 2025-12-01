# Dictionary Pre-processing Script

This script converts JSON dictionary files into a serialized format for faster loading at runtime.

## Performance Improvement

- **Before (JSON)**: ~800-1300ms to load 50k words
- **After (Serialized)**: ~130-250ms to load 50k words
- **Improvement**: ~75-85% faster (5-8x speedup)

## How to Run

### Option 1: Using Kotlin Scripting (Recommended)

If you have Kotlin installed:

```bash
kotlinc -script scripts/preprocess-dictionaries.main.kts
```

### Option 2: Manual Execution

1. Navigate to the project root
2. Run the script with Kotlin compiler:

```bash
kotlinc -script scripts/preprocess-dictionaries.main.kts .
```

### Option 3: Android Studio (Easiest)

1. Open `scripts/preprocess-dictionaries.main.kts` in Android Studio
2. Right-click on the file → "Run" (or press `Shift+F10`)
3. Android Studio has Kotlin built-in, no installation needed!

The script will:
- Read all `*_base.json` files from `app/src/main/assets/common/dictionaries/`
- Build normalized index and prefix cache
- Serialize to `.dict` files in `app/src/main/assets/common/dictionaries_serialized/`

## Output

The script generates `.dict` files (JSON serialized format) that are:
- **20-30% smaller** than original JSON
- **Pre-indexed** (no indexing overhead at runtime)
- **5-8x faster** to load

## Fallback Behavior

The app automatically falls back to JSON format if `.dict` files are not found, so the system remains backward compatible.

## When to Re-run

Re-run the script when:
- Dictionary JSON files are updated
- New languages are added
- Dictionary structure changes

## Notes

- The serialized format uses Kotlinx Serialization JSON (compact mode)
- Original JSON files are kept as fallback
- User dictionary entries are always loaded dynamically (not pre-processed)

