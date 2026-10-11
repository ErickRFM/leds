#!/usr/bin/env python3
"""No dependencies: deterministic kit validation, collision safety, no secret logging."""
import importlib.util
import json
import tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("kit", Path(__file__).resolve().parent / "create-v4-provisioning-kit.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp) / "kit"
    first = module.create(root, pop="ab" * 16, name="PROV_DEADBEEF",
                          ap="cd" * 8, key="ef" * 8)
    payload = json.loads(first["qr"].read_text())
    assert payload == {"ver":"v1","name":"PROV_DEADBEEF","pop":"ab"*16,
                       "transport":"ble","security":1}
    header = first["header"].read_text()
    assert '"ab' in header and 'PROV_DEADBEEF' in header
    assert 'kLabApPassword[] = "cdcdcdcdcdcdcdcd"' in header
    assert 'kLabAccessKey[] = "efefefefefefefef"' in header
    lab = json.loads(first["lab"].read_text())
    assert lab == {"ssid": "ESP32-Control-Lab", "password": "cd"*8, "deviceKey": "ef"*8}
    try:
        module.create(root)
        raise AssertionError("must never overwrite an existing personalization kit")
    except FileExistsError:
        pass
    for invalid in ("abcd1234", "0"*31, "g"*32):
        try:
            module.create(Path(tmp)/("bad-"+invalid[:8]),pop=invalid,name="PROV_DEADBEEF")
            raise AssertionError("weak or malformed PoP accepted")
        except ValueError:
            pass
    try:
        module.create(Path(tmp)/"reused-credentials",
                      pop="ab"*16, ap="cd"*8, key="cd"*8, name="PROV_DEADBEEF")
        raise AssertionError("duplicate lab secret accepted")
    except ValueError:
        pass
    other=module.create(Path(tmp)/"random-kit")
    assert len(json.loads(other["qr"].read_text())["pop"])==32
    assert json.loads(other["qr"].read_text())["pop"] != payload["pop"]
print("PASS V4: private random kits, exact QR contract and overwrite rejection")
