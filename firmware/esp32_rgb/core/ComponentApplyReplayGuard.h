#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace esp32core {

// Small fixed-size idempotency ledger for authorized runtime GPIO changes.
// Never stores the owner secret or requires heap allocations/NVS.
// On reboot the ledger and all runtime-only GPIO assignments reset together.
class ComponentApplyReplayGuard final {
 public:
  static constexpr size_t kCapacity = 8;
  enum class Verdict : uint8_t { New, IdenticalRetry, ConflictingReuse, Invalid };

  struct Request {
    const char* requestId;  // Exactly 16 hexadecimal ASCII characters
    const char* componentId;
    uint8_t from1, from2, to1, to2;
    uint8_t from3 = 0, to3 = 0;  // RGB third PWM signal; legacy requests default to zero.
  };

  static bool validRequestId(const char* requestId) {
    if (!requestId) return false;
    for (size_t i = 0; i < 16; ++i) {
      const char c = requestId[i];
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
            (c >= 'A' && c <= 'F'))) return false;
    }
    return requestId[16] == '\0';
  }

  Verdict inspect(const Request& input) const {
    if (!validRequestId(input.requestId) || !validComponent(input.componentId)) {
      return Verdict::Invalid;
    }
    for (const auto& record : records_) {
      if (!record.used || std::strcmp(record.requestId, input.requestId) != 0)
        continue;
      return record.sameAs(input) ? Verdict::IdenticalRetry
                                  : Verdict::ConflictingReuse;
    }
    return Verdict::New;
  }

  // Commit *only after* successful firmware application, never on error.
  // Caller invokes inspect() first; the ledger does not authorize changes.
  bool commit(const Request& input) {
    if (inspect(input) != Verdict::New) return false;
    Record& record = records_[next_];
    record.used = true;
    std::memcpy(record.requestId, input.requestId, 17);
    std::strcpy(record.componentId, input.componentId);
    record.from1 = input.from1;
    record.from2 = input.from2;
    record.to1 = input.to1;
    record.to2 = input.to2;
    record.from3 = input.from3;
    record.to3 = input.to3;
    next_ = (next_ + 1) % kCapacity;
    return true;
  }

 private:
  static constexpr bool validComponent(const char* id) {
    // Must match LocalWifiControl /v2/components/apply exactly:
    // a compiled module may be inactive, but GPIO authority stays with
    // PinManager and the compare-and-swap checks inside applyComponentPins.
    return id != nullptr &&
       (equal(id, "dht11") || equal(id, "dht22") ||
        equal(id, "ldr_light") || equal(id, "soil_moisture") ||
        equal(id, "ssd1306_oled") || equal(id, "led_rgb"));
  }
  static constexpr bool equal(const char* a, const char* b) {
    if (!a || !b) return false;
    while (*a && *b) {
      if (*a++ != *b++) return false;
    }
    return *a == *b;
  }

  struct Record {
    bool used = false;
    char requestId[17] = {};
    char componentId[20] = {};
    uint8_t from1 = 0, from2 = 0, to1 = 0, to2 = 0;
    uint8_t from3 = 0, to3 = 0;

    bool sameAs(const Request& request) const {
      return std::strcmp(componentId, request.componentId) == 0 &&
             from1 == request.from1 && from2 == request.from2 &&
             to1 == request.to1 && to2 == request.to2 &&
             from3 == request.from3 && to3 == request.to3;
    }
  };
  Record records_[kCapacity] = {};
  size_t next_ = 0;
};

}  // namespace esp32core
