# Compi — LDR analógico en GPIO32

La placa del usuario expone GPIO32 y no GPIO36. Usar **GPIO32 (ADC1)** como entrada analógica del sensor de luz.

| Módulo LDR | ESP32 |
| --- | --- |
| VCC | 3V3 |
| GND | GND |
| AO / A0 (salida analógica) | **GPIO32** |
| DO / D0 | Sin conectar |

**No usar 5 V** en un módulo cuyo AO vaya al ESP32. DHT11 usa GPIO16, OLED SDA21/SCL22, RGB 27/25/26; GPIO32 queda destinado al LDR. No confundir el pin físico de la placa con la etiqueta GPIO. Comprobar la serigrafía antes de energizar.

El valor `COMPI_LDR_PIN` del firmware pasa a 32. La inicialización del LDR permanece deliberadamente opt-in (`COMPI_ENABLE_LDR=0`) para no afirmar que un sensor está conectado ni inventar valores. Habilitarlo mediante la configuración autorizada y confirmar telemetría real / calibración (oscuridad y claridad) antes de usar umbrales ambientales. Si un perfil guardado configuró el LDR anteriormente en GPIO36, reasignar la conexión a GPIO32 y volver a aplicar la configuración desde Hardware Lab.
