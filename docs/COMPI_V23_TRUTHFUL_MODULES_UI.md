# Compi V23 — UI con evidencia real para DHT y RGB

## Evidencia base
DHT11 GPIO16 validado en ESP32 V22: 82/82 flancos, valid=1 y
métricas en Android de 21.1°C / 61% RH. LED RGB PWM no tiene un
sensor de corriente: conectar el LED físicamente no altera status.

## Correcciones (solo Android)
- Encabezado de DHT11/DHT22: responde / GPIO activo sin lecturas /
  último estado (dependiendo de telemetría real).
- La confirmación verde de DHT exige estado firmware **activo y actual**,
  temperatura/humedad **ambas fresh y válidas**, sesión autorizada,
  modelo único activo y dispositivo correcto.
- La cabecera, la tarjeta de la biblioteca y el panel de diagnóstico
  consultan la misma política. Los valores guardados no son "en vivo".
- LED RGB: se reconoce PWM preparado, **no se dice LED presente**.
  No se cambia ningún protocolo ni GPIO; la detección real requeriría
  medición de corriente o circuito de realimentación.
- Botón de Centro de conexión: "Conectar" si no hay vínculo;
  "Gestionar" si BLE o Wi-Fi están conectados. Mantiene la acción existente.
- Gráficas: separadores horizontales, mínimo/máximo de datos válidos;
  estado vacío compacto sin dibujar curvas ficticias.
- Se conservan las mismas pantallas, navegación, tags de test y
  estructura de componentes. Sin cambios al firmware V22.

## QA
Las pruebas de política cubren DHT11 validado, modelos ambiguos,
datos antiguos, mediciones incompletas y el lenguaje PWM para LED.
CI incluye Android, lint, firmware y emuladores API 30/35.
