#include <cassert>
#include <cstring>
#include <cstdio>
#include "../firmware/esp32_rgb/core/BleCommandInbox.h"
using namespace esp32core;
int main() {
  BleCommandInbox inbox;
  char output[RgbCommandInbox::kMaxCommand+1]{};
  assert(!inbox.push("AUD,old,PLAY,1"));
  inbox.setAuthorized(true);
  assert(inbox.push("AUD,old,PLAY,1"));
  assert(inbox.push("PIN,0123456789abcdef,D,16,33"));
  inbox.setAuthorized(false); // Disconnect/revoke must discard pending work.
  assert(!inbox.push("OLD,unauth,INVERT,1"));
  inbox.setAuthorized(true); // New owner session cannot revive old commands.
  assert(!inbox.pop(output));
  assert(inbox.push("OLD,new,GET"));
  inbox.setAuthorized(true); // Security link refresh must retain same-session work.
  assert(inbox.pop(output) && !std::strcmp(output,"OLD,new,GET"));
  assert(!inbox.pop(output));
  puts("BLE command queue authorization and session reset: ALL OK");
}
