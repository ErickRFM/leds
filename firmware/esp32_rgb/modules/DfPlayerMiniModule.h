#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include "../core/ModuleRegistry.h"
#include "../../../compi/audio/CompiAudioPolicy.h"

namespace esp32core {

enum class DfPlayerStatus : uint8_t {
  Unconfigured = 0,
  NoResponse = 1,
  Detected = 2,
  NoSdCard = 3,
  SdReady = 4,
  Playing = 5,
  Paused = 6,
  Stopped = 7,
  Error = 8
};

/**
 * DFPlayer Mini UART MP3 module driver for ESP32.
 * Uses non-blocking UART frame exchange, fail-closed state tracking,
 * and integrated Compi environmental voice triggers.
 *
 * UART Pins default: TX = GPIO17 (ESP32 TX -> DFPlayer RX), RX = GPIO18 (ESP32 RX <- DFPlayer TX).
 */
class DfPlayerMiniModule final : public IModule {
 public:
  using UartSendFn = void (*)(const uint8_t* buffer, size_t size, void* context);
  using UartReadFn = size_t (*)(uint8_t* buffer, size_t capacity, void* context);

  DfPlayerMiniModule(uint8_t rxPin = 18, uint8_t txPin = 17,
                     UartSendFn send = nullptr, UartReadFn read = nullptr,
                     void* context = nullptr)
      : rxPin_(rxPin), txPin_(txPin), sendFn_(send), readFn_(read),
        context_(context) {}

  const char* id() const override { return "audio.dfplayer"; }
  uint16_t version() const override { return 1; }

  void setUartCallbacks(UartSendFn send, UartReadFn read, void* context) {
    sendFn_ = send;
    readFn_ = read;
    context_ = context;
  }

  bool begin(PinManager& manager) override {
    if (ready_) return false;
    if (PinManager::reservedForRgb(rxPin_) || PinManager::reservedForRgb(txPin_)) {
      return false;
    }
    if (!PinManager::canUse(rxPin_, PinFunction::DigitalInput) ||
        !PinManager::canUse(txPin_, PinFunction::DigitalOutput)) {
      return false;
    }
    if (!manager.acquire(rxPin_, id(), PinFunction::DigitalInput)) return false;
    if (!manager.acquire(txPin_, id(), PinFunction::DigitalOutput)) {
      manager.release(rxPin_, id());
      return false;
    }
    ready_ = true;
    status_ = DfPlayerStatus::Detected;
    volume_ = 18;
    currentTrack_ = 0;
    autoVoiceEnabled_ = true;
    lastStateCheckMs_ = 0;
    sendFrame(0x06, volume_); // Set initial volume
    return true;
  }

  void stop(PinManager& manager) override {
    if (!ready_) return;
    if (status_ == DfPlayerStatus::Playing) {
      stopPlayback();
    }
    manager.release(rxPin_, id());
    manager.release(txPin_, id());
    ready_ = false;
    status_ = DfPlayerStatus::Unconfigured;
  }

  bool configurePins(uint8_t rxPin, uint8_t txPin) {
    if (ready_ || rxPin == txPin || PinManager::reservedForRgb(rxPin) ||
        PinManager::reservedForRgb(txPin)) return false;
    rxPin_ = rxPin;
    txPin_ = txPin;
    return true;
  }

  void playTrack(uint16_t track) {
    if (!ready_ || track == 0) return;
    currentTrack_ = track;
    sendFrame(0x03, track); // Command 0x03: Play track
    status_ = DfPlayerStatus::Playing;
  }

  void pausePlayback() {
    if (!ready_) return;
    sendFrame(0x0E, 0); // Command 0x0E: Pause
    status_ = DfPlayerStatus::Paused;
  }

  void resumePlayback() {
    if (!ready_) return;
    sendFrame(0x0D, 0); // Command 0x0D: Resume
    status_ = DfPlayerStatus::Playing;
  }

  void stopPlayback() {
    if (!ready_) return;
    sendFrame(0x16, 0); // Command 0x16: Stop
    status_ = DfPlayerStatus::Stopped;
  }

  void nextTrack() {
    if (!ready_) return;
    sendFrame(0x01, 0); // Command 0x01: Next
    status_ = DfPlayerStatus::Playing;
  }

  void previousTrack() {
    if (!ready_) return;
    sendFrame(0x02, 0); // Command 0x02: Previous
    status_ = DfPlayerStatus::Playing;
  }

