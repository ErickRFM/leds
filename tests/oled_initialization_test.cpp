#include <cassert>
#include <cstdio>
#include <cstring>
#include "../firmware/esp32_rgb/core/MediaControlProtocol.h"
using namespace esp32core;
int main() {
  OledSnapshot state{};
  char scene[12]="dance";
  bool dancing=true;
  uint32_t userAt=20000,frameAt=21000;
  state.contrast=240;state.inverted=true;state.paused=true;state.scene=scene;
  resetOledReadbackAfterInit(state,scene,dancing,userAt,frameAt);
  assert(state.ready && state.contrast==127 && !state.inverted && !state.paused);
  assert(!std::strcmp(state.scene,"text") && !std::strcmp(scene,"text"));
  assert(!dancing && userAt==0 && frameAt==0);
  char json[160]{};
  assert(encodeOledStatus(state,json,sizeof(json)));
  assert(std::strstr(json,"\"contrast\":127") && std::strstr(json,"\"inverted\":false") &&
      std::strstr(json,"\"paused\":false") && std::strstr(json,"\"scene\":\"text\""));
  puts("OLED reinitialization readback matches reset hardware and cancels old animation: ALL OK");
}
