# Compi OLED — prueba real (PR #181)

## Hardware

SSD1306 I²C 128×64, VCC 3V3, GND GND, SDA GPIO21, SCL GPIO22, dirección 0x3C.
Requiere **Adafruit SSD1306** y **Adafruit GFX Library** (más dependencias del gestor Arduino) antes de compilar. Sin la librería SSD1306, el firmware omite la característica y no debe declarar la pantalla como activa.

La compilación usa `COMPI_ENABLE_OLED=1` y sólo registra el módulo si el driver existe. El arranque opcional debe tolerar OLED ausente sin interrumpir las conexiones. El OLED comparte los GPIO21/22 con otros usos del laboratorio: verificar conflictos en `PinManager` antes del flasheo.

## Prueba en el dispositivo

1. Compilar **APK debug y firmware de un mismo commit**, esquema ESP32 Dev Module / Minimal SPIFFS 1.9 MB with OTA.
2. Instalar firmware con Arduino IDE; revisar uso de flash y mensajes serie a 115200.
3. Conectar pantalla 3.3 V; reiniciar y observar «Hola, soy Compi!». Si no aparece, comprobar librerías, I²C 0x3C/0x3D y leases GPIO. No declarar activo sin evidencia.
4. Conectar Android a `ESP32-Control-Lab`, autorizar la clave local y entrar a Hardware Lab → Sensores → Pantalla OLED.
5. Enviar “Hola” y luego Feliz, Sueño, Enojo, Sorpresa; verificar **cada cambio físicamente**. `DISPLAYED` significa que el driver ejecutó el refresco, **no confirma píxeles visibles**.
6. Probar sin Wi-Fi / clave errónea / módulo ausente / texto inválido: sin falsas confirmaciones y sin modificar RGB.
7. Verificar DHT11 GPIO16, LDR GPIO36, SoftAP, BLE autorizado y RGB antes/después del uso OLED.

## Límites

* Las caras son **estáticas**, sin biblioteca RoboEyes integrada aún.
* La transmisión se realiza por el endpoint Wi-Fi local autorizado, no por GATT BLE.
* Sólo ASCII 32–126 de hasta 39 caracteres; no se envían fotografías ni animaciones desde Android.
* No se confirma estabilidad de hardware sin pruebas reales, y un APK de CI no equivale a una instalación probada.
