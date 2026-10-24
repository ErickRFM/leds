"""Tests for secret-free reproducible Arduino package."""
import sys
from pathlib import Path
import tempfile
import unittest
import zipfile
from hashlib import sha256

ROOT = Path(__file__).resolve().parent.parent
# The user-facing CLI filename intentionally has a hyphen; import by path.
import importlib.util
spec = importlib.util.spec_from_file_location(
    "compi_firmware_packager", ROOT / "scripts" / "package-esp32-source.py"
)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
package = module.package


class PackageArduinoSketchTests(unittest.TestCase):
    def test_distributed_archive_contains_only_current_firmware_sources(self):
        sketch = ROOT / "firmware" / "esp32_rgb"
        expected = {
            "esp32_rgb/" + file.relative_to(sketch).as_posix(): file
            for file in sketch.rglob("*")
            if file.is_file() and file.suffix.lower() in module.INCLUDED_SUFFIXES
            and "config" not in file.relative_to(sketch).parts
        }
        with zipfile.ZipFile(ROOT / "dist" / "esp32_rgb_firmware.zip") as archive:
            actual = {name for name in archive.namelist()
                      if name.startswith("esp32_rgb/") and Path(name).suffix.lower() in module.INCLUDED_SUFFIXES}
            self.assertEqual(set(expected), actual, "Distributed sketch is missing current headers or contains retired drivers")
            for name, file in expected.items():
                self.assertEqual(file.read_bytes().replace(b"\r\n", b"\n"),
                                 archive.read(name).replace(b"\r\n", b"\n"),
                                 f"Distributed firmware is stale: {name}")

    def test_contents_preserve_arduino_structure_and_exclude_secrets(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "firmware.zip"
            manifest = package(target)
            with zipfile.ZipFile(target) as archive:
                names = set(archive.namelist())
                self.assertIn("esp32_rgb/esp32_rgb.ino", names)
                self.assertIn("esp32_rgb/core/PinManager.h", names)
                self.assertIn("esp32_rgb/core/LocalWifiControl.h", names)
                self.assertIn("esp32_rgb/modules/LedRgbModule.h", names)
                self.assertIn("esp32_rgb/core/RgbAutomationClock.h", names)
                self.assertIn("config/esp32-board.env", names)
                self.assertIn("SHA256-FILES.txt", names)
                self.assertFalse(any("FactoryProvisioning" in n or "/config/" in n
                                     for n in names if n.startswith("esp32_rgb/")))
                self.assertEqual(manifest["esp32_rgb/esp32_rgb.ino"],
                                 sha256(archive.read("esp32_rgb/esp32_rgb.ino")).hexdigest())
                self.assertIn(b"min_spiffs", archive.read("config/esp32-board.env"))

    def test_archive_is_reproducible(self):
        with tempfile.TemporaryDirectory() as temp:
            a, b = Path(temp) / "a.zip", Path(temp) / "b.zip"
            package(a)
            package(b)
            self.assertEqual(a.read_bytes(), b.read_bytes())


if __name__ == "__main__":
    unittest.main()
