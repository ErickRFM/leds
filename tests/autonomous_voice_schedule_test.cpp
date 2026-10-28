#include <cassert>
#include <cstdint>
#include "../firmware/esp32_rgb/core/AutonomousVoiceSchedule.h"
using esp32core::AutonomousVoiceSchedule;

int main() {
  AutonomousVoiceSchedule s;
  // Boot with persisted automatic announcements ON: first report at 45 seconds.
  s.initialize(true);
  assert(!s.periodicDue(44999));
  assert(s.periodicDue(45000));
  s.reportStarted(45000);
  assert(!s.periodicDue(45001));
  assert(!s.periodicDue(344999));
  assert(s.periodicDue(345000));

  // An on-demand press is a bounded event, not a delayed surprise.
  s.requestReport(100);
  assert(s.buttonRequested());
  assert(!s.expireButtonRequest(10099));
  assert(s.buttonRequested());
  assert(s.expireButtonRequest(10100));
  assert(!s.buttonRequested());
  assert(!s.expireButtonRequest(10101));
  s.requestReport(11000);
  s.reportStarted(11100);
  assert(!s.buttonRequested());
  assert(!s.periodicDue(311099));
  assert(s.periodicDue(311100));

  // Long hold toggles: OFF->ON must reset interval, not speak immediately.
  s.setAutomaticEnabled(false, 400000);
  assert(!s.periodicDue(999999));
  s.setAutomaticEnabled(true, 1000000);
  assert(!s.periodicDue(1000000));
  assert(!s.periodicDue(1299999));
  assert(s.periodicDue(1300000));
  // An idempotent ON call must not continually reset the due clock.
  s.setAutomaticEnabled(true, 1299999);
  assert(s.periodicDue(1300000));
  s.setAutomaticEnabled(false, 1300001);
  s.setAutomaticEnabled(true, 1350000);
  assert(!s.periodicDue(1350000));
  assert(s.periodicDue(1650000));

  // With automatic disabled, a physical press can still request speech.
  s.setAutomaticEnabled(false, 2000000);
  s.requestReport(2000001);
  assert(s.buttonRequested());
  assert(!s.periodicDue(2500000));
  s.reportStarted(2000050);
  assert(!s.buttonRequested());

  // wraparound: both expiry and the period use unsigned subtraction.
  AutonomousVoiceSchedule wrap;
  wrap.initialize(true);
  wrap.requestReport(0xFFFFFF00u);
  assert(!wrap.expireButtonRequest(0x000025FFu)); // 9983 ms
  assert(wrap.expireButtonRequest(0x00002610u));
  wrap.reportStarted(0xFFFFF000u);
  assert(!wrap.periodicDue(0x00001000u));
  assert(wrap.periodicDue(0x000483E0u)); // 0x493E0 = 300000 ms since start
}
