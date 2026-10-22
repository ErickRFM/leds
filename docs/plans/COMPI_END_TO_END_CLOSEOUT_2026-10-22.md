# COMPI — Plan maestro de integración y cierre (22 octubre 2026)

## Propósito y reglas
Entregar una **Estación Compi funcional**: Android Compose + ESP32 firmware, OLED 128×64, DHT11, LDR, RGB y DFPlayer Mini, con transporte BLE y Wi-Fi coherente y sin simulaciones de éxito. Trabajar por incrementos pequeños, abrir PR, esperar CI, fusionar de manera secuencial. No declarar validación física sin evidencia del ESP32. No eliminar ramas con cambios exclusivos.

## Línea base y reconciliación (P0)
- Base consultada: main @ 0f13c8eb. PR #251 abierto en draft, no fusionado.
- Revisar ramas existentes antes de crear implementación nueva: feat/compi-station-live-charts-20261022, feat/compi-station-compact-live-overview-20261022, feat/compi-real-station-ux-20261022, fix/compi-station-fresh-telemetry-20261022, feat/compi-oled-live-controls-20261021, feat/compi-oled-scenes-ui-20261022, feat/compi-ldr-persistent-calibration-20261022, feat/compi-dfplayer-environmental-voice-20261021, feat/compi-aurora-premium-ui-20261022.
- Para cada rama: obtener commits exclusivos, diferencias frente a main, compilación, evidencia y conflictos; integrar solo código útil y compatible. Evitar duplicar componentes o sobrescribir fixes recientes.

## Fase A — Contrato único de estado y comandos (P0)
- Modelo tipado: ConnectionState (offline/connecting/auth-required/ready/stale/error), transport (BLE/Wi-Fi), DeviceCapabilities, TelemetrySnapshot(timestamp, temperatureC, humidityPct, ldrRaw, lightPct, validity), CommandState(queued/sent/ack/rejected/timeout).
- Un solo repositorio observable (StateFlow) para Inicio y Estación. TTL de telemetría, timestamp del dispositivo y recepción, descarte de datos obsoletos, invalidación al desconectar; sin fabricar lecturas.
- Matriz comando/capacidad/respuesta para OLED (scene/expression, brightness/contrast, invert, pause), RGB (color, brightness, auto/ambient, LDR), audio (play/stop/volume/voice mode), y lectura sensor.
- Autorización BLE owner válida + sesión activa; mensajes accionables para no emparejado/no autorizado; no confundir cifrado con ownership.
- Misma semántica de respuesta y feedback por BLE y Wi-Fi: ACK aplicado, errores específicos, readback cuando aplique. Idempotencia/reintentos acotados.

**Aceptación:** tests de parseo/validación, reconexión, autorización, timeout y stale; ninguna pantalla confirma cambios solo por pulsación.

## Fase B — Firmware y transporte BLE/Wi-Fi (P0)
- Inventariar GATT frames y endpoints /v2/..., ubicar acciones ausentes y añadir handlers equivalentes sin romper clientes.
- Revisar presupuesto de flash 4 MB/min_spiffs/OTA y GPIO compartidos antes de flashear.
- Implementar lectura de estado efectiva y ACK, persistencia selectiva en NVS, protección de frecuencia de escrituras y arranque consistente.
- Añadir tests host/firmware por comando y errores; compilar sketch Arduino CLI del paquete completo.

**Aceptación:** BLE y Wi-Fi producen el mismo resultado observable ante cada acción soportada.

## Fase C — Sensores / historia / gráficas (P0)
- DHT11 GPIO16: valores físicos válidos y diagnosticar bordes/fallos; marcar calidad de muestra y caducidad. LDR: GPIO real desde PinManager, ADC, calibración sombra/luz, guardado y recuperación después de reiniciar.
- Un pipeline de telemetría → StateFlow → historial circular limitado → ambas pantallas. Escalas dinámicas con unidades, ticks, timestamps, ventanas 15m/1h, y mínimos/máximos; no mezclar muestras de dispositivos.
- Estado desconectado: último valor solo si se etiqueta con hora; sin muestra = vacío compacto. No graficar datos inventados.
- RGB ambiental: bandas de temperatura ya integradas PR #248, modulación humedad y brillo LDR, histéresis, modo manual/auto explícito y readback.

