#!/usr/bin/env python3
"""
Fix UTF-8 Character Corruptions in HTML and JS Files
Replaces corrupted multi-byte UTF-8 sequences with correct Unicode characters
"""

import os
import sys
from pathlib import Path

# Replacement mappings (corrupted bytes -> correct character)
REPLACEMENTS = {
    'ΓÇö': '—',     # Em dash
    'ΓÇª': '…',     # Ellipsis  
    'ΓåÆ': '→',     # Arrow
    '≡ƒÄë': '✓',   # Checkmark
    'â€™': "'",     # Apostrophe/single quote
    'â€"': '—',     # Em dash
    'â€¦': '…',     # Ellipsis
    'â†'': '→',     # Rightarrow
    'âš™ï¸': '⚙️',  # Gear/settings emoji
    'âš™': '⚙',     # Gear (simpler)
    'âœ•': '✕',     # X mark
    'â˜ ': '☠',     # Skull
    'Â': '',        # Remove stray non-breaking space byte
}

def fix_file(filepath):
    """Fix UTF-8 corruptions in a single file"""
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        original = content
        replacements_made = 0
        
        # Apply replacements
        for corrupted, correct in REPLACEMENTS.items():
            count = content.count(corrupted)
            if count > 0:
                content = content.replace(corrupted, correct)
                replacements_made += count
        
        # Only write if changes were made
        if content != original:
            with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
                f.write(content)
            return replacements_made
        
        return 0
    except Exception as e:
        print(f"Error processing {filepath}: {e}")
        return 0

def main():
    website_dir = Path('website')
    
    if not website_dir.exists():
        print("Error: website directory not found")
        sys.exit(1)
    
    # Find all HTML and JS files (exclude backups)
    html_files = list(website_dir.rglob('*.html'))
    js_files = list(website_dir.rglob('*.js'))
    
    # Filter out backup files
    html_files = [f for f in html_files if 'backup' not in f.name.lower() and '_old' not in f.name.lower()]
    all_files = html_files + js_files
    
    print(f"=== UTF-8 Character Fix Script ===\n")
    print(f"Found {len(all_files)} files to process:")
    print(f"  HTML files: {len(html_files)}")
    print(f"  JS files: {len(js_files)}\n")
    
    fixed_count = 0
    total_replacements = 0
    
    for filepath in all_files:
        replacements = fix_file(filepath)
        if replacements > 0:
            print(f"✓ Fixed: {filepath.name} ({replacements} replacements)")
            fixed_count += 1
            total_replacements += replacements
    
    print(f"\n=== Fix Complete ===")
    print(f"Files fixed: {fixed_count} / {len(all_files)}")
    print(f"Total replacements: {total_replacements}\n")
    
    if fixed_count > 0:
        print("Fixed files ready to deploy to VPS")

if __name__ == '__main__':
    main()
