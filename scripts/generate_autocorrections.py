#!/usr/bin/env python3
"""
Generate autocorrection files for multiple languages from base dictionaries.

This script generates autocorrection rules for common typing patterns:
- German (de): Umlauts (ä→ae, ö→oe, ü→ue, ß→ss)
- French (fr): Accents (é→e, è→e, à→a, ç→c, etc.)
- Spanish (es): Accents (á→a, é→e, í→i, ó→o, ú→u, ñ→n)
- Other languages: Generic accent removal

Output is written to: app/src/main/assets/common/autocorrect/auto_corrections_{lang}.json
"""

import json
import unicodedata
import argparse
import sys
from pathlib import Path
from typing import Dict, Set

def remove_german_umlauts(text: str) -> str:
    """
    Convert German umlauts to ASCII equivalents.
    
    ä → ae, ö → oe, ü → ue, ß → ss
    """
    replacements = {
        'ä': 'ae', 'Ä': 'Ae',
        'ö': 'oe', 'Ö': 'Oe',
        'ü': 'ue', 'Ü': 'Ue',
        'ß': 'ss', 'ẞ': 'SS',
    }
    
    result = text
    for char, replacement in replacements.items():
        result = result.replace(char, replacement)
    
    return result

def remove_accents_generic(text: str) -> str:
    """
    Remove all accents/diacritics using Unicode normalization.
    
    This is the generic approach for languages like French, Spanish, etc.
    """
    # Normalize to NFD (decomposed form)
    nfd = unicodedata.normalize('NFD', text)
    # Remove combining marks (accents)
    return ''.join(char for char in nfd if unicodedata.category(char) != 'Mn')

def generate_language_rules(
    dict_path: Path,
    language_code: str,
    preserve_existing: bool = True
) -> Dict[str, str]:
    """
    Generate autocorrection rules for a specific language.
    
    Args:
        dict_path: Path to the base dictionary JSON file
        language_code: Language code (de, fr, en, es, etc.)
        preserve_existing: If True, preserve manually defined rules
    
    Returns:
        Dictionary of autocorrection rules {from: to}
    """
    
    print(f"\n📖 Processing {language_code.upper()} dictionary...")
    print(f"   Source: {dict_path.name}")
    
    # Load dictionary
    with open(dict_path, 'r', encoding='utf-8') as f:
        words_data = json.load(f)
    
    print(f"   Loaded {len(words_data)} words")
    
    # Load existing rules (if preserve_existing is True)
    existing_rules = {}
    autocorrect_dir = dict_path.parents[1] / 'autocorrect'
    autocorrect_file = autocorrect_dir / f'auto_corrections_{language_code}.json'
    
    if preserve_existing and autocorrect_file.exists():
        try:
            with open(autocorrect_file, 'r', encoding='utf-8') as f:
                existing_rules = json.load(f)
            print(f"   Found {len(existing_rules)} existing manual rules")
        except Exception as e:
            print(f"   Warning: Could not load existing rules: {e}")
    
    # Generate new rules
    new_rules = {}
    
    for entry in words_data:
        original_word = entry.get('w', '')
        if not original_word:
            continue
        
        # Choose transformation based on language
        if language_code == 'de':
            unaccented = remove_german_umlauts(original_word)
        else:
            unaccented = remove_accents_generic(original_word)
        
        # Only add if there's a difference (has accents/umlauts)
        if unaccented != original_word:
            # Don't overwrite existing manual rules
            if unaccented not in existing_rules:
                # For duplicates, keep first occurrence (usually more common)
                if unaccented not in new_rules:
                    new_rules[unaccented] = original_word
    
    # Merge existing + new rules (existing takes precedence)
    all_rules = {**new_rules, **existing_rules}
    
    print(f"   Generated {len(new_rules)} new rules")
    print(f"   Total rules: {len(all_rules)}")
    
    return all_rules

def save_autocorrection_file(
    rules: Dict[str, str],
    output_path: Path
):
    """Save autocorrection rules to JSON file."""
    
    # Sort by key for readability
    sorted_rules = dict(sorted(rules.items()))
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(sorted_rules, f, ensure_ascii=False, indent=2)
    
    file_size_kb = output_path.stat().st_size / 1024
    print(f"   Saved to: {output_path.name} ({file_size_kb:.1f} KB)")

def main():
    """Main function."""
    
    parser = argparse.ArgumentParser(
        description='Generate autocorrection files for multiple languages'
    )
    parser.add_argument(
        'languages',
        nargs='*',
        default=['de', 'fr', 'es', 'en', 'it', 'pl'],
        help='Language codes to process (default: de fr es en it pl)'
    )
    parser.add_argument(
        '--no-preserve',
        action='store_true',
        help='Do not preserve existing manual rules (overwrite)'
    )
    
    args = parser.parse_args()
    
    print("=" * 70)
    print("🌍 Autocorrection Generator")
    print("=" * 70)
    
    # Find project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    
    dict_dir = project_root / 'app/src/main/assets/common/dictionaries'
    autocorrect_dir = project_root / 'app/src/main/assets/common/autocorrect'
    
    if not dict_dir.exists():
        print(f"\n❌ Dictionary directory not found: {dict_dir}")
        sys.exit(1)
    
    autocorrect_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"\n📂 Directories:")
    print(f"   Dictionaries: {dict_dir}")
    print(f"   Autocorrect:  {autocorrect_dir}")
    
    # Process each language
    results = []
    
    for lang_code in args.languages:
        dict_file = dict_dir / f'{lang_code}_base.json'
        output_file = autocorrect_dir / f'auto_corrections_{lang_code}.json'
        
        if not dict_file.exists():
            print(f"\n⚠️  Skipping {lang_code.upper()}: Dictionary not found ({dict_file.name})")
            continue
        
        try:
            rules = generate_language_rules(
                dict_file,
                lang_code,
                preserve_existing=not args.no_preserve
            )
            
            save_autocorrection_file(rules, output_file)
            
            results.append({
                'lang': lang_code,
                'rules': len(rules),
                'file': output_file.name
            })
            
        except Exception as e:
            print(f"\n❌ Error processing {lang_code.upper()}: {e}")
            import traceback
            traceback.print_exc()
    
    # Summary
    print("\n" + "=" * 70)
    print("✅ Generation Complete!")
    print("=" * 70)
    
    if results:
        print(f"\n📊 Summary:")
        for result in results:
            print(f"   {result['lang'].upper():>4}: {result['rules']:>6} rules → {result['file']}")
        
        print(f"\n💡 Usage:")
        print(f"   1. These files are automatically loaded by the app")
        print(f"   2. Users can also import custom rules via Batch-Import")
        print(f"   3. Manual edits in autocorrect/*.json are preserved by default")
    else:
        print("\n⚠️  No files were generated")
    
    print("\n" + "=" * 70)

if __name__ == '__main__':
    main()

