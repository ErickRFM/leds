#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>
namespace esp32core {
struct AudioSnapshot {
  bool modulePresent=false; int sdPresent=-1; int tracks=-1;
  const char* playback="UNKNOWN"; int track=-1; int volume=-1; bool fresh=false;
  bool voiceEnabled=false;
};
// Single UART scheduler. All state originates in checksum-validated module
// replies; transport acceptance never substitutes for playback confirmation.
// DFRobot's reference library uses SD count 0x48 and SD index 0x4c.
// PLAY uses /mp3/NNNN.mp3 (0x12): SD index/count do not enumerate filenames.
// Query responses are accepted only for the current unexpired query. Commands
// and asynchronous card events cancel that expectation. DFPlayer has no wire
// transaction nonce: a response delayed into a later query of the SAME opcode
// cannot be distinguished from its new reply. Polls are serialized and bounded
// to 500 ms, but eliminating that device-protocol ambiguity requires hardware
// BUSY/card evidence or a module protocol with transaction identifiers.
class DfPlayerRuntime final {
 public:
  using Send=bool (*)(const uint8_t*,size_t,void*);
  DfPlayerRuntime(Send send,void* context):send_(send),context_(context) {}
  AudioSnapshot snapshot(uint32_t now) const {
    AudioSnapshot out{}; out.modulePresent=seen_ && age(now,lastReply_)<=5000;
    if(!out.modulePresent) return out;
    out.sdPresent=valid(now,0)?sd_:-1;
    out.playback=valid(now,1)?playback_:"UNKNOWN";
    out.tracks=valid(now,2)?tracks_:-1;
    out.track=valid(now,3)?track_:-1;
    out.volume=valid(now,4)?volume_:-1;
    out.fresh=valid(now,0) && valid(now,1) && valid(now,2) && valid(now,3) && valid(now,4);
    return out;
  }
  bool command(const char* verb,int parameter,uint32_t now) {
    if(!verb || count_==4) return false;
    uint8_t command=0;
    if(!std::strcmp(verb,"play") && parameter>=1 && parameter<=2999 && snapshot(now).sdPresent==1 && snapshot(now).tracks!=0) command=0x12;
    else if(!std::strcmp(verb,"volume") && parameter>=0 && parameter<=30) command=0x06;
    else if(!std::strcmp(verb,"stop") && parameter==0) command=0x16;
    else if(!std::strcmp(verb,"pause") && parameter==0) command=0x0e;
    else if(!std::strcmp(verb,"resume") && parameter==0) command=0x0d;
    if(!command) return false;
    waiting_=0; // An earlier poll cannot confirm the new user intent.
    queue_[(head_+count_)%4]={command,static_cast<uint16_t>(parameter)}; ++count_;
    if(command==0x06) known_[4]=false;
    else { known_[1]=false; known_[3]=false; }
    return true;
  }
  void tick(uint32_t now) {
    if(age(now,lastSent_)<100) return;
    if(waiting_ && age(now,lastSent_)<500) return;
    waiting_=0;
    if(count_ && commandsSincePoll_<2) {
      const Pending pending=queue_[head_]; head_=(head_+1)%4; --count_;
      if(pending.command==0x06) known_[4]=false;
      else known_[1]=known_[3]=false;
      ++commandsSincePoll_;
      transmit(pending.command,pending.parameter,now); return;
    }
    if(poll_==0 && age(now,lastCycle_)<1000 && cycleStarted_ && commandsSincePoll_<2) return;
    if(poll_==0) {lastCycle_=now; cycleStarted_=true;}
    const uint8_t queries[]={0x3f,0x42,0x48,0x4c,0x43};
    const uint8_t query=queries[poll_]; poll_=(poll_+1)%5;
    commandsSincePoll_=0;
    if(transmit(query,0,now)) waiting_=query;
  }
  void consume(uint8_t byte,uint32_t now) {
    if(index_ && age(now,lastByte_)>100) index_=0;
    lastByte_=now;
    if(!index_) {if(byte==0x7e) frame_[index_++]=byte; return;}
    frame_[index_++]=byte;
    if((index_==2 && byte!=0xff) || (index_==3 && byte!=6)) {index_=byte==0x7e?1:0; frame_[0]=0x7e; return;}
    if(index_!=10) return;
    index_=0;
    unsigned sum=0; for(size_t i=1;i<7;++i) sum+=frame_[i];
    if(frame_[9]!=0xef || static_cast<uint16_t>(0u-sum)!=static_cast<uint16_t>((frame_[7]<<8)|frame_[8])) return;
    const uint8_t command=frame_[3]; const uint16_t param=(frame_[5]<<8)|frame_[6];
    if(command!=0x3f && command!=0x3a && command!=0x3b && command!=0x3d && command!=0x40 && command!=0x41 && command!=0x42 && command!=0x43 && command!=0x48 && command!=0x4c) return;
    const bool query=command==0x3f || command==0x42 || command==0x43 || command==0x48 || command==0x4c;
    if(query && (waiting_!=command || age(now,lastSent_)>=500)) return;
    seen_=true; lastReply_=now; if(waiting_==command) waiting_=0;
    switch(command) {
      case 0x3f: sd_=(param&2)?1:0; mark(0,now); if(!sd_) clearSd(); break;
      case 0x3a: if(param&2) {clearSd();sd_=1;mark(0,now);} break;
      case 0x3b: if(param&2) {sd_=0;mark(0,now);clearSd();} break;
      case 0x42: {
        const unsigned state=param&0xff;
        playback_=state==0?"STOPPED":state==1?"PLAYING":state==2?"PAUSED":"UNKNOWN";
        mark(1,now); break;
      }
      case 0x43: if(param<=30) {volume_=param;mark(4,now);} break;
      case 0x48: tracks_=param; mark(2,now); break;
      case 0x4c: track_=param; mark(3,now); break;
      case 0x3d:
        waiting_=0;
        if(valid(now,0) && sd_==1) {playback_="STOPPED";track_=param;mark(1,now);mark(3,now);}
        break;
      case 0x40: waiting_=0;known_[1]=known_[3]=false; break;
      default: break; // ACK confirms only UART receipt, never playback.
    }
  }
 private:
  struct Pending {uint8_t command;uint16_t parameter;};
  static uint32_t age(uint32_t now,uint32_t then) {return now-then;}
  bool valid(uint32_t now,size_t field) const {return known_[field] && age(now,updated_[field])<=5000;}
  void mark(size_t field,uint32_t now) {known_[field]=true;updated_[field]=now;}
  void clearSd() {waiting_=0;tracks_=track_=-1;known_[1]=known_[2]=known_[3]=false;}
  bool transmit(uint8_t command,uint16_t param,uint32_t now) {
    uint8_t frame[]={0x7e,0xff,6,command,0,static_cast<uint8_t>(param>>8),static_cast<uint8_t>(param),0,0,0xef};
    const uint16_t sum=static_cast<uint16_t>(0u-(0xffu+6u+command+frame[5]+frame[6]));frame[7]=sum>>8;frame[8]=sum;
    lastSent_=now; return send_ && send_(frame,sizeof(frame),context_);
  }
  Send send_;void* context_;
  Pending queue_[4]{};size_t head_=0,count_=0;
  uint8_t frame_[10]{};size_t index_=0;uint32_t lastByte_=0;
  uint8_t waiting_=0,poll_=0,commandsSincePoll_=0;uint32_t lastSent_=0,lastCycle_=0,lastReply_=0;
  bool seen_=false,cycleStarted_=false,known_[5]{};uint32_t updated_[5]{};
  int sd_=-1,tracks_=-1,track_=-1,volume_=-1;const char* playback_="UNKNOWN";
};
} // namespace esp32core
