# Pastiera Scripts

This directory contains utility scripts for dictionary processing and autocorrection generation.

## 📚 Available Scripts

### 1. generate_autocorrections.py

Generate autocorrection rules from base dictionaries for multiple languages.

**What it does:**
- Converts accented/umlauted words to ASCII equivalents
- German: ä→ae, ö→oe, ü→ue, ß→ss
- French, Spanish, Italian, Polish: Generic accent removal
- Preserves manually defined rules

**Usage:**

```bash
# Generate for all supported languages (de, fr, es, en, it, pl)
python3 scripts/generate_autocorrections.py

# Generate for specific languages only
python3 scripts/generate_autocorrections.py de fr

# Overwrite existing rules (don't preserve manual edits)
python3 scripts/generate_autocorrections.py --no-preserve
```

**Output:**
- Files: `app/src/main/assets/common/autocorrect/auto_corrections_{lang}.json`
- Format: Simple JSON `{"from": "to", ...}`
- Automatically loaded by the app at runtime

**Example Output:**
```
DE:   7911 rules → auto_corrections_de.json
FR:  15449 rules → auto_corrections_fr.json  
ES:  10683 rules → auto_corrections_es.json
EN:    121 rules → auto_corrections_en.json
IT:   1816 rules → auto_corrections_it.json
PL:  15472 rules → auto_corrections_pl.json
```

### 2. preprocess_dictionaries.py

Convert JSON dictionary files into serialized format for faster loading.

**Performance Improvement:**
- **Before (JSON)**: ~800-1300ms to load 50k words
- **After (Serialized)**: ~130-250ms to load 50k words
- **Improvement**: ~75-85% faster (5-8x speedup)

**Usage:**

```bash
python3 scripts/preprocess_dictionaries.py
```

**Output:**
- Files: `app/src/main/assets/common/dictionaries_serialized/*.dict`
- Format: Pre-indexed JSON (normalized index + prefix cache)
- 20-30% smaller than original JSON
- 5-8x faster to load

**When to Re-run:**
- Dictionary JSON files are updated
- New languages are added
- Dictionary structure changes

### 3. convert_dictionaries.py

Convert `.txt` dictionary files to JSON format.

**Usage:**

```bash
python3 scripts/convert_dictionaries.py
```

**Input Format:** `word frequency` (space-separated, one per line)  
**Output Format:** `[{"w": "word", "f": frequency}, ...]`

---

## Legacy Scripts

The following scripts are also available for advanced use cases:
- `backup_truncate_and_convert.py` - Backup and process dictionaries
- `build_symspell_dict.py` - Build SymSpell dictionaries
- `truncate_dict.py` - Truncate dictionaries to specific size

## Performance Improvement

**Before (JSON)**: ~800-1300ms to load 50k words
**After (Serialized)**: ~130-250ms to load 50k words
**Improvement**: ~75-85% faster (5-8x speedup)

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

### 3. convert_dictionaries.py

Convert `.txt` dictionary files to JSON format.

**Usage:**

```bash
python3 scripts/convert_dictionaries.py
```

**Input Format:** `word frequency` (space-separated, one per line)
**Output Format:** `[{"w": "word", "f": frequency}, ...]`

---

## Legacy Scripts

The following scripts are also available for advanced use cases:

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

## Notes

- The serialized format uses Kotlinx Serialization JSON (compact mode)
- Original JSON files are kept as fallback
- User dictionary entries are always loaded dynamically (not pre-processed)

