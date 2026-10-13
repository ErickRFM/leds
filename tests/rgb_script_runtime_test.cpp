#include <cassert>
#include <cstdint>
#include <string>
#include <vector>
#include "../firmware/esp32_rgb/core/RgbScriptRuntime.h"

using esp32core::RgbScriptRuntime;

static bool record(const char* cmd, void* context) {
  static_cast<std::vector<std::string>*>(context)->emplace_back(cmd);
  return true;
}
static bool reject(const char*, void*) { return false; }

int main() {
  RgbScriptRuntime runner;
  std::vector<std::string> received;
  assert(RgbScriptRuntime::validate("# demo\nRGB 255 0 1\nWAIT 500\nOFF\nSTOP"));
  assert(runner.start("RGB 255 0 1\nWAIT 500\nOFF\nSTOP", 1000));
  assert(!runner.start("ON", 1000));  // only one at a time
  assert(runner.tick(1000, record, &received));
  assert(runner.active());
  assert(received.size() == 1 && received[0] == "SET,255,0,1,255");
  assert(runner.tick(1499, record, &received));
  assert(received.size() == 1);
  assert(runner.tick(1500, record, &received));
  assert(!runner.active());
  assert(received.size() == 2 && received[1] == "OFF");

  assert(runner.start("ON\nWAIT 500\nRGB 1 2 3 10", UINT32_MAX - 200));
  received.clear();
  assert(runner.tick(UINT32_MAX - 200, record, &received));
  assert(runner.tick(301, record, &received));  // millis wraparound
  assert(!runner.active());
  assert(received.size() == 2 && received[1] == "SET,1,2,3,10");

  assert(runner.start("ON\nWAIT 1000\nOFF", 0));
  assert(runner.tick(0, record, &received));
  assert(!runner.tick(20001, record, &received));  // safety expiry past 20000ms
  assert(!runner.active());

  // Regression test for 20-second max duration script completing with OFF at exactly 20000ms
  std::string maxScript = "RGB 255 0 0\n";
  for (int i = 0; i < 10; ++i) maxScript += "WAIT 2000\n";
  maxScript += "OFF";
  assert(RgbScriptRuntime::validate(maxScript.c_str()));
  assert(runner.start(maxScript.c_str(), 0));
  received.clear();
  assert(runner.tick(0, record, &received)); // RGB
  assert(runner.tick(20000, record, &received)); // finishes final WAIT 2000 and executes OFF
  assert(!runner.active());
  assert(received.back() == "OFF");

  assert(runner.start("ON", 10));
  assert(!runner.tick(10, reject, nullptr));
  assert(!runner.active());
  assert(runner.start("ON\nWAIT 100\nOFF", 10));
  runner.stop();
  assert(!runner.active());

  for (const char* invalid : {
      "", "  ", "void setup() {}", "RGB 255 0", "RGB 256 0 0",
      "RGB 1 2 3 256", "RGB 1 2 3.5", "WAIT 50\nON",
      "ON\nWAIT 2001", "ON\nWAIT -1", "ON\nBOGUS",
      "STOP\nON", "GPIO 27 HIGH", "WAIT 100"}) {
    assert(!RgbScriptRuntime::validate(invalid));
  }
  std::string tooLong(513, 'A');
  assert(!RgbScriptRuntime::validate(tooLong.c_str()));
  std::string tooMany = "ON\n";
  for (int i = 0; i < 33; ++i) tooMany += "WAIT 100\n";
  assert(!RgbScriptRuntime::validate(tooMany.c_str()));
  std::string tooMuchWait = "ON\n";
  for (int i = 0; i < 11; ++i) tooMuchWait += "WAIT 2000\n";
  assert(!RgbScriptRuntime::validate(tooMuchWait.c_str()));
  assert(runner.start("RGB 0 0 0\nWAIT 100\nRGB 255 255 255", 0));
  received.clear();
  assert(runner.tick(0, record, &received));
  assert(received[0] == "SET,0,0,0,255");
  assert(runner.tick(100, record, &received));
  assert(received[1] == "SET,255,255,255,255");
  return 0;
}
