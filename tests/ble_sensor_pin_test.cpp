#include <cassert>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include "../firmware/esp32_rgb/core/BleSensorPinRequest.h"
#include "../firmware/esp32_rgb/core/BleSensorPinControl.h"
using namespace esp32core;
struct Physical {uint8_t pin=16;int writes=0;};
static bool apply(const char* component,uint8_t from,uint8_t from2,uint8_t to,uint8_t to2,uint8_t from3,uint8_t to3,void* context) {
  auto& physical=*static_cast<Physical*>(context);
  assert(!std::strcmp(component,"dht11") && from2==0 && to2==0 && from3==0 && to3==0);
  if(from!=physical.pin || to==0) return false;
  physical.pin=to;++physical.writes;return true;
}
int main() {
  BleSensorPinRequest request{};
  assert(parseBleSensorPinRequest("PIN,0123456789abcdef,D,16,33",request));
  assert(!std::strcmp(request.token,"0123456789abcdef") && !std::strcmp(request.component,"dht11"));
  assert(request.from==16 && request.to==33);
  for(const char* bad:{"","PIN,","PIN,1","PIN,0123456789abcdef","PIN,0123456789abcdef,D","PIN,0123456789abcdef,D,16","PIN,0123456789abcdef,D,16,40","PIN,0123456789abcdef,D,16,2,3","PIN,0123456789abcdef,X,16,33","PIN,0123456789abcdeg,D,16,33","PIN,0123456789abcdef,D,16,33\n"})
    assert(!parseBleSensorPinRequest(bad,request));
  assert(!parseBleSensorPinRequest(nullptr,request));
  Physical physical;BleSensorPinControl control;
  control.setApply(apply,&physical);
  assert(control.apply(request,true)==BlePinApplyResult::Busy && physical.writes==0);
  assert(control.apply(request,false)==BlePinApplyResult::Applied && physical.pin==33);
  assert(control.apply(request,false)==BlePinApplyResult::Replayed && physical.writes==1);
  request.to=32;
  assert(control.apply(request,false)==BlePinApplyResult::Rejected && physical.pin==33);
  std::strcpy(request.token,"fedcba9876543210");
  assert(control.apply(request,false)==BlePinApplyResult::Rejected && physical.writes==1); // stale CAS
  request.from=33;
  assert(control.apply(request,false)==BlePinApplyResult::Applied && physical.pin==32 && physical.writes==2);
  puts("BLE sensor PIN bounded parser, busy, CAS, isolated replay ledger: ALL OK");
}
