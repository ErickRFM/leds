#pragma once
#include <cstddef>
#include <cstdio>
#include <cstring>
#include "DfPlayerRuntime.h"
namespace esp32core {
struct OledSnapshot { bool ready=false; const char* scene="telemetry"; int contrast=127; bool inverted=false; bool paused=false; };
// The SSD1306 driver clears RAM and applies these defaults on every begin(),
// including GPIO-remap success and rollback. Old animation state must not
// repaint an old scene or report settings the reinitialized device lost.
inline void resetOledReadbackAfterInit(OledSnapshot& state,char (&scene)[12],
    bool& dancing,uint32_t& lastUserAt,uint32_t& lastFrameAt) {
  state=OledSnapshot{};
  state.ready=true;
  std::strcpy(scene,"text"); // The cleared framebuffer is empty text.
  state.scene=scene;
  dancing=false;
  lastUserAt=lastFrameAt=0;
}
inline bool encodeAudioStatus(const AudioSnapshot& state,char* output,size_t capacity) {
  const int n=std::snprintf(output,capacity,"{\"modulePresent\":%s,\"sdPresent\":%d,\"tracks\":%d,\"playback\":\"%s\",\"track\":%d,\"volume\":%d,\"voiceEnabled\":%s,\"fresh\":%s}",state.modulePresent?"true":"false",state.sdPresent,state.tracks,state.playback,state.track,state.volume,state.voiceEnabled?"true":"false",state.fresh?"true":"false");
  return n>0 && static_cast<size_t>(n)<capacity;
}
inline bool encodeOledStatus(const OledSnapshot& state,char* output,size_t capacity) {
  const int n=std::snprintf(output,capacity,"{\"ready\":%s,\"scene\":\"%s\",\"contrast\":%d,\"inverted\":%s,\"paused\":%s}",state.ready?"true":"false",state.scene,state.contrast,state.inverted?"true":"false",state.paused?"true":"false");
  return n>0 && static_cast<size_t>(n)<capacity;
}
enum class MediaFamily { Audio, Oled };
struct MediaCommand { MediaFamily family{}; char requestId[11]{}; char verb[10]{}; char value[40]{}; int parameter=0; };
inline bool mediaDecimal(const char* text, int minimum, int maximum, int& value) {
  if (!text || !*text) return false;
  unsigned result=0;
  for (size_t i=0; text[i]; ++i) {
    if (i>=4 || text[i]<'0' || text[i]>'9') return false;
    result=result*10u+static_cast<unsigned>(text[i]-'0');
    if (result>static_cast<unsigned>(maximum)) return false;
  }
  if (result<static_cast<unsigned>(minimum)) return false;
  value=static_cast<int>(result); return true;
}
inline bool mediaFace(const char* value) {
  return value && (!std::strcmp(value,"happy") || !std::strcmp(value,"sleepy") ||
      !std::strcmp(value,"angry") || !std::strcmp(value,"surprised") ||
      !std::strcmp(value,"pumpkin") || !std::strcmp(value,"cat") || !std::strcmp(value,"dance"));
}
// Shared validation is also applied to authenticated HTTP arguments.
inline bool validMediaCommand(const MediaCommand& cmd) {
  const char* verb=cmd.verb; int ignored=0;
  if (!std::strcmp(verb,"get")) return !*cmd.value;
  if (cmd.family==MediaFamily::Audio) {
    if (!std::strcmp(verb,"play")) return mediaDecimal(cmd.value,1,2999,ignored);
    if (!std::strcmp(verb,"volume")) return mediaDecimal(cmd.value,0,30,ignored);
    if (!std::strcmp(verb,"voice")) return mediaDecimal(cmd.value,0,1,ignored);
    return (!std::strcmp(verb,"stop") || !std::strcmp(verb,"pause") || !std::strcmp(verb,"resume")) && !*cmd.value;
  }
  if (!std::strcmp(verb,"face")) return mediaFace(cmd.value);
  if (!std::strcmp(verb,"contrast")) return mediaDecimal(cmd.value,8,255,ignored);
  if (!std::strcmp(verb,"invert") || !std::strcmp(verb,"pause")) return mediaDecimal(cmd.value,0,1,ignored);
  if (std::strcmp(verb,"text") || !*cmd.value) return false;
  for(size_t i=0;cmd.value[i];++i) if (static_cast<unsigned char>(cmd.value[i])<32 || static_cast<unsigned char>(cmd.value[i])>126) return false;
  return true;
}
inline bool parseMediaCommand(const char* line, MediaCommand& result) {
  if (!line) return false;
  size_t length=0; while(length<=64 && line[length]) ++length;
  if (length>64 || length<9 || line[3]!=',') return false;
  MediaCommand cmd{};
  if (!std::strncmp(line,"AUD,",4)) cmd.family=MediaFamily::Audio;
  else if (!std::strncmp(line,"OLD,",4)) cmd.family=MediaFamily::Oled;
  else return false;
  const char* pos=line+4; size_t n=0;
  while(*pos && *pos!=',') {
    const char ch=*pos++;
    if(n==10 || !((ch>='a' && ch<='z') || (ch>='A' && ch<='Z') || (ch>='0' && ch<='9') || ch=='_' || ch=='-')) return false;
    cmd.requestId[n++]=ch;
  }
  if(!n || *pos++!=',') return false;
  n=0;
  while(*pos && *pos!=',') {
    if(n==9 || *pos<'A' || *pos>'Z') return false;
    cmd.verb[n++]=static_cast<char>(*pos++-'A'+'a');
  }
  if(*pos==',') { ++pos; if(!*pos || std::strlen(pos)>39) return false; std::strcpy(cmd.value,pos); }
  if (!validMediaCommand(cmd)) return false;
  if (*cmd.value && cmd.family==MediaFamily::Audio) mediaDecimal(cmd.value,0,2999,cmd.parameter);
  result=cmd; return true;
}
} // namespace esp32core
