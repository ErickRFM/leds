#pragma once
#include "RgbCommandInbox.h"
namespace esp32core {
// Caller owns its portMUX. A command requires authorization both when it is
// queued and when the loop executes it; revocation drops all pending work.
class BleCommandInbox final {
 public:
  void setAuthorized(bool authorized) {
    authorized_=authorized;
    if (!authorized) queue_.clear();
  }
  bool push(const char* command) {return authorized_ && queue_.push(command);}
  bool pop(char (&output)[RgbCommandInbox::kMaxCommand+1]) {
    return authorized_ && queue_.pop(output);
  }
 private:
  bool authorized_=false;
  RgbCommandInbox queue_;
};
} // namespace esp32core
