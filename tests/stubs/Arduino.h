#pragma once
#define OUTPUT 1
#define INPUT 0
#define HIGH 1
#define LOW 0
inline int testPinModes[40] = {};
inline int testPinValues[40] = {};
inline void pinMode(int pin, int mode) { testPinModes[pin] = mode; }
inline void digitalWrite(int pin, int value) { testPinValues[pin] = value; }
