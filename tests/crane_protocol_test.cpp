#include <cassert>
#include <cstring>
#include "../firmware/esp32_rgb/core/CraneProtocol.h"

using namespace esp32core::crane;

int main() {
  ProtocolCommand command{};
  assert(parseCraneCommand("CRN,1,GET", command));
  assert(command.requestId == 1 && command.type == CommandType::Get);

  assert(parseCraneCommand("CRN,2,MODE,REMOTE", command));
  assert(command.type == CommandType::Mode && command.mode == ControlMode::Remote);

  assert(parseCraneCommand("CRN,3,TARGET,45,135,60", command));
  assert(command.type == CommandType::Target);
  assert(command.baseDeg == 45.0f && command.armDeg == 135.0f);
  assert(command.speedDegPerSecond == 60.0f);

  assert(parseCraneCommand("CRN,4,AXIS,ARM,120,35", command));
  assert(command.type == CommandType::Axis && !command.baseAxis);
  assert(command.angleDeg == 120.0f);

  assert(parseCraneCommand("CRN,5,STOP", command));
  assert(command.type == CommandType::Stop);
  assert(parseCraneCommand("CRN,6,HOME", command));
  assert(command.type == CommandType::Home);

  const char* invalid[] = {
    "", "CRN", "CRN,0,GET", "CRN,65536,GET", "CRN,1,GET,EXTRA",
    "CRN,1,MODE,ADMIN", "CRN,1,TARGET,181,90,30",
    "CRN,1,TARGET,90,-1,30", "CRN,1,TARGET,90,90,0",
    "CRN,1,TARGET,nan,90,30", "CRN,1,AXIS,Z,90,30",
    "CRN,1,AXIS,BASE,90,181", "CRN,1,STOP,EXTRA"
  };
  for (const char* frame : invalid) {
    assert(!parseCraneCommand(frame, command));
  }

  char oversized[80];
  std::memset(oversized, 'A', sizeof(oversized));
  oversized[sizeof(oversized) - 1] = '\0';
  assert(!parseCraneCommand(oversized, command));
  return 0;
}
