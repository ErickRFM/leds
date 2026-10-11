#include <cassert>
#include <cstdint>
#include <iostream>
#include "../firmware/esp32_rgb/core/PinManager.h"
#include "../firmware/esp32_rgb/core/ModuleRegistry.h"
using namespace esp32core;

class FakeModule final : public IModule {
 public:
  FakeModule(const char* label, uint8_t gpio, bool reject = false)
      : label_(label), gpio_(gpio), reject_(reject) {}
  const char* id() const override { return label_; }
  uint16_t version() const override { return 1; }
  bool begin(PinManager& pins) override {
    if (reject_) return false;
    return pins.acquire(gpio_, label_, PinFunction::DigitalOutput);
  }
  void tick(uint32_t) override { ++ticks; }
  void stop(PinManager& pins) override { pins.release(gpio_, label_); }
  int ticks = 0;
 private:
  const char* label_;
  uint8_t gpio_;
  bool reject_;
};

int main() {
  static_assert(PinManager::outputCapable(27), "RGB red must be available");
  static_assert(PinManager::analogInputCapable(34), "ADC1 input must be available");
  static_assert(!PinManager::outputCapable(34), "GPIO34 is input-only");
  static_assert(!PinManager::canUse(6, PinFunction::DigitalOutput),
                "Flash GPIO6 must remain denied");
  static_assert(!PinManager::canUse(0, PinFunction::DigitalOutput),
                "Boot strap GPIO0 must remain denied");
  static_assert(!PinManager::canUse(25, PinFunction::AnalogInput),
                "ADC2 not available in Wi-Fi mode");
  static_assert(!PinManager::canUse(40, PinFunction::DigitalInput),
                "Out-of-range GPIO denied");
  PinManager pins;
  assert(pins.acquire(27, "led.rgb", PinFunction::PwmOutput));
  assert(pins.acquire(25, "led.rgb", PinFunction::PwmOutput));
  assert(pins.acquire(26, "led.rgb", PinFunction::PwmOutput));
  assert(pins.leasedCount() == 3);
  assert(pins.belongsTo(27, "led.rgb"));
  assert(!pins.acquire(27, "laboratory", PinFunction::DigitalOutput));
  assert(!pins.release(27, "laboratory"));
  assert(pins.lease(27)->function == PinFunction::PwmOutput);
  assert(pins.release(27, "led.rgb"));
  assert(pins.release(25, "led.rgb"));
  assert(pins.release(26, "led.rgb"));
  assert(!pins.release(27, "led.rgb"));
  assert(pins.leasedCount() == 0);
  assert(pins.acquire(34, "sensor.adc", PinFunction::AnalogInput));
  assert(!pins.acquire(34, "switch", PinFunction::DigitalOutput));
  assert(pins.release(34, "sensor.adc"));

  ModuleRegistry registry;
  FakeModule led("led.rgb", 27);
  FakeModule gpio("gpio", 18);
  FakeModule duplicate("gpio", 19);
  assert(registry.add(led));
  assert(registry.add(gpio));
  assert(!registry.add(duplicate));
  assert(registry.size() == 2);
  assert(registry.at(0) == &led);
  assert(registry.at(2) == nullptr);
  registry.tickAll(100);
  assert(led.ticks == 0);
  assert(registry.startAll(pins));
  assert(registry.started());
  assert(pins.leasedCount() == 2);
  assert(!registry.add(duplicate));
  registry.tickAll(100);
  assert(led.ticks == 1 && gpio.ticks == 1);
  registry.stopAll(pins);
  assert(pins.leasedCount() == 0);
  assert(!registry.started());

  ModuleRegistry rollback;
  FakeModule first("first", 18);
  FakeModule second("second", 18);
  assert(rollback.add(first));
  assert(rollback.add(second));
  assert(!rollback.startAll(pins));
  assert(!rollback.started());
  assert(pins.leasedCount() == 0);

  ModuleRegistry failedBegin;
  FakeModule ok("ok", 19);
  FakeModule refuses("refuses", 21, true);
  assert(failedBegin.add(ok));
  assert(failedBegin.add(refuses));
  assert(!failedBegin.startAll(pins));
  assert(pins.leasedCount() == 0);
  std::cout << "PASS: PinManager permissions, leases, ModuleRegistry lifecycle, rollback\n";
}
