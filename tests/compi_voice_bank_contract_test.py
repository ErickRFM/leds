#!/usr/bin/env python3
"""Track ID contract: spoken report + environmental cue bank for DFPlayer."""
from pathlib import Path
import runpy

script = Path(__file__).resolve().parents[1] / "scripts/generate-compi-speech.py"
phrases = runpy.run_path(str(script))["PHRASES"]
environment = set(range(1, 10))
numeric = set(range(2100, 2200))
measurements = set(range(2000, 2007)) | numeric | {2200}
assert set(phrases) == environment | measurements, (
    f"Expected 117 stable track IDs; missing={(environment|measurements)-set(phrases)}, "
    f"unexpected={set(phrases)-(environment|measurements)}")
assert len(phrases) == 117
assert all(isinstance(v, str) and v.strip() for v in phrases.values())
print("PASS: 117 environmental and measurement MP3 names are complete")
