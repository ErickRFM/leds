# Compi — Sensores con métricas y gráficas reales (V20)

## DHT11 / DHT22
La tarjeta ambiental muestra temperatura en °C, humedad relativa en %,
diagnóstico independiente del lease GPIO y gráficas de **5, 15 y 30 minutos**.
Se muestran guiones, nunca cifras inventadas, cuando no existe lectura válida.
Las tarjetas se muestran ANTES de la vista de conexiones GPIO.

## Integridad
- La API de sensores sigue siendo Wi-Fi local autorizada; BLE RGB no habilita
  mágicamente telemetría DHT.
- El firmware informa `sensor.dht11` y `sensor.dht22` por separado, pero ambos
  envían los tipos `humidity` y `temperature`. Para asignar historial se
  utiliza el único lease activo del reporte firmware actual. Si falta evidencia,
  la serie se marca `unattributed` y no aparece falsamente en DHT22.
- Las gráficas aíslan modelo y dispositivo. Un punto inválido, sin checksum o
  antiguo no se promociona a lectura actual.
- El driver activo y GPIO16 confirmado NO demuestran que el sensor responda;
  solo `fresh` con `valid=true` para temperatura y humedad es evidencia de
  una lectura física decodificada.
- Historial local acotado a 240 muestras por serie (máximo 2000 en total).
  Los valores guardados se muestran como históricos, no como conexión en vivo.
- La humedad del suelo conserva su detalle de calibración y añade gráfica.

## Protocolo para prueba física del DHT11
1. ESP32 desconectado de USB: confirmar VCC, GND, DATA según serigrafía del
   módulo. VCC a 3V3, GND a tierra, DATA a GPIO16.
2. Subir firmware y APK **de la misma revisión de CI**.
3. Conectar Wi-Fi autorizado del ESP32 y entrar a Módulos > DHT11.
4. Tocar Actualizar lectura; comprobar temperatura y humedad distintas de --.
5. Esperar 2–3 lecturas válidas: deben aparecer puntos reales en ambas gráficas.
6. Si driver activo pero no hay datos, comprobar pull-up de DATA a 3.3V,
   continuidad de cables, orden real de pines y si el sensor es DHT11.
7. Desconectar Wi-Fi: la gráfica conserva el historial, pero indica no en vivo.
