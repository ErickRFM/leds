#include <cassert>
#include <cstdint>
#include "../firmware/esp32_rgb/core/AutonomousReportButton.h"

using esp32core::AutonomousReportButton;
int main() {
  using Event = AutonomousReportButton::Event;
  AutonomousReportButton button;
  assert(button.update(false, 0) == Event::None);
  assert(button.update(true, 100) == Event::None);
  assert(button.update(false, 130) == Event::None); // contact bounced
  assert(button.update(true, 140) == Event::None);
  assert(button.update(true, 200) == Event::None);
  assert(button.update(false, 250) == Event::None);
  assert(button.update(false, 310) == Event::SpeakNow);
  assert(button.update(false, 320) == Event::None);

  assert(button.update(true, 1000) == Event::None);
  assert(button.update(true, 1060) == Event::None);
  assert(button.update(true, 4059) == Event::None);
  assert(button.update(true, 4060) == Event::ToggleAutomatic);
  assert(button.update(true, 9000) == Event::None);
  assert(button.update(false, 9100) == Event::None);
  assert(button.update(false, 9160) == Event::None); // long cannot also speak
  assert(button.update(true, 9200) == Event::None);
  assert(button.update(true, 9260) == Event::None);
  assert(button.update(false, 9300) == Event::None);
  assert(button.update(false, 9360) == Event::SpeakNow);

  // Monotonic millis rollover must not break short press handling.
  AutonomousReportButton rollover;
  assert(rollover.update(true, 0xFFFFFFB0u) == Event::None);
  assert(rollover.update(true, 0x00000020u) == Event::None);
  assert(rollover.update(false, 0x00000030u) == Event::None);
  assert(rollover.update(false, 0x00000090u) == Event::SpeakNow);
}
