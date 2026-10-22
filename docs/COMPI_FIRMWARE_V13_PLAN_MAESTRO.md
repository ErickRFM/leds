# Plan maestro Compi ESP32 V13 — ejecución Git y entrega para Arduino IDE

Base verificable: main `2ef5b7f` posterior a V12 de automatizaciones.  
PR de estabilización: #80 (rama `fix/compi-v13-firmware-runtime-stability`).  
Chip objetivo: ESP32-D0WD-V3 clásico, flash 4 MB, core Arduino-ESP32 3.0.7, esquema `min_spiffs`.  
Pines RGB: rojo 27, verde 25, azul 26; ánodo común configurado, verificar físicamente.

## Bloques de ejecución y criterios de entrega

| Bloque | Git / código | Criterio automatizado | Prueba sobre placa |
|---|---|---|---|
| A. Motor temporal RGB | `RgbAutomationClock.h`, `LocalWifiControl.h` | Delays largos descuentan TODOS los intervalos; nunca excede presupuesto de 10 ciclos y 20 minutos | Esperar tiempo real, interrumpir red y confirmar último estado OFF |
| B. Persistencia NVS | `esp32_rgb.ino`, `LocalWifiControl.h` | Configuración manual copiada ANTES de escrituras de script; solo acciones manuales crean una instantánea pendiente | RGB manual, esperar 2s, ejecutar script/automatización, reset USB, recuperar ajuste manual |
| C. Entrega Arduino IDE | `scripts/package-esp32-source.py`, `android-ci.yml` | ZIP determinista sin configuración privada, contiene .ino, `core`, `modules`, perfil min_spiffs y hashes SHA256 | Extraer ZIP, abrir `esp32_rgb.ino`, compilar y cargar con COM verificado |
| D. Compatibilidad y seguridad | `scripts/check-contracts.py` | Sin cambio UUID GATT, GPIO reservado, esquema flash; ningún control GPIO/OTA privilegiado por BLE V1 | Leer BLE, SoftAP, GPIO con clave, confirmar DENY en STA |
| E. Identidad/propietario BLE | Issue #78 | **P0 no cumplido**: challenge/response criptográfico, anti-replay, revocación, control en firmware | Dos celulares y prueba de atacante sin autorización |
| F. Ampliación de placas y OTA | Issues independientes a planificar | No reclamar GPIO ni cambiar partición sin perfil explícito; OTA hasta firma/rollback | Matriz por placa, recovery USB, cortes de energía |
| G. QA final y release | PR aprobado, firmware y Android del MISMO SHA | 4 checks verdes y artefactos con checksum | 30 reconexiones, GPIO real, tensión/corriente, reset/NVS, BLE y Wi-Fi |

**Conservación:** no borrar los servicios originales BLE V1/STATE, identidad pública, telemetría GPIO, scripts acotados, Flash o sensores. No declarar propietario a partir de MAC/ID, ni habilitar `release` Android o subir OTA sin firma.

## Código fuente completo para cargar

El artefacto GitHub `esp32-lab-4mb-firmware` de **la ejecución del SHA de main final** incluye:

- `compi-esp32-source.zip`: ZIP listo para Arduino IDE, con carpetas.
- `esp32_rgb.ino.bin` y binarios complementarios: salida de Arduino CLI ya compilada (no instalar archivos individualmente sin herramienta compatible).
- `SHA256SUMS.txt` y `build-info.txt`: verificar integridad y commit.

### Ruta Arduino IDE 2.3.10

1. Descarga `compi-esp32-source.zip` desde GitHub Actions en el commit correcto, extrae completo.
2. Abre **esp32_rgb/esp32_rgb.ino**. Nunca renombres ni copies solo .ino.
3. Selecciona placa **ESP32 Dev Module**, flash **4 MB**, partición **Minimal SPIFFS (1.9 MB APP with OTA)** y core **3.0.7**.
4. Confirma el COM real en Tools → Port (antes se observó COM5, pero puede cambiar).
5. Pulsa **Verificar**; si cabe en la partición, **Subir**.
6. Monitor Serie a **115200**; observa la red `ESP32-Control-Lab` y las dos claves (solo en monitor local, NO compartir).
7. Conecta LED de pruebas con resistencias individuales 220–330 Ω y revisa el tipo de LED, tensión y GND.
8. Android: usa APK **debug** del MISMO commit; conecta BLE o red SoftAP sin Internet.

### PowerShell, desde la raíz del repositorio

```powershell
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -SkipAndroid -ValidateProfileOnly
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -SkipAndroid -UploadFirmware -Port COM5
```

**ATENCIÓN:** confirmar COM y respaldo NVS antes de USB flash; particiones equivocadas pueden alterar credenciales. COM5 es solo el puerto que apareció en pruebas previas.

### Validación manual mínima obligatoria

1. Antes de scripts: cambiar RGB manual a 100/0/0 y comprobar STATE por BLE.
2. Ejecutar un script RGB de 2 s y comprobar que no persistió sobre la elección manual; reiniciar y leer STATE.
3. Repetir con automatización limitada 2 s × 3 ciclos. Cortar brevemente red y observar estado final OFF; repetir con stalls de red.
4. Durante automatización mandar GET y comando malformado: deben dejarla intacta. Mandar SET válido: debe detenerla.
5. Comprobar lectura GPIO25/26/27 reservados en interfaz y funcionamiento de GPIO19 con **LED distinto** conectado con resistencia.
6. Monitorizar libre/min heap y guardar resultados de reinicios, rechazo BLE y estabilidad. No exponer claves.

Se considera **firmware V13 de laboratorio entregado** al pasar CI y publicar archivos para instalación del SHA integrado. La validación eléctrica, BLE seguro de propietario, OTA y paridad privilegiada siguen bloqueadas hasta evidencia de banco/seguridad.
