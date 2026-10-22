# Compi — contrato de integración y aceptación (2026-10-22)

## Alcance y principio
Una Estación Compi: Android Compose + ESP32 firmware con DHT11, LDR, RGB, SSD1306 OLED y DFPlayer. Los módulos comparten una sesión autenticada y nunca presentan datos anteriores como lecturas actuales. **Merge/CI verde no equivale a prueba física**.

## Hardware y cableado auditado
- ESP32-D0WD-V3 4 MB; placa ESP32 Dev Module; partición min_spiffs según perfil de CI.
- DHT11 DATA GPIO16; OLED SSD1306 SDA GPIO21/SCL GPIO22, 3.3 V; LDR analógico ADC1 GPIO32, divisor a 3.3 V, nunca 5 V.
- RGB R/G/B GPIO27/25/26 (comprobar ánodo/cátodo).
- DFPlayer UART ESP32 TX17 -> RX reproductor (resistor serie recomendado); TX reproductor -> ESP32 RX18 (adaptar señal si supera 3.3 V). Alimentación 5 V regulada y GND común; speaker en salida de amplificador adecuada; no conectar salida puente a GND.
- microSD FAT32 con pistas indexadas por el protocolo implementado. Comprobar el resultado real con tarjeta y bocina.

## Hitos independientes y puertas de salida
| Orden | Entregable | Prueba automática | Prueba física obligatoria |
|---|---|---|---|
| 0 | CI y artefactos del MISMO SHA | Android build/test, firmware ESP32 Arduino CLI, host C++, perfil Windows verdes | Arranque/serial tras flasheo, APK instala |
| 1 | DHT11/LDR y calibración | Freshness, intervalos, calibración min-span, persistencia NVS | Sensor desconectado, luz tapada/iluminada, calibración tras reinicio |
| 2 | RGB automático | Clases fría/confort/cálida/caliente, humedad si activada, histéresis y fallos | Color visible y PWM estable ante cambios de lectura; manual cancela automatización |
| 3 | OLED/escenas | Validación de comandos, autorizaciones, sin datos falsos y render por I2C no bloqueante | Cambiar cara/escena, retorno a telemetría y Wi-Fi/BLE coexistentes |
| 4 | DFPlayer | Saludo a 3s, solicitudes de avisos térmicos periódicos, límites de cooldown, pruebas serie | Confirmar MP3 0001/0002/0003 reales, tarjeta ausente, volumen seguro; envío UART NO prueba reproducción |
| 5 | Transporte | BLE propietario + Wi-Fi owner; BUSY/retry, timeout, reconexión | Alternancia BLE/Wi-Fi, desconexión durante comando y recuperación |
| 6 | UI/UX actual | Compose unit+instrumented emulator API30/35, back stack, accesibilidad básica | Verificar lectura/status/color/controles en teléfono real |

## Secuencia de trabajo y no regresión
1. Revisar `main`, PRs, árbol y ejecuciones recientes; no declarar verde hasta aprobar SHA de cabeza.
2. Trabajar cada subsistema en PR pequeño con prueba; si CI espera, avanzar a otro flujo independiente y volver a comprobar.
3. Evitar nuevas pantallas duplicadas: Compi Station dentro de Modules y detalles contextuales; el antiguo route LED redirige a Modules.
4. Fusionar solo después de revisar conflictos y prueba pertinente; volver a validar el nuevo SHA de `main`.
5. Registrar una matriz de evidencia (sha APK = sha firmware, resultados de UART/BLE/Wi-Fi, fotos OLED/color, audio audible). Sin acceso al circuito físico, etiquetar **pendiente de validación física**.

## Riesgos vigentes
- DFPlayer sin microSD/BUSY: no se puede afirmar que reprodujo, únicamente que el firmware solicitó la pista.
- DHT11 y ADC LDR pueden funcionar aislados y fallar durante coexistencia con OLED y BLE; comprobar carga simultánea.
- Calibración LDR: señal ADC cruda 0-4095 no es lux ni porcentaje sin referencias distintas (mínimo 32 cuentas).
- Los valores stale/perdida de autorización no deben presentarse como en vivo.
- Animaciones y UI actual deben respetar movimiento reducido y escalado tipográfico.
