# V5 — Compilación Arduino CLI local verificable (primer incremento)

## Qué funciona

El helper `scripts/build-user-sketch.py` compila un proyecto **Arduino C++** previamente guardado en el PC con el perfil fijo de laboratorio ESP32 Classic 4 MB (`min_spiffs`) y el core 3.0.7 configurado en `config/esp32-board.env`. Si el compilador termina correctamente, entrega `*.bin`, `manifest.json` con SHA-256 y presupuesto de firmware, un ZIP portátil y un log.

- **No requiere nube ni token**; todo se ejecuta localmente.
- **No flashea** USB ni permite OTA o rollback; el ZIP no es un paquete firmado.
- No ejecuta sketches en Android. Para editar desde la app, usa **Código → Exportar .ino**, pasa el archivo a Windows y guárdalo dentro de una carpeta con el mismo nombre (por ejemplo `Blink/Blink.ino`).
- La compilación de un sketch C++ y de las librerías se ejecuta sobre el equipo anfitrión: **no es un sandbox de código no confiable**. Revisa los proyectos y dependencias importados. El helper deliberadamente no acepta conexiones desde la red.
- No modifica el firmware funcional V3/V4 ni las particiones de la placa.

## Primera prueba en Windows (sin subir a la placa)

Requisitos: Python 3.9+, Arduino CLI con el core `esp32:esp32@3.0.7` instalado. Instálalo con los comandos existentes del README/verificador local.

```powershell
git switch main
git pull --ff-only origin main
# Si exportaste desde Android: coloca el archivo como C:\ESP32\Proyectos\Blink\Blink.ino
python .\scripts\build-user-sketch.py --sketch "C:\ESP32\Proyectos\Blink" --output ".\local-validation\user-sketch-builds\Blink-001"
```

Salida:

```text
local-validation/user-sketch-builds/Blink-001/
  Blink.ino.bin
  Blink.ino.partitions.bin (si Arduino CLI lo genera)
  manifest.json
  firmware-package.zip
  build.log
```

El manifest contiene SHA-256 de todos los binarios y marca de forma explícita `firmware_signature_verified=false`, `ota_install_authorized=false`, `automatic_flash_enabled=false`, `physical_hardware_tested=false`. No interpretes el ZIP como un actualizador OTA.

Comprobaciones automáticas independientes del hardware:

```powershell
python -m unittest discover -s tests -p "test_build_user_sketch.py" -v
```

Para comprobar Android APK, firmware oficial, host C++ y la seguridad de la versión integrada:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```

El script de build de usuario falla si la carpeta del sketch no coincide con `<nombre>.ino`, si encuentra symlinks, demasiados archivos, supera 2 MiB de fuentes, el compilador devuelve error, no produce el binario de aplicación, excede el presupuesto dual-slot de 4 MB o la carpeta de salida ya existe. No altera versiones previas.

## Próximas puertas (NO IMPLEMENTADAS)

1. Android empaqueta/importa un proyecto multiarchivo y se comunica con un **companion** PC por un canal autenticado y expresamente consentido.
2. Provisioning/vinculación segura reales, autorización de comandos y cierre del GATT WRITE anónimo.
3. Comprobación de firmware y particiones **en hardware**, flash supervisado por USB, imagen firmada, rollback físico y recuperación USB antes de cualquier OTA móvil.
4. Un prototipo Android offline de toolchain solo se acepta tras demostrar librerías, binarios, licencias, espacio y RAM reales.

El helper es un incremento **de compilación local en PC para pruebas**. No equivale a IDE Android autónomo ni acredita seguridad de release.
