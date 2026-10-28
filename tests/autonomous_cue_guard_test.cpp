#include <cassert>
#include <cstdint>
#include "../firmware/esp32_rgb/core/AutonomousCueGuard.h"
using esp32core::AutonomousCueGuard;
int main() {
  AutonomousCueGuard g;
  assert(!g.blocks());
  // Regular clip: BUSY low confirms start and BUSY high confirms completion.
  g.queued(100);
  assert(g.blocks());
  g.tick(200, false);
  assert(g.blocks());  // UART command may still be queued.
  g.tick(500, true);
  assert(g.blocks());
  g.tick(3000, true);
  assert(g.blocks());
  g.tick(3200, false);
  assert(g.blocks());
  g.tick(3699, false);
  assert(g.blocks());
  g.tick(3700, false);
  assert(!g.blocks());  // Physical request can now start before 10s TTL.
  // No BUSY wire: preserve conservative guard, but release after 15 seconds.
  g.queued(10000);
  g.tick(24999, false);
  assert(g.blocks());
  g.tick(25000, false);
  assert(!g.blocks());
  // A second cue resets the guard.
  g.queued(26000);
  g.tick(27000, true);
  g.queued(28000);
  g.tick(28001, false);
  assert(g.blocks());
  // Uint32 wrap.
  g.queued(0xFFFFFF00u);
  g.tick(0x00000020u, true);
  g.tick(0x00000040u, false);
  g.tick(0x00000233u, false);
  assert(g.blocks());
  g.tick(0x00000234u, false);
  assert(!g.blocks());
}
