#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Backup dictionaries, truncate them to top N words, and convert to SymSpell format.

This script:
1. Backs up original JSON dictionaries to dict_backup/
2. Truncates dictionaries to top N most frequent words
3. Converts truncated dictionaries to SymSpell .dict format

Usage:
    python scripts/backup_truncate_and_convert.py --max_words 20000
"""

import argparse
import json
import os
import shutil
from pathlib import Path
from collections import defaultdict
import unicodedata


def normalize(word: str, locale: str = "it") -> str:
    """Normalize word (matches Kotlin implementation)."""
    word = word.lower()
    word = unicodedata.normalize("NFD", word)
    word = "".join(ch for ch in word if unicodedata.category(ch) != "Mn")
    word = "".join(ch for ch in word if unicodedata.category(ch).startswith("L"))
    return word


def generate_deletes(term: str, max_distance: int):
    """Generate all delete variants for SymSpell."""
    deletes = set()

    def recurse(current: str, d: int):
        if d == 0:
            return
        for i in range(len(current)):
            deleted = current[:i] + current[i + 1 :]
            if deleted not in deletes:
                deletes.add(deleted)
                recurse(deleted, d - 1)

    recurse(term, max_distance)
    return deletes


def backup_dictionaries(project_root: Path, backup_dir: Path):
    """Backup all dictionary JSON files to backup directory."""
    dictionaries_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries"
    
    if not dictionaries_dir.exists():
        print(f"ERROR: Dictionaries directory not found: {dictionaries_dir}")
        return False
    
    # Create backup directory
    backup_dir.mkdir(parents=True, exist_ok=True)
    
    # Find all *_base.json files
    json_files = list(dictionaries_dir.glob("*_base.json"))
    
    if not json_files:
        print(f"ERROR: No dictionary JSON files found in {dictionaries_dir}")
        return False
    
    print(f"Backing up {len(json_files)} dictionaries to {backup_dir}...")
    
    for json_file in json_files:
        backup_path = backup_dir / json_file.name
        shutil.copy2(json_file, backup_path)
        print(f"  Backed up: {json_file.name}")
    
    print(f"Backup completed!\n")
    return True


def truncate_dictionary(input_path: Path, max_words: int):
    """Load and truncate dictionary to top N words."""
    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    if not isinstance(data, list):
        raise ValueError(f"Expected JSON array, got {type(data)}")
    
    original_count = len(data)
    
    # Sort by frequency (descending)
    sorted_data = sorted(data, key=lambda x: int(x.get("f", 0)), reverse=True)
    
    # Take top N words
    truncated = sorted_data[:max_words]
    
    return truncated, original_count


def convert_to_symspell(data: list, max_edit_distance: int = 2, prefix_length: int = 4):
    """Convert dictionary data to SymSpell format."""
    normalized_index = {}
    prefix_cache = {}
    
    for entry in data:
        # IMPORTANT: Preserve original case (uppercase/lowercase) from JSON
        # e.g., {"w": "Mario", "f": 100} -> word="Mario" (not "mario")
        # normalize() only converts to lowercase for indexing purposes
        w = entry["w"]  # Original case preserved
        f = int(entry.get("f", 1))
        norm = normalize(w)  # lowercase for indexing only
        # Save original word with case preserved for dictionary entry
        normalized_index.setdefault(norm, []).append({"word": w, "frequency": f, "source": 0})
        # prefix cache up to prefix_length chars
        for l in range(1, min(len(norm), prefix_length) + 1):
            prefix_cache.setdefault(norm[:l], []).append({"word": w, "frequency": f, "source": 0})
    
    # Generate deletes
    deletes = defaultdict(set)
    for norm, entries in normalized_index.items():
        freq = max(e["frequency"] for e in entries)
        key = norm[:prefix_length]
        for d in generate_deletes(key, max_edit_distance):
            deletes[d].add(key)
    
    sym_deletes = {k: sorted(v) for k, v in deletes.items()}
    sym_meta = {
        "maxEditDistance": max_edit_distance,
        "prefixLength": prefix_length,
    }
    
    return {
        "normalizedIndex": normalized_index,
        "prefixCache": prefix_cache,
        "symDeletes": sym_deletes,
        "symMeta": sym_meta,
    }


def process_dictionaries(project_root: Path, max_words: int, max_edit_distance: int, prefix_length: int):
    """Process all dictionaries: truncate and convert."""
    dictionaries_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries"
    output_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries_serialized"
    
    json_files = list(dictionaries_dir.glob("*_base.json"))
    
    if not json_files:
        print(f"ERROR: No dictionary JSON files found")
        return False
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Processing {len(json_files)} dictionaries...\n")
    
    for json_file in json_files:
        language = json_file.stem.replace("_base", "")
        print(f"Processing {language}...")
        
        try:
            # Truncate
            truncated, original_count = truncate_dictionary(json_file, max_words)
            print(f"  Truncated from {original_count} to {len(truncated)} words")
            
            # Write truncated JSON back
            with open(json_file, "w", encoding="utf-8") as f:
                json.dump(truncated, f, ensure_ascii=False, indent=2)
            print(f"  Updated {json_file.name}")
            
            # Convert to SymSpell
            symspell_dict = convert_to_symspell(truncated, max_edit_distance, prefix_length)
            
            # Write .dict file
            dict_file = output_dir / f"{language}_base.dict"
            with open(dict_file, "w", encoding="utf-8") as f:
                json.dump(symspell_dict, f, ensure_ascii=False)
            
            print(f"  Created {dict_file.name} with {len(symspell_dict['symDeletes'])} delete buckets")
            print()
            
        except Exception as e:
            print(f"  ERROR processing {language}: {e}\n")
            return False
    
    print("All dictionaries processed successfully!")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Backup, truncate, and convert dictionaries to SymSpell format"
    )
    parser.add_argument(
        "--max_words",
        type=int,
        default=20000,
        help="Maximum number of words to keep (default: 20000)"
    )
    parser.add_argument(
        "--max_edit_distance",
        type=int,
        default=2,
        help="SymSpell max edit distance (default: 2)"
    )
    parser.add_argument(
        "--prefix_length",
        type=int,
        default=4,
        help="SymSpell prefix length (default: 4)"
    )
    parser.add_argument(
        "--project_root",
        type=str,
        default=None,
        help="Project root directory (default: auto-detect from script location)"
    )
    args = parser.parse_args()
    
    # Find project root
    if args.project_root:
        project_root = Path(args.project_root)
    else:
        # Assume script is in scripts/ directory
        script_dir = Path(__file__).parent
        project_root = script_dir.parent
    
    backup_dir = project_root / "dict_backup"
    
    print(f"Project root: {project_root}")
    print(f"Backup directory: {backup_dir}")
    print(f"Max words: {args.max_words}")
    print()
    
    # Step 1: Backup
    if not backup_dictionaries(project_root, backup_dir):
        return 1
    
    # Step 2: Truncate and convert
    if not process_dictionaries(project_root, args.max_words, args.max_edit_distance, args.prefix_length):
        return 1
    
    print("\nDone! Original dictionaries backed up to dict_backup/")
    return 0


if __name__ == "__main__":
    exit(main())


