# Criterios de aceptación y QA

## Firmware / placa física
- [ ] Arduino-ESP32 3.x compila para modelo ESP32 exacto.
- [ ] GPIO 27/25/26 encienden canales correctos, sin canales cruzados.
- [ ] LED se inicia apagado.
- [ ] `SET` actualiza mezcla/brillo y rango 0..255.
- [ ] Comando erróneo no modifica salida; publica ERR.
- [ ] `OFF`, `ON`, `GET` y lectura STATE funcionan.
- [ ] Tras desconexión se vuelve a anunciar BLE y mantiene último estado.
- [ ] Flasheo y prueba de hardware documentados con resultados/logs reales.

## Android
- [ ] Runtime permissions correctos para API 26–35+ y Bluetooth deshabilitado.
- [ ] Scan filtra UUID, timeout/cancelación; no duplica dispositivos.
- [ ] Estado BLE íntegro en ViewModel y UI: no simular conexión.
- [ ] GATT serializado, WRITE con respuesta, subscribe CCCD, READ de estado.
- [ ] Sliders y rueda controlan RGB y brillo en tiempo real, sin avalancha de writes.
- [ ] Botones OFF/ON funcionales; reconexión no muestra estado obsoleto.
- [ ] Favoritos persisten, escenas se sincronizan sin internet.
- [ ] Navegación correcta hacia atrás, rotación, modo oscuro/claro.
- [ ] Contraste, accesibilidad TalkBack y tamaños mínimos táctiles.

## Seguridad / lanzamiento
- [ ] No se permite escritura desde un teléfono ajeno no autorizado.
- [ ] Se documenta el flujo de pairing/restablecimiento.
- [ ] CI Android y firmware en verde.
- [ ] APK debug/release compilado y prueba en equipo físico.
- [ ] Evidencia de latencia, reconexión, color y brillo.
- [ ] No secretos ni MAC personales hardcodeadas.

**Estado inicial de este documento: todos pendientes.** No afirmar aprobación sin evidencia.
