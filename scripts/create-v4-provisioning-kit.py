#!/usr/bin/env python3
"""Generate ONE private, per-device WiFiProv Security1 provisioning kit.

Never commit either generated output. Do not reuse a kit on another board.
This script cannot flash, register an owner, or attest a device.
"""
import argparse
import json
import secrets
from pathlib import Path

def create(out: Path, *, pop: str | None = None, name: str | None = None) -> dict:
    out = out.resolve()
    if out.exists():
        raise FileExistsError(f"Refusing to overwrite existing kit: {out}")
    pop = pop if pop is not None else secrets.token_hex(16)
    name = name if name is not None else "PROV_" + secrets.token_hex(4).upper()
    if len(pop) != 32 or any(c not in "0123456789abcdef" for c in pop):
        raise ValueError("PoP must be a unique 128-bit lowercase hex string")
    if len(name) != 13 or not name.startswith("PROV_") or any(
        c not in "0123456789ABCDEF" for c in name[5:]
    ):
        raise ValueError("Provisioning device name must be PROV_ followed by 8 uppercase hex digits")
    out.mkdir(parents=True, mode=0o700)
    header = out / "FactoryProvisioning.h"
    payload = out / "provisioning-qr.json"
    header.write_text(
        "#pragma once\n// PRIVATE per-device manufacturing credential; NEVER COMMIT.\n"
        "namespace esp32factory {\n"
        f'inline constexpr char kProvisioningName[] = "{name}";\n'
        f'inline constexpr char kProvisioningPop[] = "{pop}";\n'
        "}\n", encoding="utf-8"
    )
    qr = {"ver": "v1", "name": name, "pop": pop,
          "transport": "ble", "security": 1}
    payload.write_text(json.dumps(qr, separators=(",", ":")) + "\n", encoding="utf-8")
    # Best-effort private file mode on POSIX, Windows relies on user ACLs.
    header.chmod(0o600)
    payload.chmod(0o600)
    return {"directory": out, "header": header, "qr": payload, "public_name": name}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path,
                        help="NEW private directory (refused if existing)")
    args = parser.parse_args()
    result = create(args.out)
    print(f"Private provisioning kit created at {result['directory']}")
    print("Store provisioning-qr.json and its QR label OFFLINE. Never commit or share.")
    print("Copy FactoryProvisioning.h to firmware/esp32_rgb/config/ BEFORE local firmware compile.")
    print("A phone still needs a compatible WiFiProv client; Wi-Fi provisioning is NOT RGB ownership.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
