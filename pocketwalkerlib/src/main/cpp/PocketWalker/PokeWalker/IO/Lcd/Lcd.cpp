#include "Lcd.h"

#include <print>
#include <cstring>

#include "LcdData.h"
#include "../../../H8/Ssu/Ssu.h"

// Backend interface and color implementation

class LcdBackend
{
public:
    virtual ~LcdBackend() = default;
    virtual void Transmit(Lcd* lcd, Ssu* ssu) = 0;
    virtual bool CanExecute(Lcd* lcd, Ssu* ssu) = 0;
    virtual void Tick(Lcd* lcd) = 0;
};

class LcdColorBackend : public LcdBackend
{
public:
    void Transmit(Lcd* lcd, Ssu* ssu) override;
    bool CanExecute(Lcd* lcd, Ssu* ssu) override;
    void Tick(Lcd* lcd) override;
};

// Mono backend: emulates the original grayscale LCD behavior without
// any color sprite overlay. Derived from the legacy LcdMono.cpp
// implementation.
class LcdMonoBackend : public LcdBackend
{
public:
    void Transmit(Lcd* lcd, Ssu* ssu) override;
    bool CanExecute(Lcd* lcd, Ssu* ssu) override;
    void Tick(Lcd* lcd) override;
};

Lcd::Lcd(bool useMono)
{
    memory = new Memory(0x3200);
    useMonoBackend = useMono;

    if (useMonoBackend)
    {
        backend = std::make_unique<LcdMonoBackend>();
    }
    else
    {
        backend = std::make_unique<LcdColorBackend>();
    }
}

void Lcd::SetTestSprite(const uint32_t* pixels, const size_t count, const uint8_t width, const uint8_t height)
{
    // Backwards-compatible helper: treat the legacy test sprite as the
    // "walker" color sprite so existing Kotlin code keeps working.
    SetColorSprite("walker", pixels, count, width, height);
}

void Lcd::SetColorSprite(const std::string& id,
                         const uint32_t* pixels,
                         const size_t count,
                         const uint8_t width,
                         const uint8_t height)
{
    SpriteData data;
    data.pixels.assign(pixels, pixels + count);
    data.width = width;
    data.height = height;
    colorSprites[id] = std::move(data);
}

void Lcd::NotifyWalkerDrawn(const uint32_t walkerHash)
{
    // First-time initialization: accept whatever the firmware drew as the
    // baseline frame and do not toggle.
    if (!hasWalkerHash)
    {
        lastWalkerHash = walkerHash;
        hasWalkerHash = true;
    }
    else if (walkerHash != lastWalkerHash)
    {
        // The underlying grayscale walker sprite changed since the last
        // frame. Flip the color frame index so we track the firmware's own
        // 2-frame animation regardless of species or timing.
        lastWalkerHash = walkerHash;
        walkerFrameIndex ^= 1;
    }

    walkerDrawn = true;
}

void Lcd::QueueColorDraw(const ColorDrawCommand& command)
{
    colorDrawQueue.push_back(command);
}

void Lcd::ClearColorQueue()
{
    colorDrawQueue.clear();
}

// LcdColorBackend implementation

void LcdColorBackend::Transmit(Lcd* lcd, Ssu* ssu)
{
    uint8_t command = ssu->transmit;
    switch (lcd->state) {
    case Lcd::Waiting:
        {
            if (command <= 0xF) // low column
            {
                lcd->column &= 0xF0;
                lcd->column |= command & 0xF;
                lcd->offset = 0;
            }
            else if (command >= 0x10 && command <= 0x17) // high column
            {
                lcd->column &= 0xF;
                lcd->column |= (command & 0b111) << 4;
                lcd->offset = 0;
            }
            else if (command >= 0x40 && command <= 0x43)
            {
                lcd->state = Lcd::PageOffset;
            }
            else if (command >= 0xB0 && command <= 0xBF) // page
            {
                lcd->page = command & 0xF;
            }
            else if (command == 0x81)
            {
                lcd->state = Lcd::Contrast;
            }
            else if (command == 0xA9)
            {
                lcd->powerSaveMode = true;
            }
            else if (command == 0xE1)
            {
                lcd->powerSaveMode = false;
            }
            break;
        }
    case Lcd::Contrast:
        {
            lcd->contrast = command;
            lcd->state = Lcd::Waiting;
            break;
        }
    case Lcd::PageOffset:
        {
            lcd->pageOffset = command / 8;
            lcd->state = Lcd::Waiting;
            break;
        }
    }

    ssu->status |= SsuFlags::Status::TRANSMIT_EMPTY;
    ssu->status |= SsuFlags::Status::TRANSMIT_END;
}

