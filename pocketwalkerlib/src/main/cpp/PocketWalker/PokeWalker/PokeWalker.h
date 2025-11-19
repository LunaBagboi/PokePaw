#pragma once

#include "../H8/H8300H.h"
#include "IO/Lcd/Lcd.h"
#include "IO/Eeprom/Eeprom.h"
#include "IO/Accelerometer/Accelerometer.h"
#include "IO/Beeper/Beeper.h"
#include "IO/Buttons/Buttons.h"
#include "IO/Lcd/LcdData.h"

class PokeWalker : public H8300H {
public:
    PokeWalker(uint8_t* ramBuffer, uint8_t* eepromBuffer);

    void Tick(uint64_t cycles);
    
    void OnDraw(const EventHandlerCallback<uint8_t*>& handler) const;
    void OnFirmwareDraw(EventHandlerCallback<Lcd::FirmwareDrawEventArgs> handler) const;
    void OnAudio(const EventHandlerCallback<AudioInformation>& handler) const;
    void OnTransmitSci3(const EventHandlerCallback<uint8_t>& callback) const;
    void ReceiveSci3(uint8_t byte) const;

    void PressButton(Buttons::Button button) const;
    void ReleaseButton(Buttons::Button button) const;
    
    void SetEepromBuffer(uint8_t* buffer) const;
    uint8_t* GetEepromBuffer() const;

    uint8_t GetContrast() const;

    const std::array<uint32_t, Lcd::WIDTH * Lcd::HEIGHT>& GetColorBuffer() const;

    void SetTestSprite(const uint32_t* pixels, size_t count, uint8_t width, uint8_t height) const;

    void SetColorSprite(const std::string& id,
                        const uint32_t* pixels,
                        size_t count,
                        uint8_t width,
                        uint8_t height) const;

    void SetTestSpriteOffset(int8_t xOffset, int8_t yOffset) const;

    void SetTestSpriteFrameOverride(int8_t frame) const;

    void SetTestSpriteAnimationModeOverride(int8_t mode) const;

    struct WalkerVariantInfo
    {
        uint16_t species;
        uint8_t variant;
        bool isFemale;
        bool isShiny;
        bool hasForm;
    };

    uint16_t GetWalkerDexNumber() const;

    WalkerVariantInfo GetWalkerVariantInfo() const;

    uint16_t GetCurrentRouteId() const;

    bool IsSpecialRoute() const;

    uint16_t GetCurrentWatts() const;

    void AdjustWatts(int16_t delta) const;

    // Accumulate fused steps from the Android fusion pipeline. These
    // will be consumed by the handleAccelSteps hook inside the CPU
    // address handler.
    void AddFusedSteps(uint16_t count) const;

    // Inject a normalized acceleration sample into the emulated
    // accelerometer so the firmware's accel pipeline sees live data.
    void SetAccelerationData(float x, float y, float z) const;

    // Read a small window from the emulated accelerometer's internal
    // memory for debugging (e.g. to verify what the firmware sees).
    void ReadAccelerometerWindow(uint8_t start, uint8_t length, uint8_t* out) const;

private:
    void SetupAddressHandlers() const;

    // Pending fused steps that have been accepted on the Android
    // side but not yet consumed by the firmware's step pipeline.
    mutable uint32_t fusedStepBudget = 0;

    Lcd* lcd;
    LcdData* lcdData;
    Eeprom* eeprom;
    Accelerometer* accelerometer;
    Beeper* beeper;
    Buttons* buttons;
};
