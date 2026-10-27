#include <cassert>
#include <cstdio>
#include <cstring>
#include "../firmware/esp32_rgb/core/DfPlayerRuntime.h"
using namespace esp32core;
struct Wire { uint8_t bytes[10]{}; int count=0; bool accept=true; };
static bool send(const uint8_t* bytes, size_t length, void* context) {
  auto& wire=*static_cast<Wire*>(context); assert(length==10);
  std::memcpy(wire.bytes, bytes, length); ++wire.count; return wire.accept;
}
static void receive(DfPlayerRuntime& runtime, uint8_t command, uint16_t parameter, uint32_t now) {
  uint8_t frame[10]={0x7e,0xff,6,command,0,static_cast<uint8_t>(parameter>>8),static_cast<uint8_t>(parameter),0,0,0xef};
  const uint16_t sum=static_cast<uint16_t>(0u-(0xffu+6u+command+frame[5]+frame[6]));
  frame[7]=sum>>8; frame[8]=sum;
  for (uint8_t byte:frame) runtime.consume(byte,now);
}
int main() {
  Wire wire; DfPlayerRuntime runtime(send,&wire);
  assert(!runtime.snapshot(0).modulePresent);
  assert(!runtime.command("play",1,0));
  runtime.tick(100); assert(wire.bytes[3]==0x3f);
  // A corrupt checksum cannot manufacture presence, SD or playback.
  const uint8_t corrupt[]={0x7e,0xff,6,0x42,0,2,1,0,0,0xef};
  for(uint8_t byte:corrupt) runtime.consume(byte,101);
  assert(!runtime.snapshot(101).modulePresent);
  receive(runtime,0x3f,2,110); runtime.tick(210);
  receive(runtime,0x42,0x0201,220); runtime.tick(320);
  receive(runtime,0x48,12,330); runtime.tick(430);
  receive(runtime,0x4c,3,440); runtime.tick(540);
  receive(runtime,0x43,5,550);
  auto state=runtime.snapshot(550);
  assert(state.modulePresent && state.sdPresent==1 && state.tracks==12);
  assert(state.fresh && state.track==3 && state.volume==5);
  assert(std::strcmp(state.playback,"PLAYING")==0);
  assert(runtime.command("pause",0,560));
  assert(std::strcmp(runtime.snapshot(560).playback,"UNKNOWN")==0);
  // An earlier poll can arrive while the command waits for UART spacing.
  receive(runtime,0x42,0x0201,570);
  runtime.tick(650); assert(wire.bytes[3]==0x0e);
  // UART success is acceptance only; a query reply is needed to say paused.
  assert(std::strcmp(runtime.snapshot(650).playback,"UNKNOWN")==0);
  // Delayed pre-command data AFTER the physical PAUSE must also be ignored.
  receive(runtime,0x42,0x0201,655);
  assert(std::strcmp(runtime.snapshot(655).playback,"UNKNOWN")==0);
  receive(runtime,0x41,0,660);
  assert(std::strcmp(runtime.snapshot(660).playback,"UNKNOWN")==0);
  runtime.tick(1100);assert(wire.bytes[3]==0x3f);
  receive(runtime,0x3f,2,1110);
  runtime.tick(1210);assert(wire.bytes[3]==0x42);
  receive(runtime,0x42,0x0202,1220);
  assert(std::strcmp(runtime.snapshot(1220).playback,"PAUSED")==0);
  receive(runtime,0x3d,3,1230);
  assert(std::strcmp(runtime.snapshot(1230).playback,"STOPPED")==0);
  runtime.tick(1330);assert(wire.bytes[3]==0x48);
  receive(runtime,0x3b,2,1340); state=runtime.snapshot(1340);
  assert(state.sdPresent==0 && state.tracks==-1 && state.track==-1);
  // Removal invalidates the outstanding count query and old track replies.
  receive(runtime,0x48,12,1350);receive(runtime,0x4c,3,1360);
  state=runtime.snapshot(1360);
  assert(state.sdPresent==0 && state.tracks==-1 && state.track==-1 && !state.fresh);
  assert(!runtime.command("play",1,1400));
  assert(!runtime.snapshot(7000).modulePresent);
  assert(std::strcmp(runtime.snapshot(7000).playback,"UNKNOWN")==0);
  // Bounded command queue refuses flooding instead of allocating.
  for(int i=0;i<4;++i) assert(runtime.command("volume",i,7000));
  assert(!runtime.command("volume",5,7000));
  assert(!runtime.command("volume",31,7000));
  assert(!runtime.command("play",3000,7000));
  // Partial frames expire and a subsequent valid frame recovers.
  runtime.consume(0x7e,8000); runtime.consume(0xff,8001);
  receive(runtime,0x3a,2,8300); assert(runtime.snapshot(8300).modulePresent);
  // A query reply without a matching request, or past its deadline, is inert.
  Wire idleWire;DfPlayerRuntime idle(send,&idleWire);
  receive(idle,0x42,0x0201,100);assert(!idle.snapshot(100).modulePresent);
  idle.tick(100);receive(idle,0x3f,2,700);
  assert(!idle.snapshot(700).modulePresent);
  Wire pendingWire;DfPlayerRuntime pending(send,&pendingWire);
  pending.tick(100);receive(pending,0x3f,2,110);
  pending.tick(210);assert(pendingWire.bytes[3]==0x42);
  assert(pending.command("pause",0,220));
  receive(pending,0x42,0x0201,230);
  assert(std::strcmp(pending.snapshot(230).playback,"UNKNOWN")==0);
  pending.tick(320);assert(pendingWire.bytes[3]==0x0e);
  Wire cardWire;DfPlayerRuntime card(send,&cardWire);
  card.tick(100);receive(card,0x3b,2,110);
  receive(card,0x3f,2,120);
  assert(card.snapshot(120).sdPresent==0);
  // Queries remain serviced even when clients keep the mutation queue full.
  Wire floodWire; DfPlayerRuntime flood(send,&floodWire);
  int queryCount=0;
  for(uint32_t now=100;now<=2000;now+=100) {
    flood.command("volume",5,now);flood.tick(now);
    if(floodWire.bytes[3]>=0x3f) ++queryCount;
  }
  assert(queryCount>0);
  // A temporarily full UART TX buffer must not discard a queued volume command.
  Wire retryWire; DfPlayerRuntime retry(send,&retryWire);
  assert(retry.command("volume",17,100));
  retryWire.accept=false;
  retry.tick(100);
  const int blockedWrites=retryWire.count;
  retryWire.accept=true;
  retry.tick(200);
  assert(retryWire.count==blockedWrites+1);
  assert(retryWire.bytes[3]==0x06 && retryWire.bytes[6]==17);
  // The command must have been consumed exactly once, then polling resumes.
  retry.tick(300);
  assert(retryWire.bytes[3]==0x3f);
  // A failed STATUS poll must retry the SAME opcode rather than silently
  // advancing to the next register and leaving SD presence unknown.
  Wire pollWire; DfPlayerRuntime pollRetry(send,&pollWire);
  pollWire.accept=false;
  pollRetry.tick(100);
  assert(pollWire.bytes[3]==0x3f);
  pollWire.accept=true;
  pollRetry.tick(200);
  assert(pollWire.bytes[3]==0x3f);
  receive(pollRetry,0x3f,2,210);
  assert(pollRetry.snapshot(210).modulePresent);
  assert(pollRetry.snapshot(210).sdPresent==1);
  pollRetry.tick(310);
  assert(pollWire.bytes[3]==0x42);
  // Some DFPlayer clones report zero for the total-file query while
  // /MP3/0010.mp3 is playable. Never gate a numbered file on this count.
  Wire emptyWire; DfPlayerRuntime empty(send,&emptyWire);
  empty.tick(100); receive(empty,0x3f,2,110);
  empty.tick(210); receive(empty,0x42,0,220);
  empty.tick(320); receive(empty,0x48,0,330);
  assert(empty.snapshot(330).sdPresent==1);
  assert(empty.snapshot(330).tracks==0);
  assert(empty.command("play",10,340));
  assert(empty.command("play",2999,340));
  assert(!empty.command("play",3000,340));
  empty.tick(420);
  assert(emptyWire.bytes[3]==0x12 && emptyWire.bytes[5]==0 && emptyWire.bytes[6]==10);
  assert(empty.command("volume",10,430));
  // Diagnostics expose why a previously functional module appears absent.
  assert(runtime.diagnostics().rxBytes > 0);
  assert(runtime.diagnostics().validFrames > 0);
  assert(runtime.diagnostics().checksumErrors >= 1);
  assert(retry.diagnostics().txRejected >= 1);
  assert(retry.diagnostics().txFrames >= 2);
  assert(idle.diagnostics().expiredReplies >= 1);
  puts("DFPlayer checksum, hardware truth, queue, expiry: ALL OK");
}
