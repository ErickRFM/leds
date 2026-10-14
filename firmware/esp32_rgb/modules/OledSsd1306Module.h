#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include "../core/ModuleRegistry.h"

namespace esp32core {

// SSD1306 I2C OLED display module.
//
// Presence and content are only reported after the injected init/present
// functions confirm success against the device. Sending text to the module
// never claims it appeared on glass: the confirmed flag is driven exclusively
// by the driver callback's return value.
class OledSsd1306Module final : public IModule {
 public:
  using InitFn = bool (*)(uint8_t address, uint8_t sda, uint8_t scl, void* context);
  using PresentFn = bool (*)(uint8_t address, const char* text, void* context);

  static constexpr uint8_t kDefaultAddress = 0x3C;
  static constexpr size_t kMaxTextBytes = 40;

  OledSsd1306Module(uint8_t address, uint8_t sda, uint8_t scl, InitFn init,
                    PresentFn present, void* context,
                    uint32_t refreshIntervalMs = 250)
      : address_(address), sda_(sda), scl_(scl), init_(init),
        present_(present), context_(context),
        refreshIntervalMs_(refreshIntervalMs) {}

  const char* id() const override { return "display.ssd1306"; }
  uint16_t version() const override { return 1; }

  static bool validAddress(uint8_t address) {
    return address == 0x3C || address == 0x3D;
  }

  bool begin(PinManager& manager) override {
    if (ready_) return false;
    if (!validAddress(address_) || init_ == nullptr) return false;
    if (sda_ == scl_) return false;
    if (!manager.acquire(sda_, id(), PinFunction::DigitalOutput)) return false;
    if (!manager.acquire(scl_, id(), PinFunction::DigitalOutput)) {
      manager.release(sda_, id());
      return false;
    }
    if (!init_(address_, sda_, scl_, context_)) {
      manager.release(sda_, id());
      manager.release(scl_, id());
      return false;
    }
    ready_ = true;
    confirmed_ = true;
    dirty_ = false;
    lastRefreshMs_ = 0;
    text_[0] = '\0';
    return true;
  }

  // Bounded copy; returns false (and changes nothing) on overflow.
  bool setText(const char* text) {
    if (!text) return false;
    const size_t length = std::strlen(text);
    if (length >= kMaxTextBytes) return false;
    std::memcpy(text_, text, length + 1);
    dirty_ = true;
    return true;
  }

  void tick(uint32_t nowMs) override {
    if (!ready_ || !dirty_ || present_ == nullptr) return;
    if (lastRefreshMs_ != 0 &&
        static_cast<uint32_t>(nowMs - lastRefreshMs_) < refreshIntervalMs_) {
      return;
    }
    lastRefreshMs_ = nowMs;
    confirmed_ = present_(address_, text_, context_);
    if (confirmed_) dirty_ = false;
  }

  // Immediate refresh ignoring the rate limit; used by explicit user actions.
  bool refreshNow(uint32_t nowMs) {
    if (!ready_ || present_ == nullptr) return false;
    lastRefreshMs_ = nowMs;
    confirmed_ = present_(address_, text_, context_);
    if (confirmed_) dirty_ = false;
    return confirmed_;
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    manager.release(sda_, id());
    manager.release(scl_, id());
    ready_ = false;
    confirmed_ = false;
    dirty_ = false;
  }

  bool ready() const { return ready_; }
  bool confirmed() const { return confirmed_; }
  bool pending() const { return dirty_; }
  uint8_t address() const { return address_; }
  const char* text() const { return text_; }

 private:
  const uint8_t address_;
  const uint8_t sda_;
  const uint8_t scl_;
  InitFn init_;
  PresentFn present_;
  void* const context_;
  const uint32_t refreshIntervalMs_;
  bool ready_ = false;
  bool confirmed_ = false;
  bool dirty_ = false;
  uint32_t lastRefreshMs_ = 0;
  char text_[kMaxTextBytes] = {};
};
}  // namespace esp32core
