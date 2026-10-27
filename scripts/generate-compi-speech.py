#!/usr/bin/env python3
"""Generate the /MP3 voice bank for autonomous ESP32 reporting.

Requires offline espeak and ffmpeg on PATH. Do not overwrite user music.
Run: python scripts/generate-compi-speech.py --output build/compi-voice
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

PHRASES = {
    2000: "La temperatura es de",
    2001: "grados Celsius",
    2002: "La humedad relativa es del",
    2003: "por ciento",
    2004: "El nivel de iluminación es del",
    2005: "coma",
    2006: "menos",
    2200: "cien",
}
PHRASES.update({2100 + i: str(i) for i in range(100)})


def generate(output: Path, force: bool) -> None:
    for command in ("espeak", "ffmpeg"):
        if shutil.which(command) is None:
            raise RuntimeError(f"Se necesita {command} instalado")
    folder = output / "MP3"
    folder.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        wav = Path(temporary) / "voice.wav"
        for track, phrase in sorted(PHRASES.items()):
            destination = folder / f"{track:04d}.mp3"
            if destination.exists() and not force:
                raise FileExistsError(f"{destination} ya existe; respalda antes de usar --force")
            subprocess.run(
                ["espeak", "-v", "es", "-s", "135", "-w", str(wav), phrase],
                check=True, stdout=subprocess.DEVNULL)
            subprocess.run([
                "ffmpeg", "-v", "error", "-y", "-i", str(wav),
                "-codec:a", "libmp3lame", "-b:a", "128k",
                "-ar", "44100", "-ac", "1", "-write_xing", "0",
                "-id3v2_version", "0", str(destination)
            ], check=True)
    (output / "manifest.json").write_text(
        json.dumps({"folder": "/MP3", "tracks": PHRASES},
                   ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Generadas {len(PHRASES)} pistas en {folder}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/compi-voice"))
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    generate(args.output, args.force)