**Aceptación:** pruebas de rango, histéresis, persistencia LDR y misma telemetría en Inicio/Estación.

## Fase D — OLED (P0)
- Selección real de sonrisa, calabaza, gato y gato baile, expresiones extra compatibles con firmware; contraste/invertir/pausar por BLE y Wi-Fi, mismos permisos.
- Vista previa OLED 128×64 proporcional que represente estado confirmado por firmware, no selección provisional; fallbacks cuando capacidad falte.
- Quitar texto antiguo "OLED + Ambiente" de firmware si sigue presente; usar espacio para expresión/mediciones según diseño.
- Confirmar iconografía, animación sin parpadeo y rendimiento.

**Aceptación:** cambiar escena vía cada transporte y observar en hardware tras reinicio cuando sea persistente.

## Fase E — Audio DFPlayer Mini (P1, separado por ausencia de microSD)
- Verificar UART2 TX GPIO17/RX GPIO18 contra PinManager y cableado definitivo. Diferenciar módulo detectado, SD detectada, pistas enumerables, reproducción confirmada y error.
- Conectar botones a comandos reales y deshabilitarlos mientras no estén disponibles; construir pistas y playlist con estado verdadero.
- Voz ambiental: control enviado al firmware, persistencia, antirrepetición y umbrales con lecturas válidas; no tratar flags Compose como hardware.
- Sin microSD física, se permiten pruebas unitarias/seriales, pero NO certificar reproducción.

**Aceptación:** archivos MP3 reales en microSD, ACK/estado verificables, reproducción por BLE y Wi-Fi.

## Fase F — UX/UI premium compacta (P1)
- Tabs scrollables: Resumen, Ambiente, Iluminación, OLED (sin cortes de palabra).
- Eliminar espacios verticales vacíos, textos técnicos repetitivos, duplicación de cards y overlays de bottom nav; anclaje scroll al tab.
- Inicio: hero único temperatura/humedad/luz + acceso a estación. Estación: resumen compacto, gráficos e instrumentos. Badges consistentes "Conectado/Desconectado/Sin lectura/No autorizado".
- Accesibilidad: 320-420dp, escalado fuente, modo claro/oscuro y contraste fuerte, movimiento reducido, estados loading/empty/error/success.
- Screenshot tests y Compose navigation/UI tests en emulador.

**Aceptación:** no textos truncados ni botones muertos; 15m/1h funciona con datos válidos y vacío conciso.

## Fase G — Integración, CI y entrega (P0)
- Comprobar Android unit/instrumented, lint, compilación debug APK, firmware compile, host tests y seguridad/auth; clasificar fallos relevantes.
- Revisión de conflicto PR por PR, merge en main solo CI verde y contrato compatible; CI posterior al merge. Comparar ramas sobrantes para no perder cambios; cerrar únicamente duplicados verificados.
- Entregables: commit/PR/CI, APK, ZIP del sketch Arduino actualizado, pinout verificado y guía corta de instalación (sin afirmar listo si falla).
- Validación física con usuario: primero DHT11 y LDR, luego RGB, OLED, BLE/Wi-Fi, finalmente DFPlayer con microSD. Registrar evidencia por paso.

## Orden ejecutable y checkpoints
1. Baseline/reconciliación de ramas + PR #251.
2. Contrato de estado/ACK/autorización.
3. Sensor pipeline y conexión consistente entre pantallas.
4. Firmware BLE/Wi-Fi paridad + RGB/OLED.
5. DFPlayer y voz ambiental.
6. UI, automatizadas y artefactos.
7. Pruebas físicas y cierre.

No usar la etiqueta "todo funciona" sin cumplir pruebas y matriz de aceptación. Si CI está rojo, resolver antes del merge; mientras tanto se puede revisar un módulo independiente sin tocar main.
