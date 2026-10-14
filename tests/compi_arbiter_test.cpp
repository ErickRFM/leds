#include <cassert>
#include <iostream>
#include "../firmware/esp32_rgb/core/CompiCommandArbiter.h"

using esp32core::CompiCommandArbiter;
using esp32core::CommandSource;
using esp32core::ActiveMode;

int main() {
  CompiCommandArbiter arbiter;

  // 1. Initially IDLE, any source can issue commands
  assert(arbiter.currentMode() == ActiveMode::IDLE);
  auto res1 = arbiter.evaluate(CommandSource::BLE_UNAUTHENTICATED);
  assert(res1.allowed);

  // 2. Active script running under Wi-Fi SoftAP ownership
  arbiter.setActiveMode(ActiveMode::SCRIPT_RUNNING, CommandSource::WIFI_SOFTAP_LOCAL);
  assert(arbiter.currentMode() == ActiveMode::SCRIPT_RUNNING);

  // Unauthenticated BLE command MUST be rejected while script is running
  auto res2 = arbiter.evaluate(CommandSource::BLE_UNAUTHENTICATED);
  assert(!res2.allowed);

  // Authenticated Wi-Fi SoftAP command can override or control
  auto res3 = arbiter.evaluate(CommandSource::WIFI_SOFTAP_LOCAL);
  assert(res3.allowed);

  // Emergency stop from Wi-Fi SoftAP MUST be accepted
  auto res4 = arbiter.evaluate(CommandSource::WIFI_SOFTAP_LOCAL, true);
  assert(res4.allowed);

  // 3. Reset arbiter
  arbiter.reset();
  assert(arbiter.currentMode() == ActiveMode::IDLE);

  std::cout << "CompiCommandArbiter host tests passed successfully." << std::endl;
  return 0;
}
