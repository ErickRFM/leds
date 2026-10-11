#include <cassert>
#include <iostream>
#include "../firmware/esp32_rgb/core/ExperimentRuntime.h"

using namespace esp32core;

int main() {
  PinManager pins;
  ExperimentRuntime runtime;
  const ExperimentRuntime::Step steps[] = {{true, 300}, {false, 300}, {true, 300}};
  assert(!runtime.startSequence(pins, 18, steps, 0, 1000));
  assert(!runtime.startSequence(pins, 6, steps, 3, 1000));
  assert(runtime.startSequence(pins, 18, steps, 3, 1000));
  assert(runtime.active());
  assert(pins.belongsTo(18, "lab.experiment"));
  assert(testPinValues[18] == HIGH);
  assert(!runtime.startSequence(pins, 19, steps, 3, 1000));
  runtime.tick(pins, 1299);
  assert(testPinValues[18] == HIGH);
  runtime.tick(pins, 1300);
  assert(testPinValues[18] == LOW);
  runtime.tick(pins, 1600);
  assert(testPinValues[18] == HIGH);
  runtime.tick(pins, 1900);
  assert(!runtime.active());
  assert(testPinValues[18] == LOW);
  assert(testPinModes[18] == INPUT);
  assert(pins.leasedCount() == 0);

  const ExperimentRuntime::Step tooLong[] = {{true, 2001}};
  assert(!runtime.startSequence(pins, 18, tooLong, 1, 2000));
  const ExperimentRuntime::Step tooShort[] = {{true, 99}};
  assert(!runtime.startSequence(pins, 18, tooShort, 1, 2000));
  const ExperimentRuntime::Step longProgram[16] = {
      {true,1500},{false,1500},{true,1500},{false,1500},
      {true,1500},{false,1500},{true,1500},{false,1500},
      {true,1500},{false,1500},{true,1500},{false,1500},
      {true,1500},{false,1500},{true,1500},{false,1500}};
  assert(!runtime.startSequence(pins, 18, longProgram, 16, 100));
  assert(!runtime.start(pins, 18, 2000, 20, 100));
  assert(runtime.start(pins, 18, 500, 5, 1000));
  runtime.stop(pins);
  assert(!runtime.active() && testPinValues[18] == LOW);
  assert(pins.leasedCount() == 0);
  assert(pins.acquire(18, "other", PinFunction::DigitalOutput));
  assert(!runtime.startSequence(pins, 18, steps, 3, 3000));
  assert(pins.release(18, "other"));

  // A blocked WebServer must NOT stretch intervals or replay missed HIGH pulses.
  assert(runtime.startSequence(pins, 18, steps, 3, 4000));
  runtime.tick(pins, 4750); // Missed first two boundaries: third step is HIGH.
  assert(runtime.active() && runtime.currentStep() == 2);
  assert(testPinValues[18] == HIGH);
  runtime.tick(pins, 4900); // Sum of durations reached, stop and release.
  assert(!runtime.active() && testPinValues[18] == LOW);
  assert(testPinModes[18] == INPUT && pins.leasedCount() == 0);

  // If the loop resumes after the entire program, it should stop immediately.
  assert(runtime.startSequence(pins, 18, steps, 3, 5000));
  runtime.tick(pins, 8500);
  assert(!runtime.active() && pins.leasedCount() == 0 && testPinValues[18] == LOW);

  // millis() wraparound must preserve deadlines without claiming negative durations.
  const uint32_t nearWrap = 0xFFFFFF00u;
  assert(runtime.startSequence(pins, 18, steps, 3, nearWrap));
  runtime.tick(pins, static_cast<uint32_t>(nearWrap + 600u));
  assert(runtime.active() && runtime.currentStep() == 2 && testPinValues[18] == HIGH);
  runtime.tick(pins, static_cast<uint32_t>(nearWrap + 900u));
  assert(!runtime.active() && pins.leasedCount() == 0 && testPinValues[18] == LOW);

  std::cout << "PASS: bounded declarative experiments, timing catch-up, ownership, rollback and timeout safety\n";
}
