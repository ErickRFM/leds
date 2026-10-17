// Host-only regression suite: GPIO transaction replay and collision safety.
#include <cassert>
#include <cstdio>
#include <iostream>
#include "../firmware/esp32_rgb/core/ComponentApplyReplayGuard.h"

using esp32core::ComponentApplyReplayGuard;
using Guard = ComponentApplyReplayGuard;
using Verdict = Guard::Verdict;

int main() {
  Guard guard;
  Guard::Request request{"0011223344556677", "dht22", 32, 0, 18, 0};

  assert(guard.inspect(request) == Verdict::New);
  assert(guard.commit(request));
  assert(guard.inspect(request) == Verdict::IdenticalRetry);
  assert(!guard.commit(request));

  // Same transaction ID cannot be repurposed to take another pin or module.
  Guard::Request differentPin{"0011223344556677", "dht22", 32, 0, 19, 0};
  Guard::Request differentStart{"0011223344556677", "dht22", 33, 0, 18, 0};
  Guard::Request differentModule{"0011223344556677", "soil_moisture", 32, 0, 18, 0};
  assert(guard.inspect(differentPin) == Verdict::ConflictingReuse);
  assert(guard.inspect(differentStart) == Verdict::ConflictingReuse);
  assert(guard.inspect(differentModule) == Verdict::ConflictingReuse);
  assert(!guard.commit(differentPin));

  // Invalid, truncated, non-hex, or unknown-component requests never enter cache.
  assert(guard.inspect({nullptr, "dht22", 0, 0, 18, 0}) == Verdict::Invalid);
  assert(guard.inspect({"aabbcc", "dht22", 0, 0, 18, 0}) == Verdict::Invalid);
  assert(guard.inspect({"001122334455667z", "dht22", 0, 0, 18, 0}) == Verdict::Invalid);
  assert(guard.inspect({"001122334455667788", "dht22", 0, 0, 18, 0}) == Verdict::Invalid);
  assert(guard.inspect({"0011223344556688", "led_rgb", 0, 0, 27, 0}) == Verdict::Invalid);
  assert(guard.inspect({"0011223344556688", "ssd1306_oled", 0, 0, 21, 22}) == Verdict::New);
  assert(guard.inspect({"AABBCCDDEEFF1122", "soil_moisture", 0, 0, 34, 0}) == Verdict::New);

  // Bounded ring behavior: every successful change consumes at most one entry.
  for (int i = 0; i < 8; ++i) {
    char token[17] = {};
    std::snprintf(token, sizeof(token), "%016x", 0x1000 + i);
    Guard::Request update{token, "soil_moisture", 0, 0, 34, 0};
    assert(guard.inspect(update) == Verdict::New);
    assert(guard.commit(update));
    assert(guard.inspect(update) == Verdict::IdenticalRetry);
  }
  // Old requests expire; a reused stale expected pin is still caught by
  // the separate PinManager/firmware compare-and-swap precondition.
  assert(guard.inspect(request) == Verdict::New);
  std::cout << "Component apply idempotency 100% PASS\n";
}