bool LcdColorBackend::CanExecute(Lcd* /*lcd*/, Ssu* ssu)
{
    return !(ssu->GetPort(Ssu::Port::PORT_1) & Ssu::PIN_1);
}

void LcdColorBackend::Tick(Lcd* lcd)
{
    constexpr size_t bufferSize = Lcd::WIDTH * Lcd::HEIGHT;
    std::vector<uint8_t> paletteIndices(bufferSize);

    if (!lcd->powerSaveMode)
    {
        // 1. Base grayscale render pass (also fills palette index buffer for LCD mono mode)
        for (uint8_t y = 0; y < Lcd::HEIGHT; y++)
        {
            const int bitOffset = y % 8;
            const int pixelPage = y / 8 + lcd->pageOffset;
            const int pixelPageOffset = pixelPage * Lcd::TOTAL_COLUMNS * Lcd::COLUMN_SIZE;

            for (uint8_t x = 0; x < Lcd::WIDTH; x++)
            {
                const int baseIndex = Lcd::COLUMN_SIZE * x + pixelPageOffset;
                const uint8_t firstByte = lcd->memory->ReadByte(baseIndex);
                const uint8_t secondByte = lcd->memory->ReadByte(baseIndex + 1);
                const uint8_t firstBit = (firstByte >> bitOffset) & 1;
                const uint8_t secondBit = (secondByte >> bitOffset) & 1;
                const uint8_t paletteIndex = (firstBit << 1) | secondBit;

                paletteIndices[y * Lcd::WIDTH + x] = paletteIndex;

                const uint32_t rgb = Lcd::PALETTE[paletteIndex] & 0x00FFFFFFu;
                lcd->colorBuffer[y * Lcd::WIDTH + x] = 0xFF000000u | rgb;
            }
        }

        // 2. Color sprite overlay pass (only affects colorBuffer for full color renderer)
        for (const auto& command : lcd->colorDrawQueue)
        {
            const auto it = lcd->colorSprites.find(command.spriteId);
            if (it == lcd->colorSprites.end())
            {
                continue;
            }

            const Lcd::SpriteData& sprite = it->second;
            if (sprite.pixels.empty() || sprite.width == 0 || sprite.height == 0)
            {
                continue;
            }

            // For walker sprites we assume a 2-frame vertical strip; for other
            // sprites treat the whole bitmap as a single frame.
            const bool isWalker = (command.spriteId == "walker");
            const uint8_t frameCount = isWalker ? 2 : 1;
            const uint8_t frameHeight = static_cast<uint8_t>(sprite.height / frameCount);

            for (uint8_t sy = 0; sy < command.height; ++sy)
            {
                for (uint8_t sx = 0; sx < command.width; ++sx)
                {
                    const int destX = command.x + sx;
                    const int destY = command.y + sy;

                    if (destX < Lcd::WIDTH && destY < Lcd::HEIGHT)
                    {
                        // For the main walker sprite, we drive the animation frame
                        // from the internal walkerFrameIndex so that the colored
                        // sprite tracks the firmware's own walker animation.
                        uint8_t effectiveFrameIndex = command.frameIndex;
                        if (isWalker)
                        {
                            effectiveFrameIndex = lcd->walkerFrameIndex;
                        }

                        const size_t spriteRow = static_cast<size_t>(effectiveFrameIndex) * frameHeight + static_cast<size_t>(sy);
                        const size_t spriteIndex = spriteRow * static_cast<size_t>(sprite.width) + static_cast<size_t>(sx);

                        if (spriteIndex < sprite.pixels.size())
                        {
                            const uint32_t spritePixel = sprite.pixels[spriteIndex];
                            const uint8_t alpha = (spritePixel >> 24) & 0xFF;

                            if (isWalker)
                            {
                                // For the walker, the color sprite should fully replace
                                // the grayscale walker underneath. Treat transparent
                                // pixels as background instead of letting grayscale
                                // show through.
                                if (alpha > 0)
                                {
                                    lcd->colorBuffer[destY * Lcd::WIDTH + destX] = spritePixel;
                                }
                                else
                                {
                                    // When the color walker sprite has transparent pixels,
                                    // fall back to the lightest LCD background shade
                                    // (index 0) so the area behind the walker matches
                                    // the normal screen "paper".
                                    const uint32_t bgRgb = Lcd::PALETTE[0] & 0x00FFFFFFu;
                                    lcd->colorBuffer[destY * Lcd::WIDTH + destX] = 0xFF000000u | bgRgb;
                                }
                            }
                            else if (alpha > 0)
                            {
                                // For other sprites, keep the usual alpha-over behavior.
                                lcd->colorBuffer[destY * Lcd::WIDTH + destX] = spritePixel;
                            }
                        }
                    }
                }
            }
        }
    }
    else
    {
        const uint32_t rgb = Lcd::PALETTE[0] & 0x00FFFFFFu;
        const uint32_t argb = 0xFF000000u | rgb;
        for (size_t i = 0; i < bufferSize; ++i) {
            lcd->colorBuffer[i] = argb;
            paletteIndices[i] = 0;
        }
    }

    lcd->OnDraw(paletteIndices.data());

    lcd->ClearColorQueue();
}

