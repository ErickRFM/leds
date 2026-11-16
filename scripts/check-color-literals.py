#!/usr/bin/env python3
"""Ratchet for hard-coded Compose colors.

`Color(0x...)` literals freeze a tone against the current theme, so the palette
files own them. Existing screens still carry legacy literals; this gate does not
pretend they are gone, it only forbids adding to them, and reports how much room
is left to shrink.
"""
from pathlib import Path
import json
import sys

root = Path(__file__).resolve().parent.parent
source_dir = root / "android/app/src/main/java/com/rgbcontroller"
baseline_path = root / "scripts/color-literal-baseline.json"
palette_files = {"core/ui/CompiTokens.kt", "core/ui/RgbTheme.kt"}

counts = {}
for path in sorted(source_dir.rglob("*.kt")):
    relative = path.relative_to(source_dir).as_posix()
    if relative in palette_files:
        continue
    found = path.read_text(encoding="utf-8").count("Color(0x")
    if found:
        counts[relative] = found

if "--update" in sys.argv:
    baseline_path.write_text(
        json.dumps(counts, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n"
    )
    print(f"BASELINE ACTUALIZADO: {sum(counts.values())} literales en {len(counts)} archivos")
    raise SystemExit(0)

if not baseline_path.exists():
    print("FALTA scripts/color-literal-baseline.json; ejecuta "
          "python scripts/check-color-literals.py --update")
    raise SystemExit(1)

baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
regressions = {
    name: (baseline.get(name, 0), count)
    for name, count in counts.items()
    if count > baseline.get(name, 0)
}
if regressions:
    for name, (allowed, actual) in sorted(regressions.items()):
        print(f"REGRESIÓN: {name} pasó de {allowed} a {actual} literales Color(0x…)")
    print("Mueve el color al token de tema correspondiente; no subas el baseline "
          "salvo que la migración sea deliberada y revisada.")
    raise SystemExit(1)

retired = sum(
    baseline[name] - counts.get(name, 0)
    for name in baseline
    if counts.get(name, 0) < baseline[name]
)
print(f"PASS COLOR-RATCHET: {sum(counts.values())} literales fuera de la paleta; "
      f"{retired} eliminados desde el baseline" +
      (" (baja el baseline para fijar el progreso)" if retired else ""))
