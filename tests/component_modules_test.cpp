// Host tests for the Compi modular hardware component library.
// Compiled with -std=c++17 -Wall -Wextra -Werror -pedantic; no ESP32 required.
#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>

#include "../firmware/esp32_rgb/core/ComponentContract.h"
#include "../firmware/esp32_rgb/core/ModuleRegistry.h"
#include "../firmware/esp32_rgb/core/PinManager.h"
#include "../firmware/esp32_rgb/core/SensorTelemetry.h"
#include "../firmware/esp32_rgb/core/SensorTelemetryCodec.h"
#include "../firmware/esp32_rgb/modules/Dht22Module.h"
#include "../firmware/esp32_rgb/core/DhtFrameCodec.h"
#include "../firmware/esp32_rgb/modules/OledSsd1306Module.h"
#include "../firmware/esp32_rgb/modules/SoilMoistureModule.h"

using namespace esp32core;

namespace {

struct DhtDouble {
  bool ok = true;
  float humidity = 50.0f;
  float temperature = 22.0f;
  int calls = 0;
};

Dht22Module::Reading readDht(void* context) {
  auto* state = static_cast<DhtDouble*>(context);
  ++state->calls;
  return {state->ok, state->humidity, state->temperature};
}

struct AdcDouble {
  int value = 2048;
  int calls = 0;
};

int readAdc(uint8_t, void* context) {
  auto* state = static_cast<AdcDouble*>(context);
  ++state->calls;
  return state->value;
}

struct OledDouble {
  bool initOk = true;
  bool present = true;
  int initCalls = 0;
  int presentCalls = 0;
  char lastText[OledSsd1306Module::kMaxTextBytes] = {};
};

bool oledInit(uint8_t address, uint8_t sda, uint8_t scl, void* context) {
  auto* state = static_cast<OledDouble*>(context);
  ++state->initCalls;
  return state->initOk && OledSsd1306Module::validAddress(address) && sda != scl;
}

bool oledPresent(uint8_t, const char* text, void* context) {
  auto* state = static_cast<OledDouble*>(context);
  ++state->presentCalls;
  if (!state->present) return false;
  std::strncpy(state->lastText, text, sizeof(state->lastText) - 1);
  return true;
}

}  // namespace

