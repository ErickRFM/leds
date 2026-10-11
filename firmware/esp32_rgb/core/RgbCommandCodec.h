#pragma once
#include <cstdint>
#include <cstdio>
#include <cstring>

// Protocol V1 parser shared by BLE and Wi-Fi; no Arduino, globals or GPIO access.
namespace esp32core {
enum class RgbAction : uint8_t { Get, On, Off, StopEffect, Set, StartEffect };
enum class RgbEffect : uint8_t { Rainbow, Breathe, Fade };
struct RgbCommand {
    RgbAction action = RgbAction::Get;
    uint8_t red = 0, green = 0, blue = 0, brightness = 255;
    RgbEffect effect = RgbEffect::Rainbow;
    uint16_t speed = 100;
};

inline bool parseRgbCommand(const char* line, RgbCommand& out) {
    if (!line || !line[0] || std::strlen(line) > 48) return false;
    RgbCommand value{};
    if (std::strcmp(line, "GET") == 0) value.action = RgbAction::Get;
    else if (std::strcmp(line, "ON") == 0) value.action = RgbAction::On;
    else if (std::strcmp(line, "OFF") == 0) value.action = RgbAction::Off;
    else if (std::strcmp(line, "FX,STOP") == 0) value.action = RgbAction::StopEffect;
    else if (std::strncmp(line, "SET,", 4) == 0) {
        int r = -1, g = -1, b = -1, brightness = -1;
        char trailing = 0;
        if (std::sscanf(line, "SET,%d,%d,%d,%d%c", &r, &g, &b, &brightness, &trailing) != 4 ||
            r < 0 || r > 255 || g < 0 || g > 255 ||
            b < 0 || b > 255 || brightness < 0 || brightness > 255) return false;
        value.action = RgbAction::Set;
        value.red = static_cast<uint8_t>(r);
        value.green = static_cast<uint8_t>(g);
        value.blue = static_cast<uint8_t>(b);
        value.brightness = static_cast<uint8_t>(brightness);
    } else if (std::strncmp(line, "FX,", 3) == 0) {
        char name[16] = {};
        int speed = -1;
        char trailing = 0;
        if (std::sscanf(line, "FX,%15[^,],%d%c", name, &speed, &trailing) != 2 ||
            speed < 10 || speed > 1000) return false;
        if (std::strcmp(name, "RAINBOW") == 0) value.effect = RgbEffect::Rainbow;
        else if (std::strcmp(name, "BREATHE") == 0) value.effect = RgbEffect::Breathe;
        else if (std::strcmp(name, "FADE") == 0) value.effect = RgbEffect::Fade;
        else return false;
        value.action = RgbAction::StartEffect;
        value.speed = static_cast<uint16_t>(speed);
    } else return false;
    out = value; // Do not mutate output on malformed frames.
    return true;
}
} // namespace esp32core
