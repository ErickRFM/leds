#pragma once

namespace esp32core {
// Pure, portable arbitration for the single DFPlayer UART owner.
// A button request takes priority over an unqueued ambient cue, but must
// not overtake a cue already accepted by the DFPlayer queue.
struct AutonomousVoiceArbitration {
  bool automaticEnabled = false;
  bool reportActive = false;
  bool buttonRequested = false;
  bool periodicDue = false;
  bool cuePending = false;
  bool cueQueuedThisLoop = false;
  bool cueRecentlyQueued = false;
  bool playerReady = false;  // Recent UART presence and SD are both required.
  bool playbackStopped = false;
  bool busyPinPlaying = false;
  bool manualQuietElapsed = false;

  bool mayQueueCue() const {
    return automaticEnabled && !reportActive && !buttonRequested &&
           cuePending && !cueRecentlyQueued && playerReady &&
           playbackStopped && !busyPinPlaying;
  }

  bool mayStartReport() const {
    if (!(buttonRequested || (automaticEnabled && periodicDue)) ||
        reportActive || !playerReady || !playbackStopped ||
        busyPinPlaying || cueQueuedThisLoop || cueRecentlyQueued ||
        !manualQuietElapsed) return false;
    // A new physical request may take priority over an unqueued event.
    return buttonRequested || !cuePending;
  }
};
}  // namespace esp32core
