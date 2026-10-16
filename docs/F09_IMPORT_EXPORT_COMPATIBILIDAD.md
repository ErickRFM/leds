# F09 · Bloque 3: compatibilidad entre placas

`core/export/BoardCompatibility.kt`

Determina cómo se relaciona un proyecto exportado con la placa destino. Nunca
asume que un proyecto "funciona sin más": las placas desconocidas se marcan como
`UNKNOWN` y los conflictos de pines deben reasignarse antes de aplicar.

## Niveles

| Nivel | Significado |
| --- | --- |
| `COMPATIBLE` | Todos los componentes y pines son válidos en la placa destino. |
| `REQUIRES_REMAP` | Hay conflictos de pin, pero existe una reasignación segura. |
| `PARTIALLY_COMPATIBLE` | Algunos componentes sirven; otros no son soportados o no se pueden reasignar. |
| `INCOMPATIBLE` | Ningún componente puede aplicarse en la placa destino. |
| `UNKNOWN` | No existe un manifiesto para la placa solicitada. |

## Perfiles de placa

`BoardProfile(id, displayName, supportedTypes, reservedPins, preferredPins)`.

- `assignablePins` = 0..39 menos `reservedPins`.
- Perfiles integrados: `esp32-legacy-v1` (reserva GPIO 6–11 del flash SPI) y
  `esp32-s3-v1`.
- `BoardProfiles.find(id)` devuelve un perfil marcador `UNKNOWN` si no hay
  manifiesto, sin lanzar excepciones.

## Motor y reasignación

- Un componente con `type` no soportado → problema `UnsupportedType`.
- Pines fuera del rango asignable (reservados) → `PinOutOfRange`.
- Pines usados por dos componentes → `PinConflict`.
- La reasignación usa los pines preferidos del tipo y, como respaldo, los pines
  asignables libres. **Nunca** asigna un pin reservado ni uno ya en uso. Si no
  hay suficientes pines seguros, no adivina: lo marca como no reasignable.

## Integración en la importación

- La vista previa de importación muestra el nivel de compatibilidad
  (`project_import_compatibility`) y el número de componentes reasignados.
- Al aplicar, los pines con conflicto se sustituyen por los pines seguros del
  informe.
- Un proyecto `INCOMPATIBLE` se rechaza: no modifica la configuración y muestra
  el motivo.

## Pruebas

`BoardCompatibilityTest` (8): compatible, reasignación sin pines reservados,
conflicto entre dos componentes, tipo no soportado, mixto parcial, placa
desconocida, resolución por id y exclusión de pines reservados. Además,
`PortableProjectViewModelTest` cubre la reasignación al importar y el rechazo de
proyectos incompatibles.

Suite total **224 pruebas** en verde. `lintDebug`, `assembleDebugAndroidTest`,
`assembleDebug` y `check-contracts` (0 fallos) en verde.

APK debug SHA-256: `92ADF2E57AB7FC14013A22E5D6E2FE4E73241A8A3FCE188F7E794148CB33A5A4`
