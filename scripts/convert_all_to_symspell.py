#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Convert all dictionary JSON base files to SymSpell .dict format.

This script processes all *_base.json files and converts them to .dict format
using build_symspell_dict.py.

Usage:
    python scripts/convert_all_to_symspell.py
"""

import os
import subprocess
import sys
from pathlib import Path


def find_project_root():
    """Find project root directory."""
    script_dir = Path(__file__).parent
    return script_dir.parent


def main():
    project_root = find_project_root()
    dictionaries_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries"
    output_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries_serialized"
    script_path = project_root / "scripts" / "build_symspell_dict.py"
    
    if not dictionaries_dir.exists():
        print(f"ERROR: Dictionaries directory not found: {dictionaries_dir}")
        return 1
    
    if not script_path.exists():
        print(f"ERROR: Script not found: {script_path}")
        return 1
    
    # Find all *_base.json files
    json_files = list(dictionaries_dir.glob("*_base.json"))
    
    if not json_files:
        print(f"ERROR: No dictionary JSON files found in {dictionaries_dir}")
        return 1
    
    print(f"Found {len(json_files)} dictionaries to process...\n")
    
    success_count = 0
    failed = []
    
    for json_file in sorted(json_files):
        language = json_file.stem.replace("_base", "")
        input_path = json_file
        output_path = output_dir / f"{language}_base.dict"
        
        print(f"Processing {language}...")
        print(f"  Input:  {input_path}")
        print(f"  Output: {output_path}")
        
        try:
            # Run build_symspell_dict.py
            result = subprocess.run(
                [
                    sys.executable,
                    str(script_path),
                    "--input", str(input_path),
                    "--output", str(output_path)
                ],
                cwd=str(project_root),
                capture_output=True,
                text=True,
                encoding="utf-8"
            )
            
            if result.returncode == 0:
                print(f"  ✓ Success")
                print(f"  {result.stdout.strip()}")
                success_count += 1
            else:
                print(f"  ✗ Failed")
                print(f"  Error: {result.stderr}")
                failed.append(language)
            print()
            
        except Exception as e:
            print(f"  ✗ Exception: {e}")
            failed.append(language)
            print()
    
    print("=" * 50)
    print(f"Processed: {success_count}/{len(json_files)} dictionaries")
    if failed:
        print(f"Failed: {', '.join(failed)}")
    else:
        print("All dictionaries converted successfully!")
    
    return 0 if not failed else 1


if __name__ == "__main__":
    exit(main())


