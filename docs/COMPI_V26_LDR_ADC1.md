# COMPI V26 — LDR ADC1 real y calibración de luz

## Hardware
- Sensor del usuario: fotorresistencia **LDR de 2 patas**, NO BH1750.
- Divisor recomendado: `3V3 → LDR → nodo ADC1 GPIO36 → 10 kΩ → GND`.
- La imagen entregada discrepa: rótulo **10 kΩ**, lista **1 kΩ**. Verificar la resistencia real; la prueba se diseñó para 10 kΩ.
- GPIO36 es exclusivamente entrada. Nunca aplicar 5 V ni conectar altavoz a GPIO.
- RGB sigue en GPIO27/25/26; DHT11 sigue en GPIO16; otras reservas consultarlas en vivo.

## Implementación
- `LdrLightModule` implementa `IModule` con ADC1, intervalo de 500 ms, filtro EMA, lecturas `ambient_light_raw` y porcentaje `ambient_light` tras calibrar. Lectura -1/fuera de ADC12 → `sensor_error`.
- Contrato sin lux ficticios: la lectura representa **luz relativa** (%) entre referencias oscuridad y claridad, con una diferencia ADC mínima de 32. La salida `ambient_light_raw` es independiente de `adc_raw` del sensor de suelo.
- Firmware: compilado `COMPI_BUILD_LDR=1`, **desactivado al arrancar** `COMPI_ENABLE_LDR=0`. Solo se reserva GPIO mediante `/v2/components/apply` autorizado. Un módulo catalogado no se presume físicamente conectado.
- `/v2/components/catalog`, `/status` y `/telemetry` incorporan el módulo, estado y lecturas; el componente puede tener GPIO asignado sin porcentaje válido.
- `POST /v2/components/ldr/calibrate` solicita `darkRaw` y `brightRaw` [0..4095], requiere sesión Wi-Fi SoftAP autorizada, módulo con GPIO real y lectura ADC válida, y no permite calibrar cuando hay otras tareas experimentales en conflicto. **Calibración solo en RAM**, se pierde al reiniciar ESP32.
- Android: perfil de GPIO36, activación por el asistente ya existente, lectura cruda, porcentaje, gráfico y botones para capturar referencias. No se presenta histórico como lectura en vivo.
- No habilita audio, voz ni RGB automático: quedan para ramas V27–V32 después de validar el LDR.

## Prueba física
1. Comprobar LED y DHT11 sin cambios; cargar **firmware y APK del mismo SHA**.
2. Cortar alimentación del ESP32 antes de cablear la LDR. Revisar la orientación del divisor, GND común, límite 3.3 V.
3. Conectar al SoftAP de Compi con autorización. Módulos → agregar Sensor de Luz LDR → GPIO36 → aplicar y comprobar `/v2/components/status` activo.
4. Antes de calibrar: `ambient_light_raw` debe cambiar tapando/destapando la LDR; `ambient_light` indica `not_calibrated`, sin porcentaje fabricado.
5. Cubrir sensor → capturar **Oscuridad**. Iluminar → capturar **Claridad**. Aplicar calibración con diferencia de al menos 32 cuentas ADC.
6. Repetir cambios de iluminación; la gráfica deberá mostrar sólo porcentajes válidos, y el LED/DHT11/SoftAP/BLE deben continuar funcionando.
7. Desconectar la señal o provocar un ADC erróneo en pruebas de laboratorio: comprobar que no se presenta valor falso como `fresh`.
8. Reiniciar: confirmar que calibración deja de estar vigente y requiere recrearse; no inventar persistencia.
9. Comprobar funcionamiento en ESP32-D0WD 4 MB y firmware compatible `min_spiffs` (presupuesto de flash).
10. No afirmar presencia física de la LDR sin mediciones comparativas.

## QA automatizado
- `tests/component_modules_test.cpp`: GPIO y conflictos, lecturas crudas, calibración, errores ADC y telemetría con estado.
- `ComponentTelemetryCodecTest.kt`: separación `ambient_light_raw` y `adc_raw` de suelo.
- CI: firmware ESP32, tests host C++, Android build/lint/unit, emuladores; documentar resultados reales y SHA.
- **Pendiente:** validación física en el equipo del usuario.

## Próximas fases
- V27 Android audio y TTS (no depende de altavoz físico).
- V28 audio físico con módulo reproductor/amplificador apropiado para altavoz pasivo **8 Ω, 0.5 W**, todavía no suministrado; sin GPIO directo.
- V29 automatización ambiental PWM a partir de temperatura/luz **fresh** y arbitraje con controles manuales.
- V30 avisos de voz; V31 UX integral; V32 pruebas físicas y release.
