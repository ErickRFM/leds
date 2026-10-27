#pragma once
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace esp32core {
// Voice bank is /MP3/2000.mp3..2200.mp3; preserve 0001-0009 event cues.
struct SpokenMeasurements {
  bool temperatureFresh = false;
  float temperatureC = 0;
  bool humidityFresh = false;
  float humidityPercent = 0;
  bool lightFresh = false;
  float lightPercent = 0;  // Calibrated LDR %, not lux.
};

class AutonomousSpokenReport {
 public:
  using PlayTrack = bool (*)(uint16_t, void*);
  enum class Result : uint8_t { None, Complete, NoBusySignal, ClipTimeout, Canceled };
  static constexpr uint16_t kTemperature = 2000, kCelsius = 2001,
      kHumidity = 2002, kPercent = 2003, kLight = 2004,
      kComma = 2005, kMinus = 2006, kNumberBase = 2100, kOneHundred = 2200;
  static constexpr size_t kCapacity = 20;

  bool start(const SpokenMeasurements& s, uint32_t now) {
    if (active()) return false;
    count_ = index_ = 0;
    if (s.temperatureFresh && std::isfinite(s.temperatureC) &&
        s.temperatureC >= -40.f && s.temperatureC <= 80.f) {
      append(kTemperature);
      const int t = static_cast<int>(std::lround(s.temperatureC * 10.f));
      if (t < 0) append(kMinus);
      const int a = t < 0 ? -t : t;
      number(a / 10);
      if (a % 10 != 0) { append(kComma); number(a % 10); }
      append(kCelsius);
    }
    if (s.humidityFresh && std::isfinite(s.humidityPercent) &&
        s.humidityPercent >= 0.f && s.humidityPercent <= 100.f) {
      append(kHumidity);
      number(static_cast<int>(std::lround(s.humidityPercent)));
      append(kPercent);
    }
    if (s.lightFresh && std::isfinite(s.lightPercent) &&
        s.lightPercent >= 0.f && s.lightPercent <= 100.f) {
      append(kLight);
      number(static_cast<int>(std::lround(s.lightPercent)));
      append(kPercent);
    }
    if (count_ == 0) return false;
    phase_ = Phase::Ready; phaseAt_ = now; result_ = Result::None;
    return true;
  }

  // BUSY is LOW when the DFPlayer is actively playing a track.
  void tick(uint32_t now, bool playing, PlayTrack play, void* context) {
    switch (phase_) {
      case Phase::Idle: return;
      case Phase::Ready:
        if (playing || static_cast<uint32_t>(now - phaseAt_) < 250u) return;
        if (!play || !play(tracks_[index_], context)) {
          if (static_cast<uint32_t>(now - phaseAt_) >= 5000u)
            finish(Result::NoBusySignal);
          return;
        }
        phase_ = Phase::WaitStart; phaseAt_ = now; return;
      case Phase::WaitStart:
        if (playing) { phase_ = Phase::WaitEnd; phaseAt_ = now; }
        else if (static_cast<uint32_t>(now - phaseAt_) >= 5000u)
          finish(Result::NoBusySignal);
        return;
      case Phase::WaitEnd:
        if (!playing) { phase_ = Phase::Gap; phaseAt_ = now; }
        else if (static_cast<uint32_t>(now - phaseAt_) >= 20000u)
          finish(Result::ClipTimeout);
        return;
      case Phase::Gap:
        if (playing || static_cast<uint32_t>(now - phaseAt_) < 300u) return;
        ++index_;
        if (index_ >= count_) finish(Result::Complete);
        else { phase_ = Phase::Ready; phaseAt_ = now; }
        return;
    }
  }
  bool active() const { return phase_ != Phase::Idle; }
  Result result() const { return result_; }
  size_t count() const { return count_; }
  uint16_t track(size_t i) const { return i < count_ ? tracks_[i] : 0; }
  void cancel() { if (active()) finish(Result::Canceled); }

 private:
  enum class Phase : uint8_t { Idle, Ready, WaitStart, WaitEnd, Gap };
  void append(uint16_t id) { if (count_ < kCapacity) tracks_[count_++] = id; }
  void number(int n) {
    if (n >= 0 && n <= 99) append(kNumberBase + static_cast<uint16_t>(n));
    else if (n == 100) append(kOneHundred);
  }
  void finish(Result r) { phase_ = Phase::Idle; result_ = r; }
  uint16_t tracks_[kCapacity]{};
  size_t count_ = 0, index_ = 0;
  Phase phase_ = Phase::Idle;
  Result result_ = Result::None;
  uint32_t phaseAt_ = 0;
};
}  // namespace esp32core
