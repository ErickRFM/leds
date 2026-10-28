#include <cassert>
#include "../firmware/esp32_rgb/core/AutonomousVoiceArbitration.h"
using esp32core::AutonomousVoiceArbitration;

int main() {
  AutonomousVoiceArbitration a{};
  a.playerReady = true;
  a.playbackStopped = true;
  a.manualQuietElapsed = true;
  a.automaticEnabled = true;
  a.cuePending = true;
  a.periodicDue = true;
  // Automatic cue wins over an unattended, periodic report.
  assert(a.mayQueueCue());
  assert(!a.mayStartReport());
  // GPIO23 wins over an event that has not been sent to UART.
  a.buttonRequested = true;
  assert(!a.mayQueueCue());
  assert(a.mayStartReport());
  // A cue already queued must finish before a requested report starts.
  a.cueRecentlyQueued = true;
  assert(!a.mayStartReport());
  assert(!a.mayQueueCue());
  a.cueRecentlyQueued = false;
  a.cueQueuedThisLoop = true;
  assert(!a.mayStartReport());
  a.cueQueuedThisLoop = false;
  a.busyPinPlaying = true;
  assert(!a.mayStartReport());
  assert(!a.mayQueueCue());
  a.busyPinPlaying = false;
  a.playbackStopped = false;
  assert(!a.mayStartReport());
  a.playbackStopped = true;
  a.reportActive = true;
  assert(!a.mayStartReport());
  assert(!a.mayQueueCue());
  a.reportActive = false;
  a.manualQuietElapsed = false;
  assert(!a.mayStartReport());
  a.manualQuietElapsed = true;
  // Short press must work with automatic alerts disabled.
  a.automaticEnabled = false;
  assert(a.mayStartReport());
  assert(!a.mayQueueCue());
  // Missing hardware/SD status fails closed.
  a.playerReady = false;
  assert(!a.mayStartReport());
  assert(!a.mayQueueCue());
  a.playerReady = true;
  a.buttonRequested = false;
  assert(!a.mayStartReport());
  a.automaticEnabled = true;
  a.cuePending = false;
  assert(a.mayStartReport());
  assert(!a.mayQueueCue());
  a.periodicDue = false;
  assert(!a.mayStartReport());
}
