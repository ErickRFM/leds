# DHT11 diagnostic sketch (standalone)

**Purpose:** isolate physical wiring / sensor / GPIO issues from Compi's
Wi-Fi, BLE, Android app and asynchronous tasks. This diagnostic sketch
temporarily **replaces** Compi's main firmware. Re-flash the normal Compi
firmware afterwards.

## Wiring with ESP32 POWERED OFF

Confirm the manufacturer's **printed labels**, not jumper colors.
- DHT11 module VCC (+): ESP32 **3V3**.
- GND (-): ESP32 **GND**.
- DATA/OUT/S: ESP32 **GPIO16** by default.
- Many three-pin breakout boards include the DATA pull-up. Bare sensors
  need a ~4.7k–10k pull-up from DATA to **3.3V**, never to 5V.
- GPIO16/17 may be internally used on PSRAM/WROVER boards. The sketch
  explicitly refuses to probe those pins when PSRAM is detected.
- Do NOT rewire with the USB or other supply connected.

## Steps
1. In Arduino IDE 2.x, open `tools/dht11_probe/dht11_probe.ino` with its
   sibling `DhtProbeCore.h` in the **same folder**.
2. Board: ESP32 Dev Module, ESP32 Arduino core **3.0.7**, baud **115200**.
3. Upload and open Serial Monitor. The sketch prints:
   `reposo:`, `flancos capturados:`, `bytes:`, and `VEREDICTO:`.
4. Wait ~3 seconds between samples. At least 3 consistent `OK` readings
   are stronger evidence than a single successful packet.
5. If failure persists, POWER OFF, move only DATA to GPIO17 or GPIO32/33
   (only when that GPIO is free), edit `#define DHT_PIN` and re-flash.

## Interpretation
- `OK`: the pin, module, pull-up, and physical wiring worked for this
  isolated transaction. Does not certify the full Compi firmware.
- `SIN_RESPUESTA`: missing DHT sync pulse. Check power, pinout, DATA,
  pull-up, or sensor; not enough evidence to blame the GPIO yet.
- `LINEA_EN_BAJO`: DATA was stuck LOW before the start signal.
- `SINCRONIA_INCOMPLETA`, `TRAMA_INCOMPLETA`: incomplete pulse train,
  possibly noise, wiring, weak pull-up, wrong model or timing.
- `CHECKSUM`: a complete frame arrived but data checksum failed.
- `DATOS_INVALIDOS`: checksum good but not within DHT11 data contract.
- `PIN_RESERVADO_PSRAM`: use GPIO32 or GPIO33.

This standalone probe intentionally **does not mask interrupts for four
milliseconds**. Doing so inside Compi's networked firmware could interfere
with its Wi-Fi/BLE and scheduling; isolate first and consider hardware
capture/peripheral-based approaches for the production driver if needed.
Host waveform tests confirm decoder behavior but cannot replace a
physical ESP32 bench measurement.
