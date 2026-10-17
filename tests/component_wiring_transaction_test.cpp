// V17 wiring changes, host-verified without an ESP32 or unsafe physical writes.
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include "../firmware/esp32_rgb/core/ComponentWiringTransaction.h"
#include "../firmware/esp32_rgb/modules/Dht22Module.h"
#include "../firmware/esp32_rgb/modules/SoilMoistureModule.h"

using namespace esp32core;

static Dht22Module::Reading fakeDht(void*) { return {true, 50.f, 22.f}; }
static int fakeAdc(uint8_t, void*) { return 2000; }

struct FaultModule {
  uint8_t current = 18;
  bool isReady = false;
  bool failNew = true;
  const char* id() const { return "sensor.dht22"; }
  uint8_t pin() const { return current; }
  bool ready() const { return isReady; }
  bool setPin(uint8_t p) { if (isReady) return false; current = p; return true; }
  bool begin(PinManager& manager) {
    if (failNew && current == 19) return false;
    isReady = manager.acquire(current, id(), PinFunction::DigitalInput);
    return isReady;
  }
  void stop(PinManager& manager) {
    if (isReady) manager.release(current, id());
    isReady = false;
  }
};

int main() {
  assert(std::strcmp(wiringOutcomeName(WiringOutcome::RolledBack), "rolled_back") == 0);
  assert(!wiringPinAllowed("led_rgb", 27));
  assert(!wiringPinAllowed("dht22", 34));
  assert(!wiringPinAllowed("dht22", 25));
  assert(wiringPinAllowed("dht22", 16));
  assert(wiringPinAllowed("soil_moisture", 39));
  assert(!wiringPinAllowed("soil_moisture", 25));
  assert(!wiringPinAllowed("soil_moisture", 18));
  assert(!wiringPinAllowed("unknown", 18));

  PinManager pins;
  pins.registerSystemPin(25, "led.rgb");
  pins.registerSystemPin(26, "led.rgb");
  pins.registerSystemPin(27, "led.rgb");
  Dht22Module dht(32, fakeDht, nullptr);
  SoilMoistureModule soil(34, fakeAdc, nullptr);
  assert(dht.begin(pins));
  assert(soil.begin(pins));
  assert(!dht.setPin(18));  // Cannot mutate a module while running.

  assert(rewireSingle(pins, dht, "dht22", 31, 18) == WiringOutcome::Stale);
  assert(rewireSingle(pins, dht, "dht22", 32, 34) == WiringOutcome::Invalid);
  assert(rewireSingle(pins, dht, "dht22", 32, 25) == WiringOutcome::Invalid);
  assert(pins.belongsTo(32, dht.id()));

  assert(rewireSingle(pins, dht, "dht22", 32, 18) == WiringOutcome::Applied);
  assert(!pins.lease(32) && pins.belongsTo(18, dht.id()));
  assert(dht.pin() == 18 && dht.ready());
  assert(rewireSingle(pins, dht, "dht22", 32, 19) == WiringOutcome::Stale);
  assert(rewireSingle(pins, dht, "dht22", 18, 18) == WiringOutcome::AlreadyApplied);

  assert(rewireSingle(pins, soil, "soil_moisture", 34, 18) == WiringOutcome::Invalid);
  assert(rewireSingle(pins, soil, "soil_moisture", 34, 32) == WiringOutcome::Applied);
  assert(pins.belongsTo(32, soil.id()) && !pins.lease(34));
  assert(rewireSingle(pins, dht, "dht22", 18, 32) == WiringOutcome::Reserved);
  assert(dht.pin() == 18 && dht.ready());

  FaultModule faulty;
  assert(faulty.begin(pins) == false); // DHT already holds 18; do not steal.
  assert(dht.ready());
  dht.stop(pins);
  assert(faulty.begin(pins));
  assert(rewireSingle(pins, faulty, "dht22", 18, 19) == WiringOutcome::RolledBack);
  assert(faulty.ready() && faulty.pin() == 18);
  assert(pins.belongsTo(18, faulty.id()) && !pins.lease(19));
  assert(rewireSingle(pins, faulty, "dht22", 18, 18) == WiringOutcome::AlreadyApplied);
  faulty.stop(pins);
  soil.stop(pins);
  assert(pins.leasedCount() == 0);
  std::cout << "component_wiring_transaction_test: all tests passed\n";
  return 0;
}
