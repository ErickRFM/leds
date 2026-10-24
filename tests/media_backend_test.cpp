#include <cassert>
#include <cstdio>
#include <cstring>
#include "../firmware/esp32_rgb/core/MediaControlBackend.h"
using namespace esp32core;
struct Hardware {AudioSnapshot audio{};OledSnapshot oled{};int calls=0;bool accept=true;};
static AudioSnapshot audioStatus(void* context) {return static_cast<Hardware*>(context)->audio;}
static OledSnapshot oledStatus(void* context) {return static_cast<Hardware*>(context)->oled;}
static bool audioCommand(const char*,int,void* context) {auto& h=*static_cast<Hardware*>(context);++h.calls;return h.accept;}
static bool oledCommand(const char* verb,const char* value,void* context) {
  auto& h=*static_cast<Hardware*>(context);++h.calls;
  if(!h.accept) return false;
  if(!std::strcmp(verb,"pause")) h.oled.paused=!std::strcmp(value,"1");
  if(!std::strcmp(verb,"invert")) h.oled.inverted=!std::strcmp(value,"1");
  return true;
}
int main() {
  MediaControlBackend backend;Hardware hardware;
  backend.setAudioControl(audioCommand,&hardware);backend.setAudioStatus(audioStatus);
  backend.setOledDisplay(oledCommand,&hardware);backend.setOledStatus(oledStatus);
  MediaCommand cmd{}; char reply[125]{};char json[240]{};
  assert(parseMediaCommand("AUD,0123456789,PLAY,7",cmd));
  assert(backend.mediaReply(cmd,reply,sizeof(reply)));
  assert(!std::strcmp(reply,"AUD,0123456789,ACCEPTED,0,-1,-1,UNKNOWN,-1,-1,0,0"));
  assert(backend.audioSnapshot().track==-1); // Receipt must not invent playback.
  assert(!backend.applyAudio("volume",31));assert(!backend.applyOled("invert","2"));
  assert(hardware.calls==1);
  hardware.audio.modulePresent=true;hardware.audio.sdPresent=1;hardware.audio.tracks=12;
  hardware.audio.playback="PAUSED";hardware.audio.track=4;hardware.audio.volume=5;
  hardware.audio.voiceEnabled=true;hardware.audio.fresh=true;
  assert(parseMediaCommand("AUD,after,GET",cmd));
  assert(backend.mediaReply(cmd,reply,sizeof(reply)));
  assert(!std::strcmp(reply,"AUD,after,OK,1,1,12,PAUSED,4,5,1,1"));
  assert(encodeAudioStatus(backend.audioSnapshot(),json,sizeof(json)));
  assert(std::strstr(json,"\"playback\":\"PAUSED\"") && std::strstr(json,"\"voiceEnabled\":true"));
  assert(backend.applyOled("pause","1"));
  assert(parseMediaCommand("OLD,same,GET",cmd));assert(backend.mediaReply(cmd,reply,sizeof(reply)));
  assert(!std::strcmp(reply,"OLD,same,OK,0,telemetry,127,0,1"));
  hardware.accept=false;
  assert(parseMediaCommand("OLD,no,INVERT,1",cmd));assert(backend.mediaReply(cmd,reply,sizeof(reply)));
  assert(!std::strcmp(reply,"OLD,no,REJECTED,0,telemetry,127,0,1"));
  assert(encodeOledStatus(backend.oledSnapshot(),json,sizeof(json)));
  assert(std::strstr(json,"\"paused\":true") && std::strstr(json,"\"inverted\":false"));
  assert(!backend.mediaReply(cmd,reply,5));
  puts("Shared HTTP/BLE media validation, correlation and truthful readback: ALL OK");
}
