#pragma once

#include <cstddef>
#include <cstring>

namespace esp32core {

/**
 * Fixed-size inbox. Thread synchronization is owned by the caller:
 * Arduino BLE callbacks enqueue under portMUX, main loop drains under portMUX.
 * The inbox itself does not allocate memory or touch GPIO/BLE/HTTP.
 */
class RgbCommandInbox final {
 public:
  static constexpr size_t kCapacity = 16;
  static constexpr size_t kMaxCommand = 48;

  bool push(const char* command) {
    if (!command) return false;
    size_t n = 0;
    while (n <= kMaxCommand && command[n]) ++n;
    if (!n || n > kMaxCommand || count_ == kCapacity) return false;
    std::memcpy(commands_[tail_], command, n);
    commands_[tail_][n] = '\0';
    tail_ = (tail_ + 1) % kCapacity;
    ++count_;
    return true;
  }

  bool pop(char (&result)[kMaxCommand + 1]) {
    if (!count_) return false;
    std::memcpy(result, commands_[head_], kMaxCommand + 1);
    head_ = (head_ + 1) % kCapacity;
    --count_;
    return true;
  }

  size_t size() const { return count_; }
  void clear() { head_ = tail_ = count_ = 0; }

 private:
  char commands_[kCapacity][kMaxCommand + 1] = {};
  size_t head_ = 0, tail_ = 0, count_ = 0;
};
}  // namespace esp32core
