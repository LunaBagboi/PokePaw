 #include "PokeWalker.h"
 #include "../H8/Ssu/Ssu.h"
 #include "../../SleepConfig.h"
 
 #ifdef __ANDROID__
 #include <android/log.h>
 #endif

 // FNV-1a 32-bit hash function for detecting walker frame changes
 static uint32_t fnv1a_32(const uint8_t* data, size_t size)
 {
     uint32_t hash = 0x811c9dc5u;
     for (size_t i = 0; i < size; ++i)
     {
         hash ^= data[i];
         hash *= 0x01000193u;
     }
     return hash;
 }

PokeWalker::PokeWalker(uint8_t* ramBuffer, uint8_t* eepromBuffer) : H8300H(ramBuffer)
{
    SetupAddressHandlers();

    eeprom = new Eeprom(eepromBuffer);
    RegisterIOComponent(eeprom, Ssu::PORT_1, Ssu::PIN_2);

    accelerometer = new Accelerometer();
    RegisterIOComponent(accelerometer, Ssu::PORT_9, Ssu::PIN_0);

    lcd = new Lcd();
    RegisterIOComponent(lcd, Ssu::PORT_1, Ssu::PIN_0);

    lcdData = new LcdData(lcd);
    RegisterIOComponent(lcdData, Ssu::PORT_1, Ssu::PIN_1);

    beeper = new Beeper(board->timer->w);
    RegisterIOComponent(beeper, Ssu::PORT_8, Ssu::PIN_2);

    buttons = new Buttons(board->ssu->portB);
    RegisterIOComponent(buttons, Ssu::PORT_B, Ssu::PIN_0);

    // Attach a listener to the firmware draw event to queue color sprites
    lcd->OnFirmwareDraw += [this](const Lcd::FirmwareDrawEventArgs& args)
    {
        // Only the main 64x48 walker sprite is mirrored in color.
        if (args.width == 64 && args.height == 48)
        {
            // The firmware provides a pointer into RAM where the 2bpp walker
            // image lives. Hash the raw bytes so we can detect when the
            // grayscale walker frame actually changes.
            const size_t dataSize = (static_cast<size_t>(args.width) * (args.height / 8)) * 2;
            if (dataSize == 0 || args.pixelPtr == nullptr)
            {
                return;
            }

            const uint32_t walkerHash = fnv1a_32(args.pixelPtr, dataSize);
            lcd->NotifyWalkerDrawn(walkerHash);

            // Queue a generic color overlay for the walker sprite. The actual
            // Pokémon species is determined by GetWalkerDexNumber() on the
            // Kotlin side, which selects the appropriate colored sprite.
            Lcd::ColorDrawCommand cmd{ "walker", args.x, args.y, args.width, args.height, 0 };
            lcd->QueueColorDraw(cmd);
        }
    };
}

void PokeWalker::Tick(uint64_t cycles)
{
    H8300H::Tick(cycles);

    if (cycles % (Cpu::TICKS / Lcd::TICKS) == 0)
    {
        const uint8_t currentlyActiveView = board->ram->ReadByte(0xFFF7B1);
        const uint8_t curSubstateY = board->ram->ReadByte(0xFFF7CE);
        const uint8_t curSubstateZ = board->ram->ReadByte(0xFFF7CF);
        const uint8_t curSubstateA = board->ram->ReadByte(0xFFF7D0);
        const uint8_t curUiFlags = board->ram->ReadByte(0xFFF7AC);

        Lcd::UiState uiState{};
        uiState.currentlyActiveView = currentlyActiveView;
        uiState.curSubstateY = curSubstateY;
        uiState.curSubstateZ = curSubstateZ;
        uiState.curSubstateA = curSubstateA;
        uiState.curUiFlags = curUiFlags;

        lcd->SetUiState(uiState);
        lcd->Tick();
    }

    if (cycles % (Cpu::TICKS / Beeper::TICKS) == 0)
    {
        beeper->Tick();
    }
}

