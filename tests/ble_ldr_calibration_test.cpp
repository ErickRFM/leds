#include <cassert>
#include <iostream>
#include "../firmware/esp32_rgb/core/BleLdrCalibration.h"
#include "../firmware/esp32_rgb/core/CompiCommandArbiter.h"
using namespace esp32core;
int main() {
  BleLdrCalibration cmd{};
  assert(parseBleLdrCalibration("LDR,17,500,3500", cmd));
  assert(cmd.requestId == 17 && cmd.darkRaw == 500 && cmd.brightRaw == 3500);
  // Many LDR analog boards invert ADC polarity: dark can be the larger value.
  assert(parseBleLdrCalibration("LDR,18,3500,500", cmd));
  assert(cmd.darkRaw == 3500 && cmd.brightRaw == 500);
  for (const char* input : {"", "LDR", "LDR,1,0", "LDR,0,100,1000",
                             "LDR,1,100,105", "LDR,1,200,199",
                             "LDR,1,0,4096", "LDR,65536,0,4095",
                             "LDR,-1,200,250", "LDR,1,0,500\n",
                             "LDR,1,0,300,REBOOT", "LDR,1,ab,300"}) {
    assert(!parseBleLdrCalibration(input, cmd));
  }
  assert(!parseBleLdrCalibration(nullptr, cmd));
  assert(!CompiCommandArbiter().evaluate(
      CompiCommandArbiter::mapBleSource(
          BleAuthState::CONNECTED_UNAUTHENTICATED)).allowed);
  assert(CompiCommandArbiter().evaluate(
      CompiCommandArbiter::mapBleSource(BleAuthState::AUTHORIZED)).allowed);
  std::cout << "LDR BLE calibration parser and owner gate: OK\n";
}
