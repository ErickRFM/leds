# RGB Controller · ESP32 + Android Kotlin

Control local de iluminación RGB mediante Bluetooth Low Energy (BLE).

## Objetivo
Crear una app Android nativa en Kotlin/Jetpack Compose para controlar un LED RGB conectado al ESP32, sin conexión a internet ni servicios cloud.

## Componentes
- **Android (frontend):** pantalla de dispositivos, rueda RGB, brillo, colores predefinidos, escenas y efectos.
- **ESP32 (backend embebido):** servidor GATT BLE, protocolo de comandos, motor de efectos y PWM.
- **Hardware:** LED RGB de 4 patitas, tres resistencias y ESP32 (GPIO 27/25/26).
- **Documentación:** arquitectura, protocolo, cableado y plan maestro.

## Estado
Proyecto en fase de fundación. No hay APK ni firmware validado en hardware físico todavía.

El desarrollo se lleva por ramas, PRs y criterios de aceptación verificables; consultar `docs/PLAN_MAESTRO.md` una vez integrado.
