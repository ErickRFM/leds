#include <cassert>
#include <cstdint>
#include <limits>
#include "../firmware/esp32_rgb/core/AutonomousSpokenReport.h"
using namespace esp32core;
struct Sender { unsigned calls=0; uint16_t last=0; };
static bool play(uint16_t track, void* context) {
  auto& s=*static_cast<Sender*>(context);
  ++s.calls; s.last=track; return true;
}
int main() {
  AutonomousSpokenReport report;
  SpokenMeasurements s{};
  assert(!report.start(s,0));
  s.temperatureFresh=true;s.temperatureC=23.5f;
  s.humidityFresh=true;s.humidityPercent=46.f;
  s.lightFresh=true;s.lightPercent=72.f;
  assert(report.start(s,0));
  const uint16_t tracks[]={2000,2123,2005,2105,2001,2002,2146,2003,2004,2172,2003};
  assert(report.count()==sizeof(tracks)/sizeof(tracks[0]));
  for(size_t i=0;i<report.count();++i) assert(report.track(i)==tracks[i]);
  assert(!report.start(s,1));
  Sender sender;
  report.tick(250,false,play,&sender);
  assert(sender.calls==1 && sender.last==2000);
  report.tick(300,true,play,&sender);
  report.tick(500,false,play,&sender);
  report.tick(900,false,play,&sender);
  report.tick(1200,false,play,&sender);
  assert(sender.last==2123);
  uint32_t t=1200;
  for(size_t i=1;i<report.count();++i) {
    report.tick(t+100,true,play,&sender);
    report.tick(t+300,false,play,&sender);
    report.tick(t+650,false,play,&sender);
    if(i+1<report.count()) {
      report.tick(t+1000,false,play,&sender);
      assert(sender.last==tracks[i+1]);t+=1000;
    }
  }
  assert(!report.active() &&
         report.result()==AutonomousSpokenReport::Result::Complete);
  s.temperatureC=-2.4f;s.humidityPercent=100.f;s.lightFresh=false;
  assert(report.start(s,30000));
  assert(report.track(1)==2006 && report.track(2)==2102 &&
         report.track(3)==2005 && report.track(4)==2104 &&
         report.track(7)==2200);
  report.cancel();
  assert(report.result()==AutonomousSpokenReport::Result::Canceled);
  s.temperatureC=std::numeric_limits<float>::quiet_NaN();
  s.humidityPercent=101.f;
  assert(!report.start(s,31000));
  s.temperatureFresh=false;s.humidityFresh=false;
  s.lightFresh=true;s.lightPercent=5;
  assert(report.start(s,32000));
  assert(report.count()==3 && report.track(0)==2004 && report.track(1)==2105);
  // No connected BUSY wire or missing MP3: no fictitious completion.
  report.tick(32300,false,play,&sender);
  report.tick(37400,false,play,&sender);
  assert(report.result()==AutonomousSpokenReport::Result::NoBusySignal);
  assert(report.start(s,38000));
  report.tick(38300,false,play,&sender);
  report.tick(38400,true,play,&sender);
  report.tick(59000,true,play,&sender);
  assert(report.result()==AutonomousSpokenReport::Result::ClipTimeout);
}
