#pragma once
#include <array>
#include <vector>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <memory>

#include "../../../H8/IO/IOComponent.h"
#include "../../../H8/Memory/Memory.h"
#include "../../../Utilities/EventHandler.h"

class Memory;

class LcdBackend; // internal implementation detail

class Lcd : public IOComponent
{
public:
    struct UiState
    {
        uint8_t currentlyActiveView = 0;
        uint8_t curSubstateY = 0;
        uint8_t curSubstateZ = 0;
        uint8_t curSubstateA = 0;
        uint8_t curUiFlags = 0;
    };

    struct ColorDrawCommand
    {
        std::string spriteId;
        uint8_t x, y, width, height;
        uint8_t frameIndex; // The specific animation frame to draw
    };

    struct FirmwareDrawEventArgs
    {
        uint8_t x, y, width, height;
        uint8_t* pixelPtr;
    };

    EventHandler<FirmwareDrawEventArgs> OnFirmwareDraw;

    explicit Lcd(bool useMonoBackend = false);
    
    void Transmit(Ssu* ssu) override;
    void Tick() override;
    bool CanExecute(Ssu* ssu) override;

    enum LcdState : uint8_t
    {
        Waiting,
        Contrast,
        PageOffset
    };
    
    Memory* memory;
    LcdState state;
    EventHandler<uint8_t*> OnDraw;

    size_t column = 0;
    size_t offset = 0;
    size_t page = 0;
    uint8_t contrast = 20;
    uint8_t pageOffset;
    bool powerSaveMode;

    static constexpr uint8_t WIDTH = 96;
    static constexpr uint8_t HEIGHT = 64;
    static constexpr uint8_t COLUMN_SIZE = 2;
    static constexpr uint8_t TOTAL_COLUMNS = 0xFF;
    // Base grayscale palette for the color renderer (index 0 = lightest,
    // index 3 = darkest) to match the original LCD brightness ordering.
    // These values are treated as 0xRRGGBB and combined with an opaque alpha
    // channel when writing into the ARGB color buffer.
    static constexpr std::array<uint32_t, 4> PALETTE = {
        0xb7b8b0,  // index 0: lightest (#b7b8b0)
        0x808173,  // index 1: (#808173)
        0x666559,  // index 2: (#666559)
        0x1f1a17   // index 3: darkest (#1f1a17)
    };
    static constexpr size_t TICKS = 4;

    const std::array<uint32_t, WIDTH * HEIGHT>& GetColorBuffer() const { return colorBuffer; }

    void SetTestSprite(const uint32_t* pixels, size_t count, uint8_t width, uint8_t height);
    void SetColorSprite(const std::string& id, const uint32_t* pixels, size_t count, uint8_t width, uint8_t height);
    void SetUiState(const UiState& state) { uiState = state; }

    void QueueColorDraw(const ColorDrawCommand& command);
    void ClearColorQueue();
    void NotifyWalkerDrawn(uint32_t walkerHash);

private:
    std::array<uint32_t, WIDTH * HEIGHT> colorBuffer{};
    struct SpriteData
    {
        std::vector<uint32_t> pixels;
        uint8_t width = 0;
        uint8_t height = 0;
    };
    std::unordered_map<std::string, SpriteData> colorSprites;
    UiState uiState{};
    std::vector<ColorDrawCommand> colorDrawQueue;
    bool walkerDrawn = false;
    uint8_t walkerFrameIndex = 0;
    uint32_t lastWalkerHash = 0;
    bool hasWalkerHash = false;

    // Which backend implementation is currently active (mono or color).
    bool useMonoBackend = false;

    // Backend strategy for the actual LCD logic (color, mono, etc.).
    std::unique_ptr<LcdBackend> backend;

    // Allow backend implementations to manipulate internal state directly.
    friend class LcdBackend;
    friend class LcdColorBackend;
    friend class LcdMonoBackend;
};
