#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Truncate dictionary JSON files to keep only the top N most frequent words.

Usage examples:
    python scripts/truncate_dict.py --input app/src/main/assets/common/dictionaries/it_base.json \
        --output app/src/main/assets/common/dictionaries/it_base.json \
        --max_words 20000
"""

import argparse
import json
import os


def truncate_dictionary(input_path: str, max_words: int):
    """
    Load dictionary JSON, sort by frequency (descending), and keep top N words.
    
    Args:
        input_path: Path to input JSON file
        max_words: Maximum number of words to keep
        
    Returns:
        List of top N dictionary entries sorted by frequency
    """
    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    if not isinstance(data, list):
        raise ValueError(f"Expected JSON array, got {type(data)}")
    
    print(f"Loaded {len(data)} words from {input_path}")
    
    # Sort by frequency (descending) - ensure proper ordering
    sorted_data = sorted(data, key=lambda x: int(x.get("f", 0)), reverse=True)
    
    # Take top N words
    truncated = sorted_data[:max_words]
    
    min_freq = truncated[-1].get("f", 0) if truncated else 0
    max_freq = truncated[0].get("f", 0) if truncated else 0
    
    print(f"Truncated to {len(truncated)} words")
    print(f"Frequency range: {min_freq} - {max_freq}")
    
    return truncated


def main():
    parser = argparse.ArgumentParser(
        description="Truncate dictionary JSON files to top N most frequent words"
    )
    parser.add_argument("--input", required=True, help="Path to input JSON dictionary")
    parser.add_argument("--output", required=True, help="Path to output JSON dictionary")
    parser.add_argument(
        "--max_words",
        type=int,
        default=20000,
        help="Maximum number of words to keep (default: 20000)"
    )
    args = parser.parse_args()
    
    if not os.path.exists(args.input):
        print(f"ERROR: Input file not found: {args.input}")
        return 1
    
    try:
        truncated = truncate_dictionary(args.input, args.max_words)
        
        # Ensure output directory exists
        os.makedirs(os.path.dirname(args.output), exist_ok=True)
        
        # Write truncated dictionary
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(truncated, f, ensure_ascii=False, indent=2)
        
        print(f"Written truncated dictionary to {args.output}")
        return 0
        
    except Exception as e:
        print(f"ERROR: {e}")
        return 1


if __name__ == "__main__":
    exit(main())


