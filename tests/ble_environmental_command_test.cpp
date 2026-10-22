#include <cassert>
#include <cstring>
#include <iostream>
#include "../firmware/esp32_rgb/core/BleEnvironmentalCommand.h"
#include "../firmware/esp32_rgb/core/CompiCommandArbiter.h"

using namespace esp32core;

int main() {
  assert(parseBleEnvironmentalCommand("ENV,GET") == BleEnvironmentalAction::Get);
  assert(parseBleEnvironmentalCommand("ENV,START") == BleEnvironmentalAction::Start);
  assert(parseBleEnvironmentalCommand("ENV,STOP") == BleEnvironmentalAction::Stop);
  for (const char* invalid : {"", "ENV", "ENV,DELETE", "ENV,START ", "ENV,START\n",
                               "ENV,RESET", "ENV,GPIO,36", "env,start", "ENV,ON",
                               "SET,0,0,0,255"}) {
    assert(!isBleEnvironmentalCommand(invalid));
  }
  assert(!isBleEnvironmentalCommand(nullptr));
  // The parser is not an authorization boundary: every BLE command must pass
  // the firmware's owner-session state and command arbiter before execution.
  CompiCommandArbiter arbiter;
  for (const auto state : {BleAuthState::DISCONNECTED,
                           BleAuthState::CONNECTED_UNAUTHENTICATED,
                           BleAuthState::ENCRYPTED,
                           BleAuthState::BONDED,
                           BleAuthState::PAIRING,
                           BleAuthState::FAILED,
                           BleAuthState::REVOKED}) {
    const auto source = CompiCommandArbiter::mapBleSource(state);
    assert(source == CommandSource::BLE_UNAUTHENTICATED);
    assert(!arbiter.evaluate(source).allowed);
    assert(!arbiter.evaluate(source, true).allowed);
  }
  assert(CompiCommandArbiter::mapBleSource(BleAuthState::AUTHORIZED) ==
         CommandSource::BLE_AUTHENTICATED);
  assert(arbiter.evaluate(CommandSource::BLE_AUTHENTICATED).allowed);
  assert(arbiter.evaluate(CommandSource::BLE_AUTHENTICATED, true).allowed);
  std::cout << "BLE environmental frames/auth gate: ALL OK\n";
  return 0;
}
