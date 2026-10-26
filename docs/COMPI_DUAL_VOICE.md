# Compi Dual Voice — implementación incremental

## Arquitectura y límites

Las dos voces **no se sustituyen entre sí**. La voz física ya existente pertenece al firmware ESP32 (EnvironmentalVoicePolicy y DFPlayerRuntime), y usa la microSD del módulo UART2 RX=13/TX=14. La voz Android es un motor independiente TextToSpeech que consume únicamente telemetría autorizada, reciente y válida a través del repositorio compartido. No envía órdenes de audio al DFPlayer.

| Situación | Resultado previsto |
| --- | --- |
| DFPlayer con SD y voz física habilitada, teléfono con voz habilitada | Ambas salidas anuncian los cambios que sus respectivos motores detecten |
| Sin microSD, celular conectado con voz habilitada | Solo Android habla |
| Sin Android conectado, DFPlayer con microSD y voz física habilitada | Solo ESP32 habla |
| Ambas salidas ausentes o deshabilitadas | No se reproduce audio; lectura de sensores sigue |
| BLE y Wi-Fi simultáneos | La app consulta solo el transporte seleccionado para evitar doble lectura |

**Restricción importante:** los motores ESP32 y Android **no comparten todavía un ID único de evento** ni una sincronización de audio exacta. Las frases y umbrales pueden diferir; 'ambos' no garantiza la misma frase en el mismo instante. Por ahora es doble salida independiente, no coordinación determinista.

## Preparación Android

En Ajustes, activar "Voz automática del celular". Se requiere un motor TextToSpeech con idioma español instalado. La voz del teléfono funciona sin microSD; para usarla sin Internet, el motor TTS debe ofrecer idioma español descargado localmente. La app lee telemetría cada 3 s cuando hay sesión autorizada. Con la app terminada o con el proceso detenido no se garantiza voz: un servicio persistente de segundo plano queda pendiente.

## Preparación ESP32

Usar el firmware correspondiente al mismo commit. Audio físico: DFPlayer alimentado correctamente con microSD FAT32 y archivos `/MP3/0001.mp3` en adelante; UART2 RX13/TX14. En la UI de audio físico habilitar la voz automática correspondiente al ESP32; se almacena en NVS. El firmware **no arranca con voz automática habilitada por defecto** si no se configuró. El ESP32 continúa midiendo aun sin tarjeta.

## Validación de laboratorio

1. Con firmware y APK del mismo commit, comprobar sensor DHT11 GPIO16 y LDR calibrado GPIO32.
2. Cambiar de 23 a 27 °C y esperar un solo anuncio del teléfono.
3. Hacer un cambio brusco de luz desde estado claro a oscuro; mantenerlo al menos un segundo y comprobar un anuncio.
4. Retirar la tarjeta: verificar que el teléfono continúa y que el ESP32 no bloquea otros módulos.
5. Cerrar la conexión Android: verificar físicamente que el DFPlayer mantiene su operación.
6. Confirmar que cambiar de pestaña no suspende las consultas cuando la sesión sigue autorizada.
7. Comprobar en dispositivo físico el sonido, voz española, batería, conexión y permisos.

El CI valida compilación y pruebas de software, no la audibilidad real ni la calidad del sensor. No afirmar funcionamiento remoto fuera de alcance de BLE/SoftAP.