void PokeWalker::OnDraw(const EventHandlerCallback<uint8_t*>& handler) const
{
    lcd->OnDraw += handler;
}

void PokeWalker::OnFirmwareDraw(EventHandlerCallback<Lcd::FirmwareDrawEventArgs> handler) const
{
    lcd->OnFirmwareDraw += handler;
}

void PokeWalker::OnAudio(const EventHandlerCallback<AudioInformation>& handler) const
{
    beeper->OnPlayAudio += handler;
}

void PokeWalker::OnTransmitSci3(const EventHandlerCallback<uint8_t>& callback) const
{
    board->sci3->OnTransmitData += callback;
}

void PokeWalker::ReceiveSci3(const uint8_t byte) const
{
    board->sci3->Receive(byte);
}

void PokeWalker::PressButton(const Buttons::Button button) const
{
    buttons->Press(button);
}

void PokeWalker::ReleaseButton(const Buttons::Button button) const
{
    buttons->Release(button);
}

void PokeWalker::SetEepromBuffer(uint8_t* buffer) const
{
    eeprom->memory->buffer = buffer;
}

uint8_t PokeWalker::GetContrast() const
{
    return lcd->contrast - 20;
}

uint8_t* PokeWalker::GetEepromBuffer() const
{
    return eeprom->memory->buffer;
}

const std::array<uint32_t, Lcd::WIDTH * Lcd::HEIGHT>& PokeWalker::GetColorBuffer() const
{
    return lcd->GetColorBuffer();
}

void PokeWalker::SetTestSprite(const uint32_t* pixels, const size_t count, const uint8_t width, const uint8_t height) const
{
    lcd->SetTestSprite(pixels, count, width, height);
}

void PokeWalker::SetColorSprite(const std::string& id,
                                const uint32_t* pixels,
                                const size_t count,
                                const uint8_t width,
                                const uint8_t height) const
{
    lcd->SetColorSprite(id, pixels, count, width, height);
}

void PokeWalker::SetTestSpriteOffset(const int8_t xOffset, const int8_t yOffset) const
{
    // Legacy debug hook: currently unused in the new color pipeline.
    (void)xOffset;
    (void)yOffset;
}

void PokeWalker::SetTestSpriteFrameOverride(const int8_t frame) const
{
    // Legacy debug hook: currently unused in the new color pipeline.
    (void)frame;
}

void PokeWalker::SetTestSpriteAnimationModeOverride(const int8_t mode) const
{
    // Legacy debug hook: currently unused in the new color pipeline.
    (void)mode;
}

uint16_t PokeWalker::GetWalkerDexNumber() const
{
    const uint16_t base = 0x8F00;
    const uint8_t lo = eeprom->memory->ReadByte(base);
    const uint8_t hi = eeprom->memory->ReadByte(base + 1);
    const uint16_t species = static_cast<uint16_t>(lo | (static_cast<uint16_t>(hi) << 8));
    return species;
}

PokeWalker::WalkerVariantInfo PokeWalker::GetWalkerVariantInfo() const
{
    WalkerVariantInfo info{};

    const uint16_t base = 0x8F00;

    // Species is stored little-endian at +0x00..+0x01
    const uint8_t speciesLo = eeprom->memory->ReadByte(base + 0x00);
    const uint8_t speciesHi = eeprom->memory->ReadByte(base + 0x01);
    info.species = static_cast<uint16_t>(speciesLo | (static_cast<uint16_t>(speciesHi) << 8));

    // pokemon_flags_1 at +0x0D: [0..4]=variant, [5]=female
    const uint8_t flags1 = eeprom->memory->ReadByte(base + 0x0D);
    info.variant = static_cast<uint8_t>(flags1 & 0x1Fu);
    info.isFemale = (flags1 & 0x20u) != 0;

    // pokemon_flags_2 at +0x0E: [0]=has form, [1]=shiny
    const uint8_t flags2 = eeprom->memory->ReadByte(base + 0x0E);
    info.hasForm = (flags2 & 0x01u) != 0;
    info.isShiny = (flags2 & 0x02u) != 0;

    return info;
}

