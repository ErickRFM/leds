# Contrato Bluetooth Low Energy GATT — RGB Controller v1

Nombre anunciado: `RGB-ESP32` (ESP32 clásico con Bluetooth LE).

| Entidad | UUID | Propiedades |
|---|---|---|
| Servicio | `4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211` | Discover |
| Comando | `4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211` | WRITE with response |
| Estado | `4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211` | READ + NOTIFY, CCCD 0x2902 |

Todos los comandos se escriben como texto ASCII/UTF-8, sin nueva línea, hasta 48 bytes.

## Comandos
- `SET,255,120,0,200` asigna R,G,B,brillo (0..255), enciende y detiene efecto.
- `OFF`: apaga temporalmente.
- `ON`: enciende; conserva mezcla y modo previo.
- `GET`: vuelve a publicar el estado.
- `FX,RAINBOW,120`: arcoíris; velocidad 10..1000.
- `FX,BREATHE,120`: respiración; velocidad 10..1000.
- `FX,FADE,120`: fundido de colores; velocidad 10..1000.
- `FX,STOP`: detiene cualquier efecto y vuelve a color estático.

## Respuesta READ / NOTIFY
`STATE,r,g,b,brillo,power,mode,speed`, donde power es 0 ó 1 y mode es NONE,RAINBOW,BREATHE,FADE.
Ejemplo: `STATE,255,120,0,200,1,RAINBOW,120`.

Los clientes pueden admitir el estado anterior de 6 campos, pero el firmware nuevo publica 8.
Para un comando inválido se notifica `ERR,INVALID_COMMAND`; no modifica el estado del LED.

## Sincronización
Android habilita notificaciones mediante CCCD y espera confirmación; después lee STATE. Solo cuando READ termina satisfactoriamente declara GATT Ready. Todas las operaciones GATT deben serializarse y el estado de la UI debe distinguir cambios locales de valores confirmados.

## Seguridad y limitaciones
Firmware **experimental**: no incluye emparejamiento seguro ni autorización BLE. Cualquier cliente BLE cercano que conozca el UUID podría enviar comandos. Antes de distribución fuera de laboratorio, implementar bonding/enrolamiento de dispositivo y autorización de escrituras; no asumir que la cercanía protege el dispositivo.

El ESP32 ejecuta efectos autónomamente, aunque Android salga de primer plano. No hay nube ni servidor HTTP.
