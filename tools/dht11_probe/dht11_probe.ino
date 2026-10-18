// Compi DHT11 standalone diagnostic. NO Wi-Fi, BLE, GPIO scripts or app.
// USB serial 115200. The ESP32 must be powered OFF while changing wires.
// DHT11 VCC -> 3V3, GND -> GND, DATA -> GPIO16 (or edited DHT_PIN).
// Confirm pin names on your module: jumper colors do NOT guarantee polarity.
#include <Arduino.h>
#include "DhtProbeCore.h"
#ifndef DHT_PIN
#define DHT_PIN 16
#endif

struct ProbeIo {
  bool level() const { return digitalRead(DHT_PIN) == HIGH; }
  uint32_t micros() const { return ::micros(); }
  void spin() const { delayMicroseconds(1); }
  void release() const { pinMode(DHT_PIN, INPUT_PULLUP); }
  void driveLowMs(uint16_t milliseconds) const {
    digitalWrite(DHT_PIN, LOW);
    pinMode(DHT_PIN, OUTPUT);
    delay(milliseconds);
  }
};

static void printCapture(const compi_probe::Capture& c) {
  Serial.printf("flancos capturados: %u/82; sync LOW=%uus HIGH=%uus\n",
      static_cast<unsigned>(c.edges),
      static_cast<unsigned>(c.responseLowUs),
      static_cast<unsigned>(c.responseHighUs));
  Serial.printf("bytes: %02X %02X %02X %02X %02X\n",
      c.data[0], c.data[1], c.data[2], c.data[3], c.data[4]);
  if (c.verdict == compi_probe::Verdict::Ok) {
    Serial.printf("VEREDICTO: OK humedad=%.1f%% temperatura=%.1fC\n",
                  c.humidity, c.temperature);
  } else {
    Serial.printf("VEREDICTO: %s\n", compi_probe::verdictName(c.verdict));
  }
}
void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println("=== Compi DHT11 probe AISLADO ===");
  Serial.printf("GPIO configurado: %u\n", static_cast<unsigned>(DHT_PIN));
  Serial.println("No usa Wi-Fi, BLE, Compi, ni dependencias de terceros.");
}
void loop() {
  // WROVER boards may internally route PSRAM through GPIO16/17.
  if (psramFound() && (DHT_PIN == 16 || DHT_PIN == 17)) {
    Serial.println("reposo: NO_PROBAR; GPIO16/17 ocupado por PSRAM");
    Serial.println("VEREDICTO: PIN_RESERVADO_PSRAM; mover DATA a GPIO32 o 33");
    delay(3000);
    return;
  }
  ProbeIo io;
  io.release();
  delay(2);
  Serial.printf("reposo: %s (GPIO%u)\n",
      io.level() ? "ALTO" : "BAJO", static_cast<unsigned>(DHT_PIN));
  // No print or dynamic allocation during the ~4ms pulse measurement.
  const compi_probe::Capture result = compi_probe::sample(io);
  printCapture(result);
  delay(3000); // DHT11 needs >=1s between transactions.
}
