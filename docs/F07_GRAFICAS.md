# F07 · Bloque 2: gráficas de telemetría

Segundo bloque de F07. Consume el historial del bloque 1
(`TelemetryHistory`) y no duplica codecs ni transportes.

## Qué se añadió

- `core/components/telemetry/TelemetryChart.kt` — geometría pura, sin Android:
  - `SampleQuality` (`VALID` / `INVALID` / `STALE`) y `ClassifiedSample`.
  - `TelemetryChart.build(...)`: filtra a una ventana temporal, clasifica cada
    muestra, normaliza `fractionX` (tiempo) y `fractionY` (valor) y agrupa los
    puntos válidos en segmentos. Un hueco entre puntos válidos produce **varios
    segmentos** (`hasGap`), nunca una línea que cruce un dato inválido.
  - Reglas: `stale` explícito del firmware o muestra más antigua que
    `staleAfterMs` ⇒ `STALE`; no graficable ⇒ `INVALID`; solo validada y finita
    ⇒ `VALID`.

- `core/components/widgets/TelemetryChartWidget.kt` — `TelemetryChartCard` con
  `Canvas`: polilínea por segmento, marcadores de inválidas/antiguas en la línea
  base, leyenda de recuentos, rango y aviso de "desconectado" o "sin datos".

- `HardwareLabScreen`: sección "Gráficas (ventana de 30 min)" con tres tarjetas
  (temperatura ambiental, humedad ambiental, humedad del suelo), alimentadas por
  `state.series(...)` y con el flag `disconnected` tomado de `state.connected`.

## Invariantes

- La gráfica nunca une un dato válido con otro a través de un inválido/antiguo.
- No se dibuja nada como "en vivo" si el enlace no está autorizado/conectado.
- El eje Y se normaliza al rango observado; una serie constante se centra.

## Pruebas

`TelemetryChartTest` (6): segmento único, interrupción por dato inválido,
antiguos no graficables, fuera de ventana, propagación de desconexión y
clasificación que nunca asciende un dato crudo/sin calibrar.

## Pendiente

Alertas con histéresis (bloque 3), automatizaciones (bloque 4), plantillas
deshabilitadas y calibración persistente de suelo (bloque 5), y validación
física (`PENDING_HARDWARE_VALIDATION`).
