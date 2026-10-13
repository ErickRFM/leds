#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

/**
 * Bounded declarative RGB script for the ESP32 laboratory.
 * NOT Arduino C++ execution. No direct GPIO, flash, networking, pointers,
 * memory allocation or user supplied callbacks. One script, 32 steps, 20 s.
 */
class RgbScriptRuntime final {
 public:
  static constexpr size_t kMaxSourceBytes = 512;
  static constexpr size_t kMaxInstructions = 32;
  static constexpr uint32_t kMaxDurationMs = 20000;
  enum class Op : uint8_t { RGB, WAIT, ON, OFF, STOP };
  struct Instruction {
    Op op = Op::STOP;
    uint16_t value = 0;  // WAIT milliseconds
    uint8_t r = 0, g = 0, b = 0, brightness = 255;
  };
  using RgbCommand = bool (*)(const char* command, void* context);

  static bool validate(const char* source) {
    Instruction steps[kMaxInstructions] = {};
    size_t count = 0;
    return parse(source, steps, count);
  }

  bool start(const char* source, uint32_t nowMs) {
    if (active_) return false;
    Instruction parsed[kMaxInstructions] = {};
    size_t count = 0;
    if (!parse(source, parsed, count)) return false;
    for (size_t i = 0; i < count; ++i) steps_[i] = parsed[i];
    count_ = count;
    position_ = 0;
    startedMs_ = nowMs;
    waitStartedMs_ = nowMs;
    waiting_ = false;
    active_ = true;
    return true;
  }

  /** Never blocks: at most 32 instantaneous commands per tick. */
  bool tick(uint32_t nowMs, RgbCommand send, void* context) {
    if (!active_) return true;
    // Allow scripts up to kMaxDurationMs to complete their final action without premature cutoff.
    if (!send || static_cast<uint32_t>(nowMs - startedMs_) > kMaxDurationMs) {
      stop();
      return false;
    }
    for (size_t budget = 0; active_ && budget < kMaxInstructions; ++budget) {
      if (waiting_) {
        if (static_cast<uint32_t>(nowMs - waitStartedMs_) < steps_[position_].value) return true;
        waiting_ = false;
        ++position_;
      }
      if (position_ >= count_) {
        stop();
        return true;
      }
      const Instruction& next = steps_[position_];
      if (next.op == Op::WAIT) {
        waiting_ = true;
        waitStartedMs_ = nowMs;
        return true;
      }
      if (next.op == Op::STOP) {
        stop();
        return true;
      }
      char command[48] = {};
      if (next.op == Op::ON) std::strcpy(command, "ON");
      else if (next.op == Op::OFF) std::strcpy(command, "OFF");
      else {
        // SET,<R>,<G>,<B>,<brightness> is the existing RGB codec contract.
        size_t used = 0;
        const char* prefix = "SET,";
        std::memcpy(command, prefix, 4);
        used = 4;
        appendNumber(command, used, next.r);
        command[used++] = ',';
        appendNumber(command, used, next.g);
        command[used++] = ',';
        appendNumber(command, used, next.b);
        command[used++] = ',';
        appendNumber(command, used, next.brightness);
        command[used] = '\0';
      }
      if (!send(command, context)) {
        stop();
        return false;
      }
      ++position_;
    }
    // A malformed/unbounded program must never keep consuming loop() time.
    if (active_ && !waiting_) stop();
    return !active_;
  }

  void stop() {
    active_ = false;
    waiting_ = false;
    position_ = 0;
    count_ = 0;
  }

  bool active() const { return active_; }
  size_t instructionIndex() const { return position_; }
  size_t instructionCount() const { return count_; }

 private:
  Instruction steps_[kMaxInstructions] = {};
  size_t count_ = 0, position_ = 0;
  uint32_t startedMs_ = 0, waitStartedMs_ = 0;
  bool active_ = false, waiting_ = false;

  static void appendNumber(char* buffer, size_t& used, uint16_t n) {
    if (n >= 100) buffer[used++] = static_cast<char>('0' + (n / 100) % 10);
    if (n >= 10) buffer[used++] = static_cast<char>('0' + (n / 10) % 10);
    buffer[used++] = static_cast<char>('0' + n % 10);
  }

  static bool parseNumber(const char*& cursor, const char* end, uint16_t limit,
                          uint16_t& result) {
    if (cursor == end || *cursor < '0' || *cursor > '9') return false;
    uint32_t value = 0;
    while (cursor < end && *cursor >= '0' && *cursor <= '9') {
      value = value * 10 + static_cast<uint8_t>(*cursor++ - '0');
      if (value > limit) return false;
    }
    result = static_cast<uint16_t>(value);
    return true;
  }

  static bool parse(const char* source, Instruction* dest, size_t& count) {
    count = 0;
    if (!source) return false;
    const size_t length = boundedLength(source);
    if (length == 0 || length > kMaxSourceBytes) return false;
    size_t offset = 0;
    uint32_t totalWait = 0;
    bool hasAction = false;
    bool stopped = false;
    while (offset < length) {
      size_t end = offset;
      while (end < length && source[end] != '\n') ++end;
      const char* start = source + offset;
      const char* last = source + end;
      while (start < last && (*start == ' ' || *start == '\t')) ++start;
      while (last > start && (last[-1] == ' ' || last[-1] == '\t' || last[-1] == '\r')) --last;
      offset = end < length ? end + 1 : length;
      if (start == last) continue;
      if (*start == '#') continue;  // entire-line comment only
      if (stopped || count == kMaxInstructions) return false;
      Instruction instr{};
      const size_t n = static_cast<size_t>(last - start);
      if (n == 2 && std::memcmp(start, "ON", 2) == 0) {
        instr.op = Op::ON; hasAction = true;
      } else if (n == 3 && std::memcmp(start, "OFF", 3) == 0) {
        instr.op = Op::OFF; hasAction = true;
      } else if (n == 4 && std::memcmp(start, "STOP", 4) == 0) {
        instr.op = Op::STOP; stopped = true;
      } else if (n > 5 && std::memcmp(start, "WAIT ", 5) == 0) {
        instr.op = Op::WAIT;
        const char* p = start + 5;
        uint16_t milliseconds = 0;
        if (!parseNumber(p, last, 2000, milliseconds) || p != last ||
            milliseconds < 100) return false;
        instr.value = milliseconds;
        totalWait += milliseconds;
        if (totalWait > kMaxDurationMs) return false;
      } else if (n > 6 && std::memcmp(start, "RGB ", 4) == 0) {
        instr.op = Op::RGB;
        const char* p = start + 4;
        uint16_t rgb[4] = {0, 0, 0, 255};
        for (size_t channel = 0; channel < 3; ++channel) {
          if (!parseNumber(p, last, 255, rgb[channel])) return false;
          if (channel < 2) {
            if (p == last || *p++ != ' ') return false;
          }
        }
        if (p < last) {
          if (*p++ != ' ' || !parseNumber(p, last, 255, rgb[3])) return false;
        }
        if (p != last) return false;
        instr.r = static_cast<uint8_t>(rgb[0]);
        instr.g = static_cast<uint8_t>(rgb[1]);
        instr.b = static_cast<uint8_t>(rgb[2]);
        instr.brightness = static_cast<uint8_t>(rgb[3]);
        hasAction = true;
      } else return false;
      dest[count++] = instr;
    }
    return hasAction && count != 0;
  }

  size_t boundedLength(const char* p) {
    size_t n = 0;
    while (n <= kMaxSourceBytes && p[n] != '\0') ++n;
    return n;
  }
};
}  // namespace esp32core
