# Protocolo de pruebas V3 — Android + ESP32 real

**Producto:** ESP32 Control Center **DEBUG/LAB ONLY**, no release público.  
**Hardware esperado:** ESP32-D0WD-V3 rev 3.1, flash 4 MB; ESP32 clásico.  
**Software:** Arduino ESP32 core 3.0.7, particiones `min_spiffs`, Android JDK 17 / SDK 34.
**Precaución:** BLE GATT sigue sin autorización/bonding; usar únicamente en banco controlado y nunca en equipos de terceros.

## Paso 1. Descargar el APK de la MISMA revisión que el firmware

1. Abre [GitHub Actions → Android + ESP32 CI](https://github.com/ErickRFM/leds/actions/workflows/android-ci.yml).
2. Selecciona la ejecución **verde más reciente de `main`** y comprueba que sus tres jobs (`android`, `firmware-core-tests`, `firmware`) estén correctos.
3. En **Artifacts**, descarga `rgb-controller-debug`. Descomprime e instala **`app-debug.apk`**.
4. Verifica `SHA256SUMS.txt` y conserva `build-info.txt`. Compara el commit Git (SHA) con `main`; NO compares APK de una revisión con firmware de otra sin anotarlo.
5. El artifact `esp32-lab-4mb-firmware` contiene binarios y checksum **para diagnóstico**, no es un instalador OTA ni un flasher automático.

Si tienes Android SDK Platform Tools y el móvil autorizado por depuración USB:

```powershell
adb devices
adb install -r .\android\app\build\outputs\apk\debug\app-debug.apk
```

Instala el APK recién descargado o usa el generado localmente; la ruta del comando anterior solo existe tras compilar el proyecto en tu PC.

## Preparación de Arduino CLI en Windows

Un APK Android puede compilar correctamente mientras el firmware falla con
\`arduino-cli is not installed or not in PATH\`. Eso **no** invalida el APK ya generado.
Arduino IDE y \`arduino-cli.exe\` son herramientas relacionadas pero no necesariamente
expuestas con el mismo ejecutable en el \`PATH\`.

En PowerShell, comprueba primero:

\`\`\`powershell
Get-Command arduino-cli.exe -ErrorAction SilentlyContinue
\`\`\`

Si no aparece, instala Arduino CLI con \`winget install --id ArduinoSA.CLI --exact\`
y abre una nueva terminal. Alternativamente usa el ejecutable oficial desde
https://docs.arduino.cc/arduino-cli/installation.

El verificador actual intenta descubrir Arduino CLI en PATH, en ubicaciones
comunes del IDE y en \`.tools\`. También admite un binario indicado explícitamente
o la variable de entorno \`ARDUINO_CLI_PATH\` (ruta completa al ejecutable).

\`\`\`powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -CheckToolchainOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -SkipAndroid
\`\`\`

La primera instrucción verifica únicamente Arduino CLI; la segunda compila
el firmware sin repetir la compilación del APK ya disponible. Si la búsqueda automática
falla, indica explícitamente la ruta:

\`\`\`powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -SkipAndroid -ArduinoCliPath "C:\ruta\arduino-cli.exe"
\`\`\`

**Ningún ejemplo flashea el ESP32**; para grabación se necesita la opción
explícita \`-UploadFirmware -Port COMx\`, previa comprobación del cableado,
particiones y recuperación USB.

## Paso 2. Verificación sin flashear (recomendada)

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
arduino-cli board list
```

Guarda el SHA de Git, resultado CI y hash del APK. El script no flashea nada por defecto. **No cambies** esquema de particiones ni borres la flash sin copia y recuperación USB verificada. Para probar firmware nuevo, revisa `docs/HARDWARE_ESP32.md` y `docs/CABLEADO.md` antes de decidir si cargarlo.

## Paso 3. BLE — funcionamiento observable

- R = GPIO27; G = GPIO25; B = GPIO26. **Una resistencia de 220–330 Ω por canal**; polaridad `COMMON_ANODE` del firmware debe coincidir con tu LED.
- Buscar `RGB-ESP32`, conectar y esperar `Ready`. Comprueba lectura inicial de STATE (no valores hardcode).
- Ajustar R, G, B, brillo, ON/OFF, arcoíris/respiración/fundido. Registrar color **visual** y estado reportado por separado.
- Arrastrar sliders al menos 10 s, cerrar/reabrir app, apagar/encender BLE y reiniciar ESP32: revisar si la UI se recupera sin información falsa.
- Si el ESP32 deja de confirmar escrituras GATT por 5 s, el Android actual debe mostrar error y forzar reconciliación de STATE tras reconexión. Un ACK GATT no equivale a prueba eléctrica del LED.

## Paso 4. Wi-Fi LAB — sin Internet

1. Abrir monitor serie a **115200** para consultar **en privado** el password aleatorio `ESP32-Control-Lab` y la clave de control de 16 caracteres. **No publiques ni pegues esos secretos en tickets/capturas.**
2. Conectar Android al Wi-Fi `ESP32-Control-Lab`, aceptando 'conservar Wi-Fi sin Internet'. La IP/gateway local esperado es `192.168.4.1`.
3. Abrir **Ajustes → Centro ESP32** y consultar Estado y Diagnóstico. No confundir 'STA conectada al router' con 'Android conectado al SoftAP'.
4. Comprobar pines 18,19,21,22,23, ADC1 GPIO34, laboratorios temporizados y RGB Wi-Fi. **No aplicar >3.3 V a ADC.** No enlazar cargas de potencia directamente al GPIO.
5. Registrar cuándo una solicitud fue aceptada y cuándo el dispositivo confirmó su estado. En Wi-Fi conectar al router devuelve 202 `connecting`: comprobar estado posterior.

## Matriz de retroalimentación

| ID | Prueba | Esperado | Estado / observación |
|---|---|---|---|
| B1 | Scan BLE y Ready | ESP32 encontrado y STATE inicial válido | PENDIENTE |
| B2 | SET R/G/B/brillo | LED real cambia sin jitter persistente | PENDIENTE |
| B3 | OFF/ON y efectos | Estado/color coherentes | PENDIENTE |
| B4 | Reinicio/desconexión | No deja datos o colores falsamente confirmados | PENDIENTE |
| W1 | Android SoftAP sin Internet | Estado responde vía Wi-Fi, nunca celular | PENDIENTE |
| W2 | Clave errónea | 403; ninguna operación aplicada | PENDIENTE |
| G1 | GPIO permitido y reset | HIGH/LOW, timeout y liberación segura | PENDIENTE |
| G2 | GPIO reservado RGB/flash | Comando rechazado sin tocar salida | PENDIENTE |
| S1 | ADC GPIO34 | RAW entre 0–4095, no voltios calibrados | PENDIENTE |
| L1 | Experimento + Stop | Secuencia acotada; pines devueltos a entrada/LOW | PENDIENTE |
| F1 | Particiones | Diagnóstico reporta 2 slots, OTA deshabilitado | PENDIENTE |

## Plantilla para reportar un error

```text
ID: B2 / W1 / G2 / …
Commit (de build-info.txt):
Android / versión del SO:
Modelo de teléfono:
Conexión: BLE / SoftAP
Firmware grabado y particiones:
Pasos EXACTOS:
Esperado:
Observado:
¿La app reporta Ready/STATE/API 200/202 o error?
Log SIN contraseñas/claves/SSID personal:
Capturas SIN secretos:
```

**Los resultados de CI no sustituyen evidencia física.** Mantener abiertos [#10](https://github.com/ErickRFM/leds/issues/10) y [#28](https://github.com/ErickRFM/leds/issues/28) hasta recibir evidencia de la placa y el móvil.
