#!/usr/bin/env python3
"""
Pre-process JSON dictionaries into serialized format for faster loading.
Converts *_base.json files to *_base.dict files (JSON serialized format).
"""

import json
import os
import sys
from pathlib import Path
import unicodedata
import re

def normalize(word, locale='it'):
    """Normalize word: lowercase, remove accents, keep only letters."""
    # Convert to lowercase
    word_lower = word.lower()
    
    # Normalize to NFD (decomposed form)
    normalized = unicodedata.normalize('NFD', word_lower)
    
    # Remove combining marks (accents) and keep only Unicode letters
    only_letters = ''.join(
        c for c in normalized 
        if unicodedata.category(c) == 'Ll' or unicodedata.category(c) == 'Lu' or unicodedata.category(c) == 'Lt' or
           unicodedata.category(c) == 'Lo' or unicodedata.category(c) == 'Lm' or unicodedata.category(c) == 'Mn'
    )
    
    # Remove combining marks (accents) - second pass
    without_accents = ''.join(c for c in only_letters if unicodedata.category(c) != 'Mn')
    
    return without_accents

def process_dictionary(json_file_path, output_dir):
    """Process a single dictionary JSON file."""
    print(f"Processing {json_file_path.name}...")
    
    # Read JSON file
    with open(json_file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"  Loaded {len(data)} entries")
    
    # Extract language from filename (e.g., "it_base.json" -> "it")
    language = json_file_path.stem.replace('_base', '')
    
    # Build indices
    normalized_index = {}
    prefix_cache = {}
    cache_prefix_length = 4
    
    for entry in data:
        # IMPORTANT: Preserve original case (uppercase/lowercase) from JSON
        # e.g., {"w": "Mario", "f": 100} -> word="Mario" (not "mario")
        # normalize() only converts to lowercase for indexing purposes
        word = entry['w']  # Original case preserved
        freq = entry.get('f', 1)
        
        # Normalize word (lowercase for indexing only)
        normalized = normalize(word, language)
        
        # Add to normalized index - word keeps original case
        if normalized not in normalized_index:
            normalized_index[normalized] = []
        normalized_index[normalized].append({
            'word': word,  # Original case preserved (e.g., "Mario", "Roma", "casa")
            'frequency': freq,
            'source': 0  # 0 = MAIN
        })
        
        # Add to prefix cache - word keeps original case
        max_prefix_length = min(len(normalized), cache_prefix_length)
        for length in range(1, max_prefix_length + 1):
            prefix = normalized[:length]
            if prefix not in prefix_cache:
                prefix_cache[prefix] = []
            prefix_cache[prefix].append({
                'word': word,  # Original case preserved
                'frequency': freq,
                'source': 0
            })
    
    # Sort prefix cache by frequency (descending)
    for prefix in prefix_cache:
        prefix_cache[prefix].sort(key=lambda x: x['frequency'], reverse=True)
    
    # Create serializable index
    serializable_index = {
        'normalizedIndex': normalized_index,
        'prefixCache': prefix_cache
    }
    
    # Serialize to JSON (compact format)
    output_file = output_dir / f"{language}_base.dict"
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(serializable_index, f, ensure_ascii=False, separators=(',', ':'))
    
    # Calculate file sizes
    original_size = json_file_path.stat().st_size
    new_size = output_file.stat().st_size
    compression_ratio = (1.0 - new_size / original_size) * 100
    
    print(f"  Created {output_file.name}")
    print(f"  Size: {original_size // 1024}KB -> {new_size // 1024}KB ({compression_ratio:.1f}% reduction)")
    print(f"  Indexes: {len(normalized_index)} normalized, {len(prefix_cache)} prefixes")
    print()

def main():
    """Main function."""
    print("=" * 60)
    print("Dictionary Pre-processing Script (Python)")
    print("=" * 60)
    print()
    
    # Find project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    
    dictionaries_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries"
    output_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries_serialized"
    
    print(f"Project root: {project_root}")
    print(f"Dictionaries dir: {dictionaries_dir}")
    print(f"Output dir: {output_dir}")
    print()
    
    if not dictionaries_dir.exists():
        print(f"ERROR: Dictionaries directory not found: {dictionaries_dir}")
        sys.exit(1)
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Find all *_base.json files
    json_files = list(dictionaries_dir.glob("*_base.json"))
    
    if not json_files:
        print(f"ERROR: No dictionary JSON files found in {dictionaries_dir}")
        sys.exit(1)
    
    print(f"Found {len(json_files)} JSON files")
    print()
    print("Pre-processing dictionaries...")
    print()
    
    # Process each dictionary
    for json_file in sorted(json_files):
        try:
            process_dictionary(json_file, output_dir)
        except Exception as e:
            print(f"  ERROR processing {json_file.name}: {e}")
            import traceback
            traceback.print_exc()
            print()
    
    # List generated files
    generated_files = list(output_dir.glob("*.dict"))
    print("=" * 60)
    print(f"Dictionary preprocessing completed!")
    print(f"Generated {len(generated_files)} .dict files:")
    for f in sorted(generated_files):
        size_kb = f.stat().st_size // 1024
        print(f"  ✓ {f.name} ({size_kb}KB)")
    print("=" * 60)

if __name__ == "__main__":
    main()