// LcdMonoBackend implementation (legacy mono behavior)

void LcdMonoBackend::Transmit(Lcd* lcd, Ssu* ssu)
{
    uint8_t command = ssu->transmit;
    switch (lcd->state) {
    case Lcd::Waiting:
        {
            if (command <= 0xF) // low column
            {
                lcd->column &= 0xF0;
                lcd->column |= command & 0xF;
                lcd->offset = 0;
            }
            else if (command >= 0x10 && command <= 0x17) // high column
            {
                lcd->column &= 0xF;
                lcd->column |= (command & 0b111) << 4;
                lcd->offset = 0;
            }
            else if (command >= 0x40 && command <= 0x43)
            {
                lcd->state = Lcd::PageOffset;
            }
            else if (command >= 0xB0 && command <= 0xBF) // page
            {
                lcd->page = command & 0xF;
            }
            else if (command == 0x81)
            {
                lcd->state = Lcd::Contrast;
            }
            else if (command == 0xA9)
            {
                lcd->powerSaveMode = true;
            }
            else if (command == 0xE1)
            {
                lcd->powerSaveMode = false;
            }
            break;
        }
    case Lcd::Contrast:
        {
            lcd->contrast = command;
            lcd->state = Lcd::Waiting;
            break;
        }
    case Lcd::PageOffset:
        {
            lcd->pageOffset = command / 8;
            lcd->state = Lcd::Waiting;
            break;
        }
    }

    ssu->status |= SsuFlags::Status::TRANSMIT_EMPTY;
    ssu->status |= SsuFlags::Status::TRANSMIT_END;
}

bool LcdMonoBackend::CanExecute(Lcd* /*lcd*/, Ssu* ssu)
{
    // TODO create larger component for handling multiple pins
    return !(ssu->GetPort(Ssu::Port::PORT_1) & Ssu::PIN_1);
}

void LcdMonoBackend::Tick(Lcd* lcd)
{
    constexpr size_t bufferSize = Lcd::WIDTH * Lcd::HEIGHT;
    const auto buffer = new uint8_t[bufferSize]();

    if (!lcd->powerSaveMode)
    {
        for (uint8_t y = 0; y < Lcd::HEIGHT; y++)
        {
            const int bitOffset = y % 8;
            const int pixelPage = y / 8 + lcd->pageOffset;
            const int pixelPageOffset = pixelPage * Lcd::TOTAL_COLUMNS * Lcd::COLUMN_SIZE;

            for (uint8_t x = 0; x < Lcd::WIDTH; x++)
            {
                const int baseIndex = Lcd::COLUMN_SIZE * x + pixelPageOffset;

                const uint8_t firstByte = lcd->memory->ReadByte(baseIndex);
                const uint8_t secondByte = lcd->memory->ReadByte(baseIndex + 1);

                const uint8_t firstBit = (firstByte >> bitOffset) & 1;
                const uint8_t secondBit = (secondByte >> bitOffset) & 1;

                const uint8_t paletteIndex = (firstBit << 1) | secondBit;

                const int bufferIndex = y * Lcd::WIDTH + x;
                buffer[bufferIndex] = paletteIndex;
            }
        }
    }
    else
    {
        std::memset(buffer, 0, bufferSize); // Fill with palette index 0 for power save mode
    }

    lcd->OnDraw(buffer);
}

// Lcd public API delegates

void Lcd::Transmit(Ssu* ssu)
{
    backend->Transmit(this, ssu);
}

bool Lcd::CanExecute(Ssu* ssu)
{
    return backend->CanExecute(this, ssu);
}

void Lcd::Tick()
{
    backend->Tick(this);
}
