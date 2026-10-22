# Compi — DHT11 / DHT22 (ESP32 clásico)

El firmware incluye **ambos protocolos** DHT: DHT11 (pulso de inicio ≥18 ms,
humedad/temperatura en bytes enteros y decimales) y DHT22/AM2302 (inicio 2 ms,
lecturas con resolución 0.1). No son intercambiables y requieren el modelo
correcto en la aplicación.

## Cableado seguro
Desconectar USB antes de mover cables. Confirmar el orden **real de pines**
impreso en el módulo: **3V3 a VCC (+)**, **GND a GND (-)** y **DATA a GPIO16**
para el DHT11 de prueba. No inferir el pinout de los colores de los jumpers ni
conectar DATA a 5V. Verificar resistencia pull-up a 3.3V (muchos módulos de
tres pines ya traen una; en sensor desnudo usar ~4.7k–10k).

## Firmware y activación
- `COMPI_BUILD_DHT11=1` y `COMPI_BUILD_DHT22=1` agregan ambos drivers.
- En este perfil de banco, `COMPI_ENABLE_DHT11=1` inicia DHT11 en **GPIO16**;
  su ausencia **no produce lecturas inventadas ni bloquea BLE/SoftAP**.
- `COMPI_ENABLE_DHT22=0`: puede activarse mediante `/v2/components/apply`
  desde Compi por **Wi-Fi autorizado**, en GPIO32 por defecto u otro permitido.
- El firmware permite **un modelo DHT activo a la vez**. Cambiar entre DHT11
  y DHT22 transfiere el GPIO de forma transaccional y revierte si falla.
- Toda reasignación realizada desde la app es temporal: tras reiniciar, el
  firmware vuelve a los valores configurados en la compilación.
- Estado `active` indica driver y lease GPIO activos, **no confirma una
  respuesta física del sensor**. Confirmar `fresh` / `valid=true` en
  `GET /v2/components/telemetry`. Si no hay lecturas, revisar alimentación,
  tipo de sensor, DATA/GPIO y pull-up.

## App
Elige el módulo DHT11 o DHT22, verifica la conexión Wi-Fi del ESP32 y
consulta telemetría. Los sensores se obtienen por la API Wi-Fi local,
**no por la conexión BLE de control RGB**. Sin Wi-Fi autorizado no debe
mostrarse una lectura obsoleta como en vivo.

Si el físico es un DHT22 en GPIO16, abre su asistente GPIO y aplica 16
por Wi-Fi: el firmware liberará el DHT11 y activará el DHT22, con lectura
de confirmación de pin independiente.