  void setVolume(uint8_t volume) {
    if (!ready_) return;
    if (volume > 30) volume = 30;
    volume_ = volume;
    sendFrame(0x06, volume); // Command 0x06: Set volume
  }

  void setAutoVoiceEnabled(bool enabled) {
    autoVoiceEnabled_ = enabled;
  }

  bool autoVoiceEnabled() const { return autoVoiceEnabled_; }

  /**
   * Process temperature & humidity to trigger environmental voice announcements.
   */
  void updateEnvironmentalVoice(float celsius, float humidity, uint32_t nowMs) {
    if (!ready_ || !autoVoiceEnabled_) return;
    uint16_t track = tempAnnouncement_.update(celsius, nowMs);
    if (track != 0) {
      playTrack(track);
      return;
    }
    uint16_t humTrack = CompiAudio::humidityTrack(humidity, celsius);
    if (humTrack != 0 && currentTrack_ == 0 && status_ != DfPlayerStatus::Playing) {
      // Optional humidity audio cue when idle
    }
  }

  void tick(uint32_t nowMs) override {
    if (!ready_) return;
    // Process incoming UART responses non-blockingly
    if (readFn_ != nullptr) {
      uint8_t rxBuffer[10];
      size_t readCount = readFn_(rxBuffer, sizeof(rxBuffer), context_);
      if (readCount >= 10 && rxBuffer[0] == 0x7E && rxBuffer[9] == 0xEF) {
        parseResponseFrame(rxBuffer);
      }
    }
    // Query state periodically if needed
    if (lastStateCheckMs_ == 0 || static_cast<uint32_t>(nowMs - lastStateCheckMs_) >= 10000) {
      lastStateCheckMs_ = nowMs;
      sendFrame(0x3F, 0); // Query status / online devices
    }
  }

  uint8_t rxPin() const { return rxPin_; }
  uint8_t txPin() const { return txPin_; }
  bool ready() const { return ready_; }
  DfPlayerStatus status() const { return status_; }
  uint8_t volume() const { return volume_; }
  uint16_t currentTrack() const { return currentTrack_; }

 private:
  void sendFrame(uint8_t command, uint16_t param) {
    if (sendFn_ == nullptr) return;
    uint8_t frame[10];
    frame[0] = 0x7E; // Start byte
    frame[1] = 0xFF; // Version
    frame[2] = 0x06; // Length
    frame[3] = command;
    frame[4] = 0x00; // No ACK
    frame[5] = static_cast<uint8_t>((param >> 8) & 0xFF); // Param High
    frame[6] = static_cast<uint8_t>(param & 0xFF);        // Param Low
    uint16_t sum = 0;
    for (size_t i = 1; i <= 6; ++i) sum += frame[i];
    uint16_t checksum = 0 - sum;
    frame[7] = static_cast<uint8_t>((checksum >> 8) & 0xFF);
    frame[8] = static_cast<uint8_t>(checksum & 0xFF);
    frame[9] = 0xEF; // End byte
    sendFn_(frame, sizeof(frame), context_);
  }

  void parseResponseFrame(const uint8_t* frame) {
    uint8_t event = frame[3];
    uint16_t param = (static_cast<uint16_t>(frame[5]) << 8) | frame[6];
    switch (event) {
      case 0x3A: // Card inserted / online
        status_ = DfPlayerStatus::SdReady;
        break;
      case 0x3B: // Card removed
        status_ = DfPlayerStatus::NoSdCard;
        break;
      case 0x3D: // Track finished playing
        if (status_ == DfPlayerStatus::Playing) {
          status_ = DfPlayerStatus::Stopped;
        }
        break;
      case 0x40: // Error
        status_ = DfPlayerStatus::Error;
        break;
      default:
        break;
    }
  }

  uint8_t rxPin_;
  uint8_t txPin_;
  UartSendFn sendFn_;
  UartReadFn readFn_;
  void* context_;
  bool ready_ = false;
  DfPlayerStatus status_ = DfPlayerStatus::Unconfigured;
  uint8_t volume_ = 18;
  uint16_t currentTrack_ = 0;
  bool autoVoiceEnabled_ = true;
  uint32_t lastStateCheckMs_ = 0;
  CompiAudio::TemperatureAnnouncement tempAnnouncement_{};
};

} // namespace esp32core
