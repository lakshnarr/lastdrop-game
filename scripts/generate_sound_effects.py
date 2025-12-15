#!/usr/bin/env python3
"""
Sound Effects Generator for Last Drop
Generates all game sound effects using ElevenLabs API
Run once to create the sound library
"""

import requests
import json
import os
import time
from pathlib import Path

# Configuration
API_KEY = "YOUR_API_KEY_HERE"  # Replace with your ElevenLabs API key
OUTPUT_DIR = "../app/src/main/res/raw"

# Sound effects catalog with descriptions
SOUND_EFFECTS = {
    # Dice sounds
    "dice_roll": {
        "text": "wooden dice rolling tumbling on a wooden table",
        "duration": 1.5
    },
    "dice_settle": {
        "text": "dice landing and settling with a soft clack",
        "duration": 0.8
    },
    
    # Tile landing sounds
    "water_splash": {
        "text": "water splashing into a pool with gentle ripples",
        "duration": 1.2
    },
    "metal_clang": {
        "text": "metal coin dropping and clanging on steel surface",
        "duration": 1.0
    },
    "wood_thud": {
        "text": "wooden token landing on board with soft thud",
        "duration": 0.8
    },
    "magic_chime": {
        "text": "magical crystal bell chime with shimmer",
        "duration": 1.5
    },
    
    # Chance card sounds
    "card_flip": {
        "text": "playing card being flipped quickly on table",
        "duration": 0.6
    },
    "thunder_rumble": {
        "text": "distant thunder rumbling in the sky",
        "duration": 2.0
    },
    "coins_jingle": {
        "text": "gold coins jingling and clinking in a leather pouch",
        "duration": 1.5
    },
    "crowd_cheer": {
        "text": "small crowd cheering and applauding happily",
        "duration": 2.0
    },
    "crowd_groan": {
        "text": "disappointed crowd groaning and sighing",
        "duration": 1.8
    },
    "magic_sparkle": {
        "text": "magical sparkles twinkling with fairy dust",
        "duration": 1.2
    },
    
    # Game events
    "victory_fanfare": {
        "text": "triumphant brass fanfare with drums, short celebration",
        "duration": 3.0
    },
    "player_eliminated": {
        "text": "sad trombone descending notes, comedic failure sound",
        "duration": 2.0
    },
    "game_start": {
        "text": "starting bell ring, clear and bright",
        "duration": 1.5
    },
    "undo_whoosh": {
        "text": "quick whoosh rewind sound effect",
        "duration": 0.8
    },
    "button_click": {
        "text": "soft button click, interface sound",
        "duration": 0.3
    },
    "error_buzz": {
        "text": "error buzzer sound, negative feedback",
        "duration": 0.5
    },
    
    # Ambient sounds
    "crowd_murmur": {
        "text": "distant crowd murmuring quietly in background",
        "duration": 5.0
    },
    "wind_soft": {
        "text": "gentle wind blowing softly through desert",
        "duration": 4.0
    },
    "oasis_ambience": {
        "text": "peaceful oasis with gentle water trickling and birds",
        "duration": 5.0
    },
    
    # Special effects
    "quicksand_sink": {
        "text": "sinking into quicksand with squelching sounds",
        "duration": 2.0
    },
    "mirage_shimmer": {
        "text": "heat mirage shimmering with warbling effect",
        "duration": 1.5
    },
    "sandstorm_gust": {
        "text": "sand and wind gusting in desert storm",
        "duration": 2.5
    }
}


def generate_sound_effect(filename, text, duration, api_key):
    """
    Generate a single sound effect using ElevenLabs API
    """
    url = "https://api.elevenlabs.io/v1/sound-generation"
    
    headers = {
        "xi-api-key": api_key,
        "Content-Type": "application/json"
    }
    
    payload = {
        "text": text,
        "duration_seconds": duration,
        "prompt_influence": 0.5
    }
    
    print(f"Generating: {filename}.mp3")
    print(f"  Description: {text}")
    print(f"  Duration: {duration}s")
    
    try:
        response = requests.post(url, headers=headers, json=payload, timeout=30)
        
        if response.status_code == 200:
            return response.content
        else:
            print(f"  ❌ Error: {response.status_code}")
            print(f"  {response.text}")
            return None
            
    except Exception as e:
        print(f"  ❌ Exception: {e}")
        return None


def save_sound_file(filename, audio_data, output_dir):
    """
    Save MP3 data to file
    """
    output_path = Path(output_dir) / f"{filename}.mp3"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'wb') as f:
        f.write(audio_data)
    
    file_size = len(audio_data) / 1024  # KB
    print(f"  ✓ Saved: {output_path} ({file_size:.1f} KB)")


def main():
    print("=" * 60)
    print("Last Drop - Sound Effects Generator")
    print("=" * 60)
    print()
    
    if API_KEY == "YOUR_API_KEY_HERE":
        print("❌ Error: Please set your ElevenLabs API key in the script")
        print("   Edit API_KEY variable at the top of this file")
        return
    
    print(f"Output directory: {OUTPUT_DIR}")
    print(f"Total sounds to generate: {len(SOUND_EFFECTS)}")
    print()
    
    # Confirm before proceeding
    response = input("Continue? (y/n): ")
    if response.lower() != 'y':
        print("Cancelled.")
        return
    
    print()
    print("Starting generation...")
    print()
    
    success_count = 0
    failed_count = 0
    total_size = 0
    
    for filename, config in SOUND_EFFECTS.items():
        audio_data = generate_sound_effect(
            filename,
            config["text"],
            config["duration"],
            API_KEY
        )
        
        if audio_data:
            save_sound_file(filename, audio_data, OUTPUT_DIR)
            success_count += 1
            total_size += len(audio_data)
            print()
        else:
            failed_count += 1
            print()
        
        # Rate limiting - wait between requests
        time.sleep(1)
    
    print("=" * 60)
    print("Generation Complete!")
    print("=" * 60)
    print(f"✓ Success: {success_count}")
    print(f"✗ Failed: {failed_count}")
    print(f"📦 Total size: {total_size / 1024 / 1024:.2f} MB")
    print()
    print(f"Files saved to: {OUTPUT_DIR}")
    print()
    print("Next steps:")
    print("1. Verify sounds play correctly")
    print("2. Build Android app (sounds will be packaged in APK)")
    print("3. Use SoundEffectsPlayer class to play sounds")


if __name__ == "__main__":
    main()
