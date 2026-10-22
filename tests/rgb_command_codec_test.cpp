#include <cassert>
#include <cstring>
#include <iostream>
#include "../firmware/esp32_rgb/core/RgbCommandCodec.h"

using namespace esp32core;
int main() {
    RgbCommand command{};
    assert(parseRgbCommand("GET", command) && command.action == RgbAction::Get);
    assert(parseRgbCommand("ON", command) && command.action == RgbAction::On);
    assert(parseRgbCommand("OFF", command) && command.action == RgbAction::Off);
    assert(parseRgbCommand("FX,STOP", command) && command.action == RgbAction::StopEffect);
    assert(parseRgbCommand("SET,0,128,255,200", command));
    assert(command.action == RgbAction::Set && command.red == 0 && command.green == 128 &&
           command.blue == 255 && command.brightness == 200);
    assert(parseRgbCommand("FX,RAINBOW,120", command));
    assert(command.action == RgbAction::StartEffect && command.effect == RgbEffect::Rainbow && command.speed == 120);
    assert(parseRgbCommand("FX,BREATHE,10", command) && command.effect == RgbEffect::Breathe);
    assert(parseRgbCommand("FX,FADE,1000", command) && command.effect == RgbEffect::Fade);
    const char* invalid[] = {
        "", "get", "SET,1,2,3", "SET,256,0,0,0", "SET,-1,0,0,0",
        "SET,1,2,3,999", "SET,1,2,3,4,5", "FX,STOP,100", "FX,UNKNOWN,100",
        "FX,RAINBOW,9", "FX,FADE,1001", "FX,FADE,100foo",
        "FX,RAINBOW,100,garbage"
    };
    for (const char* frame : invalid) {
        RgbCommand prior = command;
        assert(!parseRgbCommand(frame, command));
        assert(command.action == prior.action && command.speed == prior.speed);
    }
    assert(!parseRgbCommand(nullptr, command));
    char oversized[80] = {};
    std::memset(oversized, 'X', 79);
    assert(!parseRgbCommand(oversized, command));
    std::cout << "PASS: V1 RGB BLE/Wi-Fi codec and malformed input regression\n";
}
