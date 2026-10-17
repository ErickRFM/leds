#pragma once

#include <Arduino.h>
#include "../core/ModuleRegistry.h"

namespace esp32core {

// Adapter around the existing, proven RGB color/effect protocol.
// BLE framing/parser remain in .ino for V1 compatibility; this module owns
// GPIO/PWM initialisation, module lifetime and the effect ticker.
class LedRgbModule final : public IModule {
 public:
  using Render = void (*)();
  using RenderEffect = void (*)(uint32_t);
  using EffectsActive = bool (*)();

  LedRgbModule(uint8_t red, uint8_t green, uint8_t blue, bool commonAnode,
               Render initial, RenderEffect effects, EffectsActive active)
      : pins_{red, green, blue}, commonAnode_(commonAnode),
        renderInitial_(initial), renderEffect_(effects), active_(active) {}

  const char* id() const override { return "led.rgb"; }
  uint16_t version() const override { return 1; }

  bool begin(PinManager& manager) override {
    if (ready_ || renderInitial_ == nullptr || renderEffect_ == nullptr ||
        active_ == nullptr) return false;
    for (size_t i = 0; i < 3; ++i) {
      if (!manager.acquire(pins_[i], id(), PinFunction::PwmOutput)) {
        releaseClaims(manager);
        return false;
      }
    }
    for (size_t i = 0; i < 3; ++i) {
      if (!ledcAttach(pins_[i], 5000, 8)) {
        for (size_t j = 0; j < i; ++j) {
          ledcDetach(pins_[j]);
          pinMode(pins_[j], INPUT);  // Never leave a partially attached PWM driven.
        }
        releaseClaims(manager);
        return false;
      }
    }
    ready_ = true;
    lastFrame_ = 0;
    renderInitial_(); // RGB starts OFF exactly as in V1.
    return true;
  }

  void tick(uint32_t nowMs) override {
    if (ready_ && active_() && nowMs - lastFrame_ >= 25U) {
      lastFrame_ = nowMs;
      renderEffect_(nowMs);
    }
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    for (const uint8_t pin : pins_) {
      ledcWrite(pin, commonAnode_ ? 255 : 0); // Off before detaching PWM.
      ledcDetach(pin);
      pinMode(pin, INPUT);
    }
    ready_ = false;
    releaseClaims(manager);
  }

  // Only the authorized transaction coordinator may move a STOPPED RGB.
  // Never accept input-only pins, duplicate signals or non-ESP32 outputs.
  bool configurePins(uint8_t red, uint8_t green, uint8_t blue) {
    if (ready_ || red == green || red == blue || green == blue ||
        !PinManager::outputCapable(red) || !PinManager::outputCapable(green) ||
        !PinManager::outputCapable(blue)) return false;
    pins_[0] = red;
    pins_[1] = green;
    pins_[2] = blue;
    return true;
  }

  bool ready() const { return ready_; }
  uint8_t redPin() const { return pins_[0]; }
  uint8_t greenPin() const { return pins_[1]; }
  uint8_t bluePin() const { return pins_[2]; }

 private:
  void releaseClaims(PinManager& manager) {
    for (const uint8_t pin : pins_) manager.release(pin, id());
  }
  uint8_t pins_[3];
  const bool commonAnode_;
  Render renderInitial_;
  RenderEffect renderEffect_;
  EffectsActive active_;
  bool ready_ = false;
  uint32_t lastFrame_ = 0;
};
} // namespace esp32core
