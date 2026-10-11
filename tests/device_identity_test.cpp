#include <cassert>
#include <cstring>
#include <iostream>
#include "../firmware/esp32_rgb/core/DeviceIdentityCodec.h"

int main() {
  using esp32core::validDeviceId;
  using esp32core::formatDeviceId;
  char id[17] = {};
  assert(formatDeviceId(0x1234ABCD, 0xDEADBEEF, id, sizeof(id)));
  assert(std::strcmp(id, "1234ABCDDEADBEEF") == 0);
  assert(validDeviceId(id));
  assert(!validDeviceId("1234ABCDDEADBEEx"));
  assert(!validDeviceId("1234ABCDDEADBEE"));
  assert(!validDeviceId("1234ABCDDEADBEEF0"));
  assert(!validDeviceId(""));
  assert(!validDeviceId(nullptr));
  assert(!formatDeviceId(0, 0, id, sizeof(id)));
  assert(!formatDeviceId(1, 2, id, 16));
  assert(!formatDeviceId(1, 2, nullptr, 17));
  assert(formatDeviceId(1, 2, id, sizeof(id)));
  assert(std::strcmp(id, "0000000100000002") == 0);
  std::cout << "PASS: stable public ID format, invalid input and buffer bounds\n";
}
