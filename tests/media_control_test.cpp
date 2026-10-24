#include <cassert>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include "../firmware/esp32_rgb/core/MediaControlProtocol.h"
using namespace esp32core;
int main() {
  MediaCommand command{};
  assert(parseMediaCommand("AUD,abc-9,PLAY,2999", command));
  assert(command.family==MediaFamily::Audio && command.parameter==2999);
  assert(std::strcmp(command.requestId,"abc-9")==0);
  assert(parseMediaCommand("OLD,123,FACE,dance", command));
  assert(std::strcmp(command.verb,"face")==0 && std::strcmp(command.value,"dance")==0);
  assert(parseMediaCommand("OLD,x,TEXT,hello, world", command));
  assert(std::strcmp(command.value,"hello, world")==0);
  for(const char* bad:{"AUD,x,PLAY,0","AUD,x,PLAY,3000","AUD,x,VOLUME,31","AUD,x,GET,1","AUD,,GET","AUD,abcdefghijkl,GET","AUD,x/1,GET","AUD,x,VOICE,2","OLD,x,FACE,unknown","OLD,x,CONTRAST,7","OLD,x,INVERT,2","OLD,x,PAUSE,-1","OLD,x,TEXT,","AUD,x,STOP\n","OLD,x,TEXT,hi\tthere"})
    assert(!parseMediaCommand(bad,command));
  assert(!parseMediaCommand(nullptr,command));
  char maximum[80]; std::snprintf(maximum,sizeof(maximum),"OLD,1234567890,TEXT,%039d",1);
  assert(parseMediaCommand(maximum,command));
  std::strcat(maximum,"x"); assert(!parseMediaCommand(maximum,command));
  puts("Bounded media commands and correlation IDs: ALL OK");
}
