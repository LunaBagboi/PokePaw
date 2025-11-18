#include "Lcd.h"

#include <print>

#include "LcdData.h"
#include "../../../H8/Ssu/Ssu.h"

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

void Lcd::Transmit(Ssu* ssu)
{
    uint8_t command = ssu->transmit;
    switch (state) {
    case Waiting:
        {
            if (command <= 0xF) // low column
            {
                column &= 0xF0;
                column |= command & 0xF;
                offset = 0;
            }
            else if (command >= 0x10 && command <= 0x17) // high column
            {
                column &= 0xF;
                column |= (command & 0b111) << 4;
                offset = 0;
            }
            else if (command >= 0x40 && command <= 0x43)
            {
                state = PageOffset;
            }
            else if (command >= 0xB0 && command <= 0xBF) // page
            {
                page = command & 0xF;
            }
            else if (command == 0x81)
            {
                state = Contrast;
            }
            else if (command == 0xA9)
            {
                powerSaveMode = true;
            }
            else if (command == 0xE1)
            {
                powerSaveMode = false;
            }
            break;
        }
    case Contrast:
        {
            contrast = command;
            state = Waiting;
            break;
        }
    case PageOffset:
        {
            pageOffset = command / 8;
            state = Waiting;
            break;
        }
    }

    ssu->status |= SsuFlags::Status::TRANSMIT_EMPTY;
    ssu->status |= SsuFlags::Status::TRANSMIT_END;
}

bool Lcd::CanExecute(Ssu* ssu)
{
    return !(ssu->GetPort(Ssu::Port::PORT_1) & Ssu::PIN_1);
}

void Lcd::Tick()
{
    constexpr size_t bufferSize = WIDTH * HEIGHT;
    std::vector<uint8_t> paletteIndices(bufferSize);

    if (!powerSaveMode)
    {
        // 1. Base grayscale render pass (also fills palette index buffer for LCD mono mode)
        for (uint8_t y = 0; y < HEIGHT; y++)
        {
            const int bitOffset = y % 8;
            const int pixelPage = y / 8 + pageOffset;
            const int pixelPageOffset = pixelPage * TOTAL_COLUMNS * COLUMN_SIZE;

            for (uint8_t x = 0; x < WIDTH; x++)
            {
                const int baseIndex = COLUMN_SIZE * x + pixelPageOffset;
                const uint8_t firstByte = memory->ReadByte(baseIndex);
                const uint8_t secondByte = memory->ReadByte(baseIndex + 1);
                const uint8_t firstBit = (firstByte >> bitOffset) & 1;
                const uint8_t secondBit = (secondByte >> bitOffset) & 1;
                const uint8_t paletteIndex = (firstBit << 1) | secondBit;

                paletteIndices[y * WIDTH + x] = paletteIndex;

                const uint32_t rgb = PALETTE[paletteIndex] & 0x00FFFFFFu;
                colorBuffer[y * WIDTH + x] = 0xFF000000u | rgb;
            }
        }

        // 2. Color sprite overlay pass (only affects colorBuffer for full color renderer)
        for (const auto& command : colorDrawQueue)
        {
            const auto it = colorSprites.find(command.spriteId);
            if (it == colorSprites.end())
            {
                continue;
            }

            const SpriteData& sprite = it->second;
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

                    if (destX < WIDTH && destY < HEIGHT)
                    {
                        // For the main walker sprite, we drive the animation frame
                        // from the internal walkerFrameIndex so that the colored
                        // sprite tracks the firmware's own walker animation.
                        uint8_t effectiveFrameIndex = command.frameIndex;
                        if (isWalker)
                        {
                            effectiveFrameIndex = walkerFrameIndex;
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
                                    colorBuffer[destY * WIDTH + destX] = spritePixel;
                                }
                                else
                                {
                                    // When the color walker sprite has transparent pixels,
                                    // fall back to the lightest LCD background shade
                                    // (index 0) so the area behind the walker matches
                                    // the normal screen "paper".
                                    const uint32_t bgRgb = PALETTE[0] & 0x00FFFFFFu;
                                    colorBuffer[destY * WIDTH + destX] = 0xFF000000u | bgRgb;
                                }
                            }
                            else if (alpha > 0)
                            {
                                // For other sprites, keep the usual alpha-over behavior.
                                colorBuffer[destY * WIDTH + destX] = spritePixel;
                            }
                        }
                    }
                }
            }
        }
    }
    else
    {
        const uint32_t rgb = PALETTE[0] & 0x00FFFFFFu;
        const uint32_t argb = 0xFF000000u | rgb;
        for (size_t i = 0; i < bufferSize; ++i) {
            colorBuffer[i] = argb;
            paletteIndices[i] = 0;
        }
    }

    OnDraw(paletteIndices.data());

    ClearColorQueue();
}
