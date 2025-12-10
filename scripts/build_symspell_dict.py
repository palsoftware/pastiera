#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Precompute SymSpell deletes and write an extended .dict file.

Input: an existing serialized dictionary JSON (the current .dict) or a base JSON (w/f list).
Output: JSON with fields: normalizedIndex, prefixCache, symDeletes, symMeta.

Usage examples:
    python scripts/build_symspell_dict.py --input app/src/main/assets/common/dictionaries_serialized/it_base.dict \
        --output app/src/main/assets/common/dictionaries_serialized/it_base.dict

    python scripts/build_symspell_dict.py --input app/src/main/assets/common/dictionaries/it_base.json \
        --output app/src/main/assets/common/dictionaries_serialized/it_base.dict
"""

import argparse
import json
import os
import re
import unicodedata
from collections import defaultdict


def normalize(word: str, locale: str = "it") -> str:
    # Align with Kotlin: lowercase, NFD, strip combining marks, keep only letters
    word = word.lower()
    word = unicodedata.normalize("NFD", word)
    # Remove combining marks
    word = "".join(ch for ch in word if unicodedata.category(ch) != "Mn")
    # Keep only letters (unicode)
    word = "".join(ch for ch in word if unicodedata.category(ch).startswith("L"))
    return word


def generate_deletes(term: str, max_distance: int):
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


def load_input(path: str):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, list):
        # base JSON format [{w,f}]
        normalized_index = {}
        prefix_cache = {}
        for entry in data:
            # IMPORTANT: Preserve original case (uppercase/lowercase) from JSON
            # e.g., {"w": "Mario", "f": 100} -> word="Mario" (not "mario")
            # normalize() only converts to lowercase for indexing purposes
            w = entry["w"]
            f = int(entry.get("f", 1))
            norm = normalize(w)  # lowercase for indexing only
            # Save original word with case preserved for dictionary entry
            normalized_index.setdefault(norm, []).append({"word": w, "frequency": f, "source": 0})
            # prefix cache up to 4 chars (matches cachePrefixLength default)
            for l in range(1, min(len(norm), 4) + 1):
                prefix_cache.setdefault(norm[:l], []).append({"word": w, "frequency": f, "source": 0})
        return {"normalizedIndex": normalized_index, "prefixCache": prefix_cache}
    else:
        # assume already in DictionaryIndex shape (case should already be preserved)
        return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Path to base json or existing .dict")
    parser.add_argument("--output", required=True, help="Path to write the extended .dict")
    parser.add_argument("--max_edit_distance", type=int, default=2)
    parser.add_argument("--prefix_length", type=int, default=4)
    args = parser.parse_args()

    data = load_input(args.input)
    normalized_index = data["normalizedIndex"]

    deletes = defaultdict(set)
    for norm, entries in normalized_index.items():
        key = norm[: args.prefix_length]
        for d in generate_deletes(key, args.max_edit_distance):
            # Store full normalized term (matches SymSpell.addWord behavior)
            deletes[d].add(norm)

    sym_deletes = {k: sorted(v) for k, v in deletes.items()}
    sym_meta = {
        "maxEditDistance": args.max_edit_distance,
        "prefixLength": args.prefix_length,
    }

    out = {
        "normalizedIndex": normalized_index,
        "prefixCache": data.get("prefixCache", {}),
        "symDeletes": sym_deletes,
        "symMeta": sym_meta,
    }

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    print(f"Written {args.output} with {len(sym_deletes)} delete buckets")


if __name__ == "__main__":
    main()