uint16_t PokeWalker::GetCurrentRouteId() const
{
    if (!eeprom || !eeprom->memory) {
        return 0;
    }

    const uint16_t specialFlagsAddr = 0xB800;
    const uint8_t specialFlags = eeprom->memory->ReadByte(specialFlagsAddr);

    if (specialFlags & 0x80u) {
        const uint16_t specialRouteImageAddr = 0xBF06;
        const uint8_t specialRouteImageIdx = eeprom->memory->ReadByte(specialRouteImageAddr);
        return static_cast<uint16_t>(specialRouteImageIdx);
    }

    const uint16_t routeInfoBase = 0x8F00;
    const uint16_t routeImageOffset = 0x27;
    const uint8_t routeImageIdx = eeprom->memory->ReadByte(routeInfoBase + routeImageOffset);

    return static_cast<uint16_t>(routeImageIdx);
}

bool PokeWalker::IsSpecialRoute() const
{
    if (!eeprom || !eeprom->memory) {
        return false;
    }

    const uint16_t specialFlagsAddr = 0xB800;
    const uint8_t specialFlags = eeprom->memory->ReadByte(specialFlagsAddr);

    return (specialFlags & 0x80u) != 0;
}

uint16_t PokeWalker::GetCurrentWatts() const
{
    if (!board || !board->ram)
    {
        return 0;
    }

    // RamCache_curWatts is stored as a 16-bit value in RAM.
    // DISASSEMBLY notes show it lives at address 0xF78E.
    const uint16_t wattsAddr = 0xF78E;
    const uint16_t watts = board->ram->ReadShort(wattsAddr);
    return watts;
}

void PokeWalker::AdjustWatts(const int16_t delta) const
{
    if (!board || !board->ram)
    {
        return;
    }

    const uint16_t wattsAddr = 0xF78E;
    const uint16_t currentWatts = board->ram->ReadShort(wattsAddr);

    int32_t newWatts = static_cast<int32_t>(currentWatts) + static_cast<int32_t>(delta);
    if (newWatts < 0)
    {
        newWatts = 0;
    }
    else if (newWatts > 9999)
    {
        newWatts = 9999;
    }

    board->ram->WriteShort(wattsAddr, static_cast<uint16_t>(newWatts));
}

void PokeWalker::SetupAddressHandlers() const
{
    // prevent firmware sleep when the Power Saving Cheat is enabled
    board->cpu->OnAddress(0x7944, [](Cpu* cpu)
    {
        if (g_disableSleep)
        {
            cpu->flags->zero = false;
        }

        return Continue;
    });

    // factory tests
    board->cpu->OnAddress(0x336, [](Cpu* cpu)
    {
        cpu->registers->pc += 4;

        return SkipInstruction;
    });

    // accelerometer sleep TODO proper interrupt?
    board->cpu->OnAddress(0x7700, [](Cpu* cpu)
    {
        cpu->registers->pc += 2;

        return SkipInstruction;
    });

    // hacky ir fix
    board->cpu->OnAddress(0x8EE, [](Cpu* cpu)
    {
        cpu->registers->pc += 2;

        return SkipInstruction;
    });

    // Spy on drawImageToScreen (0x80AC)
    board->cpu->OnAddress(0x80AC, [this](Cpu* cpu)
    {
        const uint32_t er0 = *cpu->registers->Register32(0x0);
        const uint32_t er1 = *cpu->registers->Register32(0x1);

        Lcd::FirmwareDrawEventArgs args{};
        args.x = er0 & 0xFFu;
        args.y = (er0 >> 8) & 0xFFu;
        args.width = er1 & 0xFFu;
        args.height = (er1 >> 8) & 0xFFu;

        const uint16_t imageDataPtr = (*cpu->registers->Register32(0x0) >> 16) & 0xFFFFu;
        args.pixelPtr = cpu->ram->buffer + imageDataPtr;

        lcd->OnFirmwareDraw(args);

        return Continue; // Allow the original firmware draw to complete
    });
}
