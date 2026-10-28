#include <cassert>
#include <cstdint>
#include <limits>
#include "../firmware/esp32_rgb/core/AutonomousSpokenReport.h"
#include "../firmware/esp32_rgb/core/EnvironmentalVoicePolicy.h"
#include "../firmware/esp32_rgb/modules/LdrLightModule.h"
using namespace esp32core;
struct Sender { unsigned calls=0; uint16_t last=0; };
static int readDark(uint8_t, void*) { return 0; }
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

  // End-to-end: a fresh LDR raw=0 becomes a truthful relative 0%, a spoken
  // light fragment and a low-light automatic cue even without NVS calibration.
  PinManager pins;
  LdrLightModule ldr(32, readDark, nullptr);
  assert(ldr.begin(pins));
  assert(ldr.applySample(0, 61000));
  assert(!ldr.calibrated() && ldr.lightReading().valid);
  assert(ldr.lightReading().value==0.f);
  SpokenMeasurements dark{};
  dark.lightFresh=ldr.lightReading().valid;
  dark.lightPercent=ldr.lightReading().value;
  AutonomousSpokenReport lightReport;
  assert(lightReport.start(dark, 61000));
  assert(lightReport.count()==3);
  assert(lightReport.track(0)==AutonomousSpokenReport::kLight);
  assert(lightReport.track(1)==AutonomousSpokenReport::kNumberBase);
  assert(lightReport.track(2)==AutonomousSpokenReport::kPercent);
  EnvironmentalVoicePolicy lightPolicy;
  VoiceSample lightSample{};
  lightSample.lightFresh=true;
  lightSample.lightPercent=dark.lightPercent;
  assert(lightPolicy.evaluate(lightSample,3000)==VoiceCue::Hello);
  assert(lightPolicy.evaluate(lightSample,19000)==VoiceCue::Dark);
  for(int i=0;i<30;++i) assert(ldr.applySample(1135,62000u+static_cast<uint32_t>(i)));
  assert(ldr.lightReading().value>90.f);
  lightSample.lightPercent=ldr.lightReading().value;
  assert(lightPolicy.evaluate(lightSample,40000)==VoiceCue::LightRestored);
  ldr.stop(pins);
}
