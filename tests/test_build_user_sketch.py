"""Regression tests for unsigned offline Arduino CLI build packaging (no hardware)."""
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

BUILDER_PATH = Path(__file__).resolve().parents[1] / "scripts" / "build-user-sketch.py"
spec = importlib.util.spec_from_file_location("esp32_user_builder", BUILDER_PATH)
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class BuildSketchTest(unittest.TestCase):
    def test_locked_board_profile(self):
        fqbn, core = builder.board_profile()
        self.assertEqual("esp32:esp32:esp32:PartitionScheme=min_spiffs", fqbn)
        self.assertEqual("3.0.7", core)

    def test_bad_profile_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / "board.env"
            file.write_text("ARDUINO_FQBN=esp32:esp32:esp32\nARDUINO_ESP32_CORE=3.0.7\n")
            with self.assertRaises(builder.BuildError):
                builder.board_profile(file)

    def test_sketch_validation_requires_matched_ino(self):
        with tempfile.TemporaryDirectory() as temp:
            sketch = Path(temp) / "Blink"
            sketch.mkdir()
            with self.assertRaises(builder.BuildError):
                builder.inspect_sketch(sketch)
            (sketch / "Blink.ino").write_text("void setup() {} void loop() {}")
            self.assertEqual(builder.inspect_sketch(sketch), sketch.resolve())
            (sketch / "payload.bin").write_bytes(b"A" * (builder.MAX_SOURCE_BYTES + 1))
            with self.assertRaises(builder.BuildError):
                builder.inspect_sketch(sketch)

    def test_manifest_budget_and_checksum(self):
        with tempfile.TemporaryDirectory() as temp:
            binary = Path(temp) / "Blink.ino.bin"
            binary.write_bytes(b"\x01\x02\x03")
            result = builder.artifact_manifest("board", "3.0.7", "Blink", [binary])
            self.assertFalse(result["ota_install_authorized"])
            self.assertFalse(result["firmware_signature_verified"])
            self.assertEqual(result["files"][0]["bytes"], 3)
            self.assertEqual(result["application_bytes"], 3)
            self.assertEqual(len(result["files"][0]["sha256"]), 64)
            with binary.open("wb") as f:
                f.truncate(builder.OTA_SLOT_BYTES - builder.MIN_HEADROOM_BYTES + 1)
            with self.assertRaises(builder.BuildError):
                builder.artifact_manifest("board", "3.0.7", "Blink", [binary])

    def test_mock_compile_produces_package_but_never_flashes(self):
        with tempfile.TemporaryDirectory() as temp:
            sketch = Path(temp) / "Blink"
            sketch.mkdir()
            (sketch / "Blink.ino").write_text("void setup() {} void loop() {}")
            dest = Path(temp) / "build" / "blink-001"
            seen = []

            def fake_run(command, **kwargs):
                seen.extend(command)
                binary_dir = Path(command[command.index("--output-dir") + 1])
                (binary_dir / "Blink.ino.bin").write_bytes(b"\xe9\x00\x01\x02")
                (binary_dir / "Blink.ino.partitions.bin").write_bytes(b"partitions")
                return subprocess.CompletedProcess(command, 0, stdout="compile OK")

            with patch.object(builder.shutil, "which", return_value="/fake/arduino-cli"), \
                 patch.object(builder.subprocess, "run", side_effect=fake_run):
                actual = builder.build(sketch, dest, "/fake/arduino-cli")
            self.assertEqual(actual, dest)
            self.assertEqual(seen[0:2], ["/fake/arduino-cli", "compile"])
            self.assertNotIn("upload", seen)
            manifest = json.loads((dest / "manifest.json").read_text())
            self.assertEqual(len(manifest["files"]), 2)
            self.assertFalse(manifest["automatic_flash_enabled"])
            self.assertTrue((dest / "firmware-package.zip").is_file())
            with self.assertRaises(builder.BuildError):
                builder.build(sketch, dest, "arduino-cli")

    def test_nonzero_compile_fails_closed_and_no_artifacts(self):
        with tempfile.TemporaryDirectory() as temp:
            sketch = Path(temp) / "Blink"
            sketch.mkdir()
            (sketch / "Blink.ino").write_text("broken")
            dest = Path(temp) / "not-created"
            with patch.object(builder.shutil, "which", return_value="/fake/arduino-cli"), \
                 patch.object(builder.subprocess, "run",
                              return_value=subprocess.CompletedProcess(["compile"], 1, stdout="syntax error")):
                with self.assertRaises(builder.BuildError):
                    builder.build(sketch, dest, "/fake/arduino-cli")
            self.assertFalse(dest.exists())

    def test_output_inside_source_is_forbidden(self):
        with tempfile.TemporaryDirectory() as temp:
            sketch = Path(temp) / "Blink"
            sketch.mkdir()
            (sketch / "Blink.ino").write_text("void setup(){}")
            with patch.object(builder.shutil, "which", return_value="/fake/arduino-cli"):
                with self.assertRaises(builder.BuildError):
                    builder.build(sketch, sketch / "build", "/fake/arduino-cli")


if __name__ == "__main__":
    unittest.main()
