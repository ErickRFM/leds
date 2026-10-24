#pragma once
#include "MediaControlProtocol.h"
namespace esp32core {
// Hardware callbacks are transport-independent; callers enforce their channel
// authorization before execution. HTTP and BLE read exactly this state.
class MediaControlBackend final {
 public:
  using OledDisplay=bool (*)(const char*,const char*,void*);
  using AudioCommand=bool (*)(const char*,int,void*);
  using AudioStatus=AudioSnapshot (*)(void*);
  using OledStatus=OledSnapshot (*)(void*);
  bool oledAvailable() const {return oledDisplay_!=nullptr;}
  void setOledDisplay(OledDisplay display, void* context) {
    oledDisplay_ = display;
    oledContext_ = context;
  }

  void setAudioControl(AudioCommand audio, void* context) {
    audio_ = audio;
    audioContext_ = context;
  }

  void setAudioStatus(AudioStatus source) { audioStatus_=source; }
  void setOledStatus(OledStatus source) { oledStatus_=source; }
  AudioSnapshot audioSnapshot() const { return audioStatus_ ? audioStatus_(audioContext_) : AudioSnapshot{}; }
  OledSnapshot oledSnapshot() const { return oledStatus_ ? oledStatus_(oledContext_) : OledSnapshot{}; }
  bool applyAudio(const char* verb,int parameter) {
    if(!verb || std::strlen(verb)>=10 || !audio_) return false;
    MediaCommand cmd{}; cmd.family=MediaFamily::Audio; std::strcpy(cmd.verb,verb);
    if(!std::strcmp(verb,"play") || !std::strcmp(verb,"volume") || !std::strcmp(verb,"voice"))
      std::snprintf(cmd.value,sizeof(cmd.value),"%d",parameter);
    else if(parameter!=0) return false;
    return validMediaCommand(cmd) && std::strcmp(verb,"get") && audio_(verb,parameter,audioContext_);
  }
  bool applyOled(const char* mode,const char* value) {
    if(!mode || !value || std::strlen(mode)>=10 || std::strlen(value)>=40 || !oledDisplay_) return false;
    MediaCommand cmd{};cmd.family=MediaFamily::Oled;std::strcpy(cmd.verb,mode);std::strcpy(cmd.value,value);
    return validMediaCommand(cmd) && std::strcmp(mode,"get") && oledDisplay_(mode,value,oledContext_);
  }
  // Called only after the same owner gate as every other BLE command.
  bool mediaReply(const MediaCommand& cmd,char* output,size_t capacity) {
    const bool query=!std::strcmp(cmd.verb,"get");
    if(cmd.family==MediaFamily::Audio) {
      const bool accepted=query ? audioStatus_!=nullptr : applyAudio(cmd.verb,cmd.parameter);
      const auto state=audioSnapshot();
      const char* receipt=accepted ? (query || !std::strcmp(cmd.verb,"voice") ? "OK" : "ACCEPTED") : "REJECTED";
      const int n=std::snprintf(output,capacity,"AUD,%s,%s,%d,%d,%d,%s,%d,%d,%d,%d",cmd.requestId,receipt,state.modulePresent?1:0,state.sdPresent,state.tracks,state.playback,state.track,state.volume,state.voiceEnabled?1:0,state.fresh?1:0);
      return n>0 && static_cast<size_t>(n)<capacity;
    }
    const bool accepted=query ? oledStatus_!=nullptr : applyOled(cmd.verb,cmd.value);
    const auto state=oledSnapshot();
    const int n=std::snprintf(output,capacity,"OLD,%s,%s,%d,%s,%d,%d,%d",cmd.requestId,accepted?"OK":"REJECTED",state.ready?1:0,state.scene,state.contrast,state.inverted?1:0,state.paused?1:0);
    return n>0 && static_cast<size_t>(n)<capacity;
  }

 private:
  OledDisplay oledDisplay_=nullptr;void* oledContext_=nullptr;
  AudioCommand audio_=nullptr;void* audioContext_=nullptr;
  AudioStatus audioStatus_=nullptr;OledStatus oledStatus_=nullptr;
};
} // namespace esp32core
