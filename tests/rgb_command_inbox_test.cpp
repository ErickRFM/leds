#include <cassert>
#include <cstring>
#include <string>
#include "../firmware/esp32_rgb/core/RgbCommandInbox.h"
using esp32core::RgbCommandInbox;

int main() {
    RgbCommandInbox inbox;
    char output[RgbCommandInbox::kMaxCommand + 1] = {};
    assert(!inbox.pop(output));
    assert(!inbox.push(""));
    assert(!inbox.push(nullptr));
    assert(!inbox.push(std::string(65, 'x').c_str()));
    const std::string oled("OLD,1234567890,TEXT,123456789012345678901234567890123456789");
    assert(inbox.push(oled.c_str()));
    assert(inbox.pop(output) && std::strcmp(output, oled.c_str()) == 0);
    assert(inbox.push("GET"));
    assert(inbox.push("SET,255,20,0,128"));
    assert(inbox.size() == 2);
    assert(inbox.pop(output) && std::strcmp(output, "GET") == 0);
    assert(inbox.pop(output) && std::strcmp(output, "SET,255,20,0,128") == 0);
    assert(!inbox.pop(output));
    for (size_t i = 0; i < RgbCommandInbox::kCapacity; ++i) assert(inbox.push("ON"));
    assert(!inbox.push("OFF")); // never overwrite emergency ordering silently
    for (size_t i = 0; i < RgbCommandInbox::kCapacity; ++i)
        assert(inbox.pop(output) && std::strcmp(output, "ON") == 0);
    assert(inbox.size() == 0);
    assert(inbox.push("OFF"));
    inbox.clear();
    assert(inbox.size() == 0 && !inbox.pop(output));
    assert(inbox.push("GET"));
    assert(inbox.pop(output) && std::strcmp(output, "GET") == 0);
    return 0;
}
