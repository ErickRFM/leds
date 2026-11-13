// Host test: robotics_crane GPIO rewiring over the owner BLE channel.
// Covers the fixed-width CPN frame parser, the multi-signal CAS transaction,
// replay idempotency, busy/refusal ordering and impossible components.
#include <cassert>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include "../firmware/esp32_rgb/core/BleSensorPinRequest.h"
#include "../firmware/esp32_rgb/core/BleSensorPinControl.h"
using namespace esp32core;

struct Physical {
  uint8_t pins[7] = {18, 19, 34, 35, 36, 39, 17};  // audited boot map
  bool running = false;
  int writes = 0;
};
static bool apply(const char* component, const uint8_t* from,
                  const uint8_t* to, void* context) {
  auto& physical = *static_cast<Physical*>(context);
  assert(!std::strcmp(component, "robotics_crane"));
  if (physical.running) return false;  // configurePins() refuses while active
  for (size_t i = 0; i < 7; ++i) {
    if (from[i] != physical.pins[i]) return false;  // CAS: stale predecessor
    if (to[i] == 0) return false;                   // a live crane needs pads
  }
  for (size_t i = 0; i < 7; ++i) physical.pins[i] = to[i];
  ++physical.writes;
  return true;
}

int main() {
  // Frame: CPN + 16 hex + ,C, + 14 two-digit pins (7 fixed-order from/to
  // pairs: servo base, servo arm, joystick up/down/left/right/mid).
  const char* valid =
      "CPN,0123456789abcdef,C,"
      "1818191934343535362139221717";
  BleComponentPinRequest request{};
  assert(parseBleComponentPinRequest(valid, request));
  assert(!std::strcmp(request.token, "0123456789abcdef"));
  assert(!std::strcmp(request.component, "robotics_crane"));
  const uint8_t fromExpected[7] = {18, 19, 34, 35, 36, 39, 17};
  const uint8_t toExpected[7] = {18, 19, 34, 35, 21, 22, 17};
  for (size_t i = 0; i < 7; ++i) {
    assert(request.from[i] == fromExpected[i]);
    assert(request.to[i] == toExpected[i]);
  }

  // Malformed frames: length, prefix, token charset, component code, pair
  // count, non-digits, trailing junk and impossible GPIO 40.
  for (const char* bad : {
      "", "CPN,", "PIN,0123456789abcdef,D,16,33",
      "CPN,0123456789abcdef",
      "CPN,0123456789abcdef,C",
      "CPN,0123456789abcdef,D,1818191934343535362139221717",
      "CPN,0123456789abcdeg,C,1818191934343535362139221717",
      "CPN,0123456789abcdef,C,18181919343435353621392217",
      "CPN,0123456789abcdef,C,181819193434353536213922171700",
      "CPN,0123456789abcdef,C,18181919343435353621392217174",
      "CPN,0123456789abcdef,C,1818191934343535362139221717a7",
      "CPN,0123456789abcdef,C,181819193434353536213922171740"}) {
    BleComponentPinRequest sink{};
    assert(!parseBleComponentPinRequest(bad, sink));
  }
  assert(!parseBleComponentPinRequest(nullptr, request));

  Physical physical;
  BleSensorPinControl control;
  control.setApply(apply, &physical);

  // A running crane cannot jump pads even with a fresh token: Busy precedes
  // nothing — the ledger sees a New request and the physical layer refuses.
  physical.running = true;
  BleComponentPinRequest whileRunning{};
  assert(parseBleComponentPinRequest(valid, whileRunning));
  assert(control.apply(whileRunning, false) == BlePinApplyResult::Rejected);
  assert(physical.writes == 0);
  physical.running = false;

  // Authorized transfer onto the user's reachable pads (no GPIO36/39).
  const char* rewire =
      "CPN,2222222222222222,C,"
      "1818191934343535362139221717";
  BleComponentPinRequest rewiring{};
  assert(parseBleComponentPinRequest(rewire, rewiring));
  assert(control.apply(rewiring, false) == BlePinApplyResult::Applied);
  assert(physical.writes == 1);
  for (size_t i = 0; i < 7; ++i) assert(physical.pins[i] == toExpected[i]);

  // Identical retry after a lost BLE reply is safe idempotency.
  assert(control.apply(rewiring, false) == BlePinApplyResult::Replayed);
  assert(physical.writes == 1);

  // Same token with different pins is a conflicting reuse, never applied.
  BleComponentPinRequest conflicting{};
  assert(parseBleComponentPinRequest(
      "CPN,2222222222222222,C,"
      "1616171732323333212122222323", conflicting));
  assert(control.apply(conflicting, false) == BlePinApplyResult::Rejected);
  assert(physical.writes == 1);

  // Stale CAS: first activation of a stopped crane carries zero predecessors;
  // a nonzero from on an inactive module fails at the physical layer.
  BleComponentPinRequest stale{};
  assert(parseBleComponentPinRequest(
      "CPN,3333333333333333,C,"
      "0000000000000000000000000000", stale));
  for (size_t i = 0; i < 7; ++i) stale.to[i] = 16 + static_cast<uint8_t>(i);
  assert(control.apply(stale, false) == BlePinApplyResult::Rejected);
  assert(physical.writes == 1);

  puts("BLE component PIN parser, crane CAS, replay ledger: ALL OK");
}