int main() {
  // --- Pure contract metadata ------------------------------------------------
  static_assert(ComponentType::Sensor != ComponentType::Display, "types differ");
  assert(std::strcmp(componentTypeName(ComponentType::Sensor), "sensor") == 0);
  assert(std::strcmp(componentStateName(ComponentState::Detected), "detected") == 0);
  assert(std::strcmp(interfaceName(HardwareInterface::Adc), "adc") == 0);
  assert(std::strcmp(measurementTypeName(MeasurementType::SoilMoisture), "soil_moisture") == 0);
  assert(std::strcmp(measurementStatusName(MeasurementStatus::NotCalibrated), "not_calibrated") == 0);

  // --- Telemetry codec: bounded, NaN-safe, status-preserving ---------------
  {
    Measurement good{MeasurementType::Humidity, MeasurementStatus::Fresh, true, 50.0f, "%", 1234};
    char buffer[160] = {};
    assert(SensorTelemetryCodec::encode(&good, 1, buffer, sizeof(buffer)));
    assert(std::strstr(buffer, "\"type\":\"humidity\"") != nullptr);
    assert(std::strstr(buffer, "\"status\":\"fresh\"") != nullptr);
    assert(std::strstr(buffer, "\"valid\":true") != nullptr);
    assert(std::strstr(buffer, "50.00") != nullptr);

    Measurement nan{MeasurementType::Temperature, MeasurementStatus::Fresh, true, 0.0f, "C", 7};
    nan.value = std::nanf("");
    char encoded[160] = {};
    assert(SensorTelemetryCodec::encode(&nan, 1, encoded, sizeof(encoded)));
    assert(std::strstr(encoded, "\"valid\":false") != nullptr);
    assert(std::strstr(encoded, "\"status\":\"invalid\"") != nullptr);

    char tiny[16] = {};
    assert(!SensorTelemetryCodec::encode(&good, 1, tiny, sizeof(tiny)));
    assert(!SensorTelemetryCodec::encode(nullptr, 0, nullptr, 0));
  }

  // --- Safe stopped-only pin reassignment contract -------------------------
  {
    PinManager pins;
    DhtDouble sensor;
    Dht22Module dht(32, readDht, &sensor);
    SoilMoistureModule soil(34, readAdc, nullptr);
    OledDouble displayDriver;
    OledSsd1306Module display(0x3C, 21, 22, oledInit, oledPresent, &displayDriver);
    assert(dht.begin(pins));
    assert(soil.begin(pins));
    assert(display.begin(pins));
    assert(!dht.configurePin(18)); // active module cannot move
    assert(!soil.configurePin(35));
    assert(!display.configurePins(18, 19));
    dht.stop(pins);
    assert(!dht.configurePin(25)); // RGB reserved even when not leased
    assert(!dht.configurePin(34)); // input-only DHT rejected
    assert(dht.configurePin(18));
    assert(dht.pin() == 18);
    assert(dht.begin(pins));
    assert(pins.belongsTo(18, dht.id()));
    assert(soil.setCalibration(3850, 950));
    assert(soil.calibrated());
    soil.stop(pins);
    assert(!soil.configurePin(25));
    assert(!soil.configurePin(19)); // no ADC1 on GPIO19
    assert(soil.configurePin(34));  // same physical source preserves calibration
    assert(soil.calibrated());
    assert(soil.configurePin(35));  // new probe requires new dry/wet reference
    assert(!soil.calibrated());
    assert(soil.moistureReading().status == MeasurementStatus::NotCalibrated);
    assert(!soil.moistureReading().valid);
    assert(soil.begin(pins));
    assert(pins.belongsTo(35, soil.id()));
    soil.applySample(2500, 1000);
    assert(!soil.moistureReading().valid); // never show a stale percentage
    display.stop(pins);
    assert(!display.configurePins(19, 19));
    assert(!display.configurePins(25, 23));
    assert(display.configurePins(22, 23));
    assert(display.begin(pins));
    assert(pins.belongsTo(22, display.id()));
    assert(pins.belongsTo(23, display.id()));
    dht.stop(pins);
    soil.stop(pins);
    display.stop(pins);
    assert(pins.leasedCount() == 0);
  }

  // DHT11 and DHT22 are NOT interchangeable. Test real wire payloads.
  {
    const uint8_t dht11[5] = {55, 0, 24, 0, 79};
    const auto r11 = decodeDhtFrame(dht11, DhtModel::DHT11);
    assert(r11.ok && r11.humidity == 55.0f && r11.temperature == 24.0f);
    assert(!decodeDhtFrame(dht11, DhtModel::DHT22).ok);
    const uint8_t dht22[5] = {0x02, 0x2b, 0x00, 0xef, 0x1c};
    const auto r22 = decodeDhtFrame(dht22, DhtModel::DHT22);
    assert(r22.ok && r22.humidity == 55.5f && r22.temperature == 23.9f);
    assert(!decodeDhtFrame(dht22, DhtModel::DHT11).ok);
    const uint8_t negative[5] = {0x01, 0xf4, 0x80, 0x7b, 0xf0};
    const auto minus = decodeDhtFrame(negative, DhtModel::DHT22);
    assert(minus.ok && minus.temperature == -12.3f);
    uint8_t bad[5] = {55, 0, 24, 0, 78};
    assert(!decodeDhtFrame(bad, DhtModel::DHT11).ok);
    (void)bad;
    PinManager gpio;
    DhtDouble sensor;
    Dht22Module dht11(16, readDht, &sensor, 2000, "sensor.dht11");
    assert(dht11.begin(gpio));
    assert(gpio.belongsTo(16, "sensor.dht11"));
    dht11.tick(1000);
    assert(dht11.humidityReading().valid);
    dht11.stop(gpio);
    assert(gpio.lease(16) == nullptr);
  }

  // --- DHT22 ---------------------------------------------------------------
  {
    PinManager pins;
    assert(!Dht22Module(25, readDht, nullptr).begin(pins));  // RGB pin refused
    assert(!Dht22Module(34, readDht, nullptr).begin(pins));  // input-only refused
    assert(!Dht22Module(18, nullptr, nullptr).begin(pins));  // no reader refused

    DhtDouble sensor;
    Dht22Module dht(32, readDht, &sensor, 2000);
    assert(pins.lease(32) == nullptr);
    assert(dht.begin(pins));
    assert(!dht.begin(pins));  // double begin refused
    assert(pins.belongsTo(32, "sensor.dht22"));

    dht.tick(1000);  // first attempt always samples
    assert(sensor.calls == 1);
    assert(dht.humidityReading().status == MeasurementStatus::Fresh);
    assert(dht.humidity() == 50.0f);
    assert(dht.temperature() == 22.0f);
    dht.tick(1500);  // inside min interval, no new sample
    assert(sensor.calls == 1);
    dht.tick(3500);
    assert(sensor.calls == 2);

    sensor.ok = false;
    dht.tick(6000);
    assert(dht.status() == MeasurementStatus::Stale);  // one failure, not yet fatal
    assert(!dht.humidityReading().valid);
    dht.tick(9000);
    dht.tick(12000);
    assert(dht.status() == MeasurementStatus::SensorError);  // kMaxFailures reached

    sensor.ok = true;
    dht.tick(15000);
    assert(dht.consecutiveFailures() == 0);
    assert(dht.status() == MeasurementStatus::Fresh);

    sensor.humidity = 250.0f;  // out-of-range payload must be rejected
    dht.tick(18000);
    assert(dht.status() == MeasurementStatus::Stale);

    dht.stop(pins);
    assert(!dht.ready());
    assert(pins.lease(32) == nullptr);
  }

  // --- Soil moisture -------------------------------------------------------
  {
    PinManager pins;
    assert(!SoilMoistureModule(18, readAdc, nullptr).begin(pins));  // not ADC1

    AdcDouble adc;
    SoilMoistureModule soil(34, readAdc, &adc, 1000);
    assert(soil.begin(pins));
    assert(pins.belongsTo(34, "sensor.soil_moisture"));

    // Without calibration we expose raw only, never a percentage.
    assert(soil.applySample(3000, 100));
    assert(soil.moistureReading().status == MeasurementStatus::NotCalibrated);
    assert(!soil.moistureReading().valid);
    assert(soil.rawReading().valid);
    assert(soil.rawReading().value == 3000.0f);
    assert(!soil.calibrated());

    // Calibration rejects equal or out-of-range reference points.
    assert(!soil.setCalibration(2000, 2000));
    assert(!soil.setCalibration(5000, 1000));
    assert(soil.setCalibration(4095, 0));
    assert(soil.calibrated());

    // Dry reference (4095) converges near 0%, wet reference (0) near 100%.
    for (int i = 0; i < 40; ++i) soil.applySample(4095, 200 + i);
    assert(std::fabs(soil.moistureReading().value - 0.0f) < 1.0f);
    assert(soil.moistureReading().valid);
    assert(soil.band() == SoilMoistureModule::Band::Dry);
    // Feed several samples toward the wet end so the EMA converges.
    for (int i = 0; i < 40; ++i) soil.applySample(0, 300 + i);
    assert(soil.moistureReading().value > 95.0f);
    assert(soil.moistureReading().valid);
    assert(soil.band() == SoilMoistureModule::Band::Wet);

    // Tick path uses the injected reader and honours the sample interval.
    const int before = adc.calls;
    soil.tick(10000);
    assert(adc.calls == before + 1);
    soil.tick(10500);  // inside interval
    assert(adc.calls == before + 1);

    assert(SoilMoistureModule::clampRaw(-5) == 0);
    assert(SoilMoistureModule::clampRaw(9999) == SoilMoistureModule::kAdcMax);

    soil.clearCalibration();
    soil.applySample(1000, 20000);
    assert(soil.moistureReading().status == MeasurementStatus::NotCalibrated);

    soil.stop(pins);
    assert(pins.lease(34) == nullptr);
  }

  // --- OLED SSD1306 --------------------------------------------------------
  {
    PinManager pins;
    OledDouble oled;
    OledSsd1306Module display(0x3C, 21, 22, oledInit, oledPresent, &oled, 250);
    assert(!display.setText(nullptr));
    assert(!display.setText("0123456789012345678901234567890123456789"));  // too long
    assert(display.begin(pins));
    assert(pins.belongsTo(21, "display.ssd1306"));
    assert(pins.belongsTo(22, "display.ssd1306"));

    assert(display.setText("Compi V15"));
    assert(display.pending());
    display.tick(1000);  // first refresh always runs
    assert(oled.presentCalls == 1);
    assert(!display.pending());
    assert(display.confirmed());
    assert(std::strcmp(oled.lastText, "Compi V15") == 0);

    assert(display.setText("Humedad 42%"));
    display.tick(1100);  // inside refresh interval
    assert(oled.presentCalls == 1);
    display.tick(1400);
    assert(oled.presentCalls == 2);
    assert(std::strcmp(oled.lastText, "Humedad 42%") == 0);

    oled.present = false;
    assert(display.setText("fallo"));
    assert(!display.refreshNow(2000));
    assert(!display.confirmed());
    assert(display.pending());  // still pending after a failed confirmation

    display.stop(pins);
    assert(pins.lease(21) == nullptr);
    assert(pins.lease(22) == nullptr);

    // A driver that fails to initialise must release the I2C pins.
    PinManager pins2;
    OledDouble bad;
    bad.initOk = false;
    OledSsd1306Module broken(0x3C, 21, 22, oledInit, oledPresent, &bad, 250);
    assert(!broken.begin(pins2));
    assert(pins2.lease(21) == nullptr);
    assert(pins2.lease(22) == nullptr);
    assert(!OledSsd1306Module::validAddress(0x3B));

    // A pin already leased by another module must block the OLED (default SDA
    // GPIO21 / SCL GPIO22, address 0x3C) without stealing or leaking it.
    PinManager pins3;
    assert(pins3.acquire(21, "led.rgb", PinFunction::PwmOutput));
    OledDouble oledConflict;
    OledSsd1306Module conflict(0x3C, 21, 22, oledInit, oledPresent, &oledConflict, 250);
    assert(!conflict.begin(pins3));
    assert(!conflict.ready());
    assert(pins3.belongsTo(21, "led.rgb"));  // the OLED never took GPIO21
    assert(pins3.lease(22) == nullptr);      // and never held SCL after failing
  }

  // --- Registry integration + system pin reservations ----------------------
  {
    PinManager pins;
    assert(pins.registerSystemPin(27, "led.rgb"));
    assert(pins.registerSystemPin(25, "led.rgb"));
    assert(pins.registerSystemPin(26, "led.rgb"));
    assert(!pins.registerSystemPin(27, "other"));  // already reserved
    assert(pins.isSystemReserved(27));
    assert(std::strcmp(pins.systemOwner(27), "led.rgb") == 0);
    // A different owner can never acquire a reserved pin, but the owner can.
    assert(!pins.acquire(27, "sensor.dht22", PinFunction::DigitalInput));
    assert(pins.acquire(27, "led.rgb", PinFunction::PwmOutput));
    pins.release(27, "led.rgb");

    DhtDouble sensor;
    Dht22Module dht(32, readDht, &sensor, 2000);
    AdcDouble adc;
    SoilMoistureModule soil(34, readAdc, &adc, 1000);
    OledDouble oled;
    OledSsd1306Module display(0x3C, 21, 22, oledInit, oledPresent, &oled, 250);

    ModuleRegistry registry;
    assert(registry.registerModule(&dht));
    assert(registry.registerModule(&soil));
    assert(registry.registerModule(&display));
    assert(!registry.registerModule(nullptr));
    assert(!registry.registerModule(&dht));  // duplicate id refused
    assert(registry.size() == 3);
    assert(registry.find("sensor.dht22") == &dht);
    assert(registry.find("missing") == nullptr);
    assert(registry.startAll(pins));
    assert(pins.belongsTo(32, "sensor.dht22"));
    assert(pins.belongsTo(34, "sensor.soil_moisture"));
    registry.tickAll(1000);
    assert(sensor.calls == 1);
    assert(adc.calls == 1);
    assert(oled.presentCalls == 0);  // nothing to present yet
    registry.stopAll(pins);
    assert(pins.leasedCount() == 0);
  }

  // A registry whose module fails to begin must roll back every prior claim.
  {
    PinManager pins;
    DhtDouble sensor;
    Dht22Module ok(32, readDht, &sensor, 2000);
    AdcDouble adc;
    SoilMoistureModule conflicts(32, readAdc, &adc, 1000);  // same pin -> begin fails
    ModuleRegistry registry;
    assert(registry.registerModule(&ok));
    assert(registry.registerModule(&conflicts));
    assert(!registry.startAll(pins));
    assert(pins.leasedCount() == 0);
    assert(!ok.ready());
  }

  // --- Safe boot: optional OLED absent, essential service remains operational ---
  {
    PinManager pins;
    DhtDouble sensor;
    Dht22Module essential(32, readDht, &sensor);
    OledDouble absent;
    absent.initOk = false;
    OledSsd1306Module optionalDisplay(0x3C, 21, 22, oledInit, oledPresent, &absent);
    ModuleRegistry registry;
    assert(registry.add(essential));
    assert(registry.add(optionalDisplay));
    assert(registry.startRequiredAndOptional(pins, 1));
    assert(registry.started());
    assert(registry.unavailableAtBoot() == 1);
    assert(essential.ready());
    assert(!optionalDisplay.ready());
    assert(pins.belongsTo(32, essential.id()));
    assert(!pins.lease(21) && !pins.lease(22));
    registry.tickAll(1000);
    assert(sensor.calls == 1);  // required module stays operational
    // Plug in the OLED later: the same registered driver can start and tick.
    absent.initOk = true;
    assert(optionalDisplay.begin(pins));
    assert(optionalDisplay.ready());
    assert(pins.belongsTo(21, optionalDisplay.id()));
    registry.stopAll(pins);
    assert(!registry.started());
    assert(pins.leasedCount() == 0);
  }

  // A failed REQUIRED first module must still abort, not silently expose
  // healthy control services with the RGB core missing.
  {
    PinManager pins;
    OledDouble absent;
    absent.initOk = false;
    OledSsd1306Module required(0x3C, 21, 22, oledInit, oledPresent, &absent);
    DhtDouble sensor;
    Dht22Module optional(32, readDht, &sensor);
    ModuleRegistry registry;
    assert(registry.add(required));
    assert(registry.add(optional));
    assert(!registry.startRequiredAndOptional(pins, 1));
    assert(!registry.started());
    assert(pins.leasedCount() == 0);
  }

  std::cout << "component library host tests: ALL OK\n";
  return 0;
}
