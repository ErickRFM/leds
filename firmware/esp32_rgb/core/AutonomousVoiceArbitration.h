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
        (!buttonRequested && !manualQuietElapsed)) return false;
    // A physical request may bypass the *time-only* manual grace period,\n    // but never playback status, BUSY or the previously queued cue guard.\n    // Physical requests take priority over an unqueued ambient event.
    return buttonRequested || !cuePending;
  }
};
}  // namespace esp32core
