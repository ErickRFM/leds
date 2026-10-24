"""Compile and execute every portable firmware test; no board or flashing.

Windows portable example: --compiler local-validation/toolchain/ziglang/zig.exe c++
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiler", nargs="+", default=["g++"])
    parser.add_argument("--output", default="local-validation/host-tests")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = (root / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["ZIG_GLOBAL_CACHE_DIR"] = str(root / "local-validation/zig-global")
    env["ZIG_LOCAL_CACHE_DIR"] = str(root / "local-validation/zig-local")
    sources = sorted((root / "tests").glob("*_test.cpp"))
    sources += sorted((root / "compi/audio/tests").glob("*.cpp"))
    results = []
    for source in sources:
        executable = output / (source.stem + (".exe" if os.name == "nt" else ""))
        command = args.compiler + ["-std=c++17", "-Wall", "-Wextra", "-Werror", "-pedantic",
                                   "-I", str(root / "tests"), "-I", str(root / "tests/stubs")]
        if source.stem == "nvs_owner_first_boot_test":
            command += ["-DARDUINO=1"]
        command += [str(source), "-o", str(executable)]
        log = output / (source.stem + ".log")
        with log.open("w", encoding="utf-8") as stream:
            try:
                build = subprocess.run(command, cwd=root, env=env, stdout=stream,
                                       stderr=subprocess.STDOUT, timeout=180)
                code = build.returncode
                if code == 0:
                    code = subprocess.run([str(executable)], cwd=root, env=env, stdout=stream,
                                          stderr=subprocess.STDOUT, timeout=30).returncode
            except (OSError, subprocess.TimeoutExpired) as error:
                stream.write(str(error))
                code = -1
        passed = code == 0
        results.append({"test": str(source.relative_to(root)), "passed": passed, "exit": code})
        print(f"{'PASS' if passed else 'FAIL'} {source.stem}", flush=True)
        if not passed:
            print("\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-18:]), flush=True)
    (output / "results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    count = sum(item["passed"] for item in results)
    print(f"Host tests: {count}/{len(results)} passed", flush=True)
    return 0 if count == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
