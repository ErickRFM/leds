#!/usr/bin/env python3
"""Reproducible, secret-free ESP32 Arduino sketch ZIP for Compi laboratory QA.

The ZIP contains the WHOLE sketch directory, not just esp32_rgb.ino. It
intentionally excludes personalized provisioning files, binaries and NVS data.
"""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SKETCH = ROOT / "firmware" / "esp32_rgb"
PROFILE = ROOT / "config" / "esp32-board.env"
INCLUDED_SUFFIXES = {".ino", ".h", ".hpp", ".cpp", ".c"}
STAMP = (2026, 1, 1, 0, 0, 0)
README = """Compi ESP32 — firmware completo de laboratorio
================================================
1) Extrae el ZIP sin cambiar la estructura de carpetas.
2) Abre esp32_rgb/esp32_rgb.ino en Arduino IDE.
3) ESP32 Dev Module (ESP32 clasico de 4 MB).
4) ESP32 by Espressif Systems core 3.0.7.
5) Tools > Partition Scheme: Minimal SPIFFS (1.9MB APP with OTA).
6) Verifica y luego sube al puerto USB confirmado de TU placa.
7) Monitor Serie: 115200 baud. Claves solo por serial fisico.

RGB: GPIO27 rojo, GPIO25 verde, GPIO26 azul, 220–330 ohm en cada
canal. COMMON_ANODE=true: confirma polaridad y cableado antes de cargar.

AVISO: BLE V1 NO autentica propietario. SOLO laboratorio.
No se incluye firmware personalizado, claves, NVS, OTA firmado o
funcion de subida de Arduino C++ desde la aplicacion.
"""


def package(destination: Path, root: Path = ROOT) -> dict[str, str]:
    sketch = root / "firmware" / "esp32_rgb"
    profile = root / "config" / "esp32-board.env"
    assert (sketch / "esp32_rgb.ino").is_file(), "Missing main .ino"
    assert (sketch / "core" / "PinManager.h").is_file(), "Missing core headers"
    assert (sketch / "modules" / "LedRgbModule.h").is_file(), "Missing LED module"
    assert profile.is_file(), "Missing audited board profile"
    entries = {}
    for file in sorted(sketch.rglob("*")):
        if not file.is_file():
            continue
        relative = file.relative_to(sketch)
        # Never include CI-only personal provisioning, generated secrets, or
        # random test credentials, regardless of the file extension.
        if "config" in relative.parts or file.suffix.lower() not in INCLUDED_SUFFIXES:
            continue
        if file.is_symlink():
            raise ValueError("Symlinks not allowed inside firmware package")
        entries[f"esp32_rgb/{relative.as_posix()}"] = file.read_bytes()
    entries["config/esp32-board.env"] = profile.read_bytes()
    entries["LEEME_COMPI_ESP32.txt"] = README.encode("utf-8")
    manifest = "\n".join(
        f"{hashlib.sha256(data).hexdigest()}  {name}" for name, data in sorted(entries.items())
    ) + "\n"
    entries["SHA256-FILES.txt"] = manifest.encode("utf-8")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, STAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED)
    return {name: hashlib.sha256(data).hexdigest() for name, data in entries.items()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ROOT / "build/firmware/compi-esp32-source.zip")
    args = parser.parse_args()
    entries = package(args.out)
    print(f"Source ZIP: {args.out} ({len(entries)} files)")
    print("SHA256:", hashlib.sha256(args.out.read_bytes()).hexdigest())
