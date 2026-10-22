# Compi Estación — DFPlayer integration contract (staged)

This directory contains a **GPIO-free** temperature/humidity announcement policy and integration guidance. It does not replace the existing ESP32 sketch and is not proof of DFPlayer detection.

## MP3 file convention

Use FAT32 microSD with files `/MP3/0001.mp3` through `/MP3/0024.mp3`. Play the explicit MP3-folder track number (e.g. 15 for startup), not filesystem enumeration indexes. 1/3/5/7/9 represent cold/comfortable/warm/hot/extreme. 11–14 represent humidity. Other tracks can be selected manually.

## Integration requirements

1. Confirm current firmware pin map **before selecting UART RX/TX**. Already observed: DHT11 GPIO16, LDR GPIO32, SSD1306 I2C GPIO21/22, RGB GPIO27/25/26. Do not allocate those blindly.
2. Probe DFPlayer via a nonblocking serial state machine; distinguish `uartResponding`, `sdReady`, `playing`, `error`, and `notConfigured`. UART respond does not imply SD readiness. Include bounded timeouts and reconnection/backoff.
3. Play environmental announcements only when card is verified ready, not during requested/manual playback. Queue with priority to avoid interruptions.
4. Expose the same state, volume [0..30], play track [1..24], stop and enable/disable auto voice via existing authenticated BLE and Wi-Fi transport mechanisms. Acknowledge actual device state, not optimistic UI.
5. Show a single DFPlayer tile in Hardware Lab with explicit module/card states and commands. Do not repurpose sensor discovery into false-positive module detection.
6. Validate ESP32 flash size/partition use, Android tests, sensor+OLED+BLE+Wi-Fi operation, then check module and microSD physically before merge.

The audio files are distributed separately as `Compi_Audios_DFPlayer_v1.zip`; binary MP3 files are intentionally not inserted via a text-only GitHub commit.
