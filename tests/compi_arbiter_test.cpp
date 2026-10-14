#include <cassert>
#include <iostream>
#include "../firmware/esp32_rgb/core/CompiCommandArbiter.h"

using esp32core::CompiCommandArbiter;
using esp32core::CommandSource;
using esp32core::ActiveMode;
using esp32core::BleAuthState;

int main() {
  CompiCommandArbiter arbiter;

  // BleAuthState mapping assertions
  assert(CompiCommandArbiter::mapBleSource(BleAuthState::DISCONNECTED) == CommandSource::BLE_UNAUTHENTICATED);
  assert(CompiCommandArbiter::mapBleSource(BleAuthState::CONNECTED_UNAUTHENTICATED) == CommandSource::BLE_UNAUTHENTICATED);
  assert(CompiCommandArbiter::mapBleSource(BleAuthState::AUTHORIZED) == CommandSource::BLE_AUTHENTICATED);
  assert(CompiCommandArbiter::mapBleSource(BleAuthState::BONDED) == CommandSource::BLE_AUTHENTICATED);
  assert(CompiCommandArbiter::mapBleSource(BleAuthState::REVOKED) == CommandSource::BLE_UNAUTHENTICATED);

  // 1. Unauthenticated BLE is rejected by default
  assert(arbiter.currentMode() == ActiveMode::IDLE);
  auto res1 = arbiter.evaluate(CommandSource::BLE_UNAUTHENTICATED);
  assert(!res1.allowed);

  // 2. Authenticated Wi-Fi SoftAP and Authenticated BLE commands are permitted when IDLE
  auto resWifi = arbiter.evaluate(CommandSource::WIFI_SOFTAP_LOCAL);
  assert(resWifi.allowed);

  auto resBleAuth = arbiter.evaluate(CommandSource::BLE_AUTHENTICATED);
  assert(resBleAuth.allowed);

  // 3. Active script running under Wi-Fi SoftAP ownership
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

  // 4. Reset arbiter
  arbiter.reset();
  assert(arbiter.currentMode() == ActiveMode::IDLE);

  std::cout << "CompiCommandArbiter host tests passed successfully." << std::endl;
  return 0;
}
