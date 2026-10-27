#pragma once
#include <stdint.h>
#include <cmath>

namespace esp32core {
// Values are stable /MP3/NNNN.mp3 filenames, not SD insertion indices.
enum class VoiceCue : uint8_t {
  None=0, Hello=1, Cold=2, Comfortable=3, Warm=4,
  Hot=5, Extreme=6, Humid=7, Dark=8, LightRestored=9
};
struct VoiceSample {
  bool temperatureFresh=false; float temperatureC=0;
  bool humidityFresh=false; float humidityPercent=0;
  bool lightFresh=false; float lightPercent=0;
};
class EnvironmentalVoicePolicy {
 public:
  VoiceCue evaluate(const VoiceSample& s, uint32_t now) {
    const bool tempOK=s.temperatureFresh && std::isfinite(s.temperatureC) && s.temperatureC>=-40 && s.temperatureC<=80;
    const bool humOK=s.humidityFresh && std::isfinite(s.humidityPercent) && s.humidityPercent>=0 && s.humidityPercent<=100;
    const bool lightOK=s.lightFresh && std::isfinite(s.lightPercent) && s.lightPercent>=0 && s.lightPercent<=100;
    if (!greeted_) {
      if (now<3000u) return VoiceCue::None;
      greeted_=true; mark(now); return VoiceCue::Hello;
    }
    VoiceCue next=VoiceCue::None;
    if (tempOK) {
      if (s.temperatureC<20) next=VoiceCue::Cold;
      else if (s.temperatureC<26) next=VoiceCue::Comfortable;
      else if (s.temperatureC<30) next=VoiceCue::Warm;
      else if (s.temperatureC<35) next=VoiceCue::Hot;
      else next=VoiceCue::Extreme;
      if (next==pendingTemp_) {if (sameTemp_<3) ++sameTemp_;}
      else {pendingTemp_=next; sameTemp_=1;}
    } else {pendingTemp_=VoiceCue::None; sameTemp_=0;}
    if (humOK) {
      if (s.humidityPercent>=95) humid_=true;
      else if (s.humidityPercent<=90) {humid_=false; humidityAnnounced_=false;}
    }
    if (lightOK) {
      if (s.lightPercent<=20) dark_=true;
      else if (s.lightPercent>=30) dark_=false;
    }
    VoiceCue requested=VoiceCue::None;
    if (tempOK && sameTemp_>=3 && next!=announcedTemp_) requested=next;
    else if (humOK && humid_ && !humidityAnnounced_) requested=VoiceCue::Humid;
    else if (lightOK && dark_ && !darkAnnounced_) requested=VoiceCue::Dark;
    else if (lightOK && !dark_ && darkAnnounced_) requested=VoiceCue::LightRestored;
    if (requested==VoiceCue::None || (played_ && uint32_t(now-lastPlay_)<15000u)) return VoiceCue::None;
    mark(now);
    if (requested==VoiceCue::Humid) humidityAnnounced_=true;
    else if (requested==VoiceCue::Dark) darkAnnounced_=true;
    else if (requested==VoiceCue::LightRestored) darkAnnounced_=false;
    else announcedTemp_=requested;
    return requested;
  }
 private:
  void mark(uint32_t now) {lastPlay_=now;played_=true;}
  bool greeted_=false,played_=false,humid_=false,humidityAnnounced_=false,dark_=false,darkAnnounced_=false;
  VoiceCue pendingTemp_=VoiceCue::None,announcedTemp_=VoiceCue::None;
  uint8_t sameTemp_=0;
  uint32_t lastPlay_=0;
};
} // namespace esp32core
