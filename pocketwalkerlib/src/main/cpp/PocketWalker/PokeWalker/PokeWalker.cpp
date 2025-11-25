#include "PokeWalker.h"
#include "../H8/Ssu/Ssu.h"
#include "../../SleepConfig.h"
#include <unordered_map>
#include <string>

#ifdef __ANDROID__
#include <android/log.h>
#define PW_LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, "PWStep", fmt, ##__VA_ARGS__)
#else
#define PW_LOGD(fmt, ...) (void)0
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

// Helper to queue a color sprite draw command.
static void QueueColorSprite(Lcd* lcd, const Lcd::FirmwareDrawEventArgs& args, const std::string& spriteId)
{
    if (!lcd) return;
    Lcd::ColorDrawCommand cmd{ spriteId, args.x, args.y, args.width, args.height, 0, args.sourceAddr };
    lcd->QueueColorDraw(cmd);
}

// Map of EEPROM source addresses to color sprite IDs.
static const std::unordered_map<uint16_t, std::string> kSpriteMap = {
    // 8x8 UI icons
    { 0x0460, "icon_bottombar_pokeball.png" },
    { 0x0470, "icon_item_pokeball_small.png" },
    { 0x0488, "icon_item_small.png" },
    { 0x0498, "icon_item_event_small.png" },
    { 0x04A8, "icon_map_scroll_small.png" },
    { 0x0650, "icon_gift_small.png" },
    { 0x0660, "icon_status_low_battery.png" },
    { 0x2030, "icon_hp_bar_small.png" },
    { 0x2040, "icon_star_small.png" },
    { 0x2470, "icon_music_note_small.png" },

    // 8x8 Arrows
    { 0x04F8, "arrows/arrow_up.png" },
    { 0x0508, "arrows/arrow_offset_up.png" },
    { 0x0518, "arrows/inverted_up.png" },
    { 0x0528, "arrows/arrow_down.png" },
    { 0x0538, "arrows/arrow_offset_down.png" },
    { 0x0548, "arrows/inverted_down.png" },
    { 0x0558, "arrows/arrow_left.png" },
    { 0x0568, "arrows/arrow_offset_left.png" },
    { 0x0578, "arrows/inverted_left.png" },
    { 0x0588, "arrows/arrow_right.png" },
    { 0x0598, "arrows/arrow_offset_right.png" },
    { 0x05A8, "arrows/inverted_right.png" },

    // 8x16 UI icons
    { 0x05B8, "arrows/namebannerarrow_left.png" },
    { 0x05D8, "arrows/namebannerarrow_right.png" },
    { 0x05F8, "icon_menu_back.png" },
    { 0x18F0, "ui_shade_bar.png" },
    { 0x2450, "icon_ir_signal.png" },

    // 16x16 Main menu icons
    { 0x1090, "icon_menu_pokeradar.png" },
    { 0x10D0, "icon_menu_dowsing.png" },
    { 0x1110, "icon_menu_connect.png" },
    { 0x1150, "icon_menu_trainercard.png" },
    { 0x1190, "icon_menu_pokemon_items.png" },
    { 0x11D0, "icon_menu_settings.png" },
    { 0x1210, "icon_trainercard_person.png" },
    { 0x1390, "icon_trainercard_route_small.png" },
    { 0x1D70, "bubble_exclaim_left_small.png" },
    { 0x1DB0, "bubble_exclaim2_left_small.png" },
    { 0x1DF0, "bubble_exclaim3_left_small.png" },
    { 0x1E30, "effect_emote_lines_left.png" },
    { 0x1B50, "tile_dowsing_field_dark.png" },
    { 0x1B90, "tile_grass_bright.png" },

    // 24x16 UI icons
    { 0x0670, "bubble_exclaim_right.png" },
    { 0x06D0, "bubble_heart_right.png" },
    { 0x0730, "bubble_music_right.png" },
    { 0x0790, "bubble_smile_right.png" },
    { 0x07F0, "bubble_neutral_right.png" },
    { 0x0850, "bubble_ellipsis_right.png" },
    { 0x08B0, "bubble_exclaim_left_large.png" },
    { 0x17D0, "icon_speaker_mute.png" },
    { 0x1830, "icon_speaker_low.png" },
    { 0x1890, "icon_speaker_high.png" },

    // 16x32 UI icons
    { 0x1E70, "effect_star_attack_small.png" },
    { 0x1EF0, "effect_star_attack_large.png" },

    // 32x24 UI icons
    { 0x1910, "icon_item_chest_large.png" },
    { 0x19D0, "icon_map_scroll_large.png" },
    { 0x1A90, "icon_gift_large.png" },
    { 0x1CB0, "tile_radar_field_bush.png" },
    { 0x1F70, "effect_cloud_appearance.png" },

    // 32x32 UI icons
    { 0x2350, "icon_pokewalker_large.png" },
};

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
        // Main 64x48 walker sprite
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

            // Queue a color overlay for the walker sprite. The actual
            // Pokémon species is determined by GetWalkerDexNumber() on the
            // Kotlin side, which selects the appropriate colored sprite.
            // The walker sprite uses a fixed ID so the Kotlin layer can
            // always upload it under "walker".
            QueueColorSprite(lcd, args, "walker");
            return;
        }

        // For other UI sprites, look up the sprite ID from the map.
        auto it = kSpriteMap.find(args.sourceAddr);
        if (it != kSpriteMap.end())
        {
            QueueColorSprite(lcd, args, it->second);
        }
    };
}

uint16_t PokeWalker::ResolveEepromAddress(const uint16_t ramAddr) const
{
    if (eepromLoadHistoryCount == 0)
    {
        return 0;
    }

    size_t count = eepromLoadHistoryCount;
    if (count > kEepromLoadHistorySize)
    {
        count = kEepromLoadHistorySize;
    }

    // Walk most-recent-first so that overlapping loads resolve to the
    // latest copy the firmware wrote into RAM.
    for (size_t i = 0; i < count; ++i)
    {
        const size_t index = (eepromLoadHistoryCount - 1u - i) % kEepromLoadHistorySize;
        const EepromLoadRecord& rec = eepromLoadHistory[index];

        if (rec.length == 0)
        {
            continue;
        }

        const uint16_t start = rec.ramDst;
        const uint16_t end = static_cast<uint16_t>(start + rec.length);

        if (ramAddr >= start && ramAddr < end)
        {
            const uint16_t offset = static_cast<uint16_t>(ramAddr - rec.ramDst);
            return static_cast<uint16_t>(rec.eepromAddr + offset);
        }
    }

    return 0;
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

void PokeWalker::AddFusedSteps(const uint16_t count) const
{
    if (count == 0)
    {
        return;
    }

    fusedStepBudget += count;
}

void PokeWalker::SetAccelerationData(const float x, const float y, const float z) const
{
    if (!accelerometer || !accelerometer->memory)
    {
        return;
    }

    auto writeAxis = [this](uint8_t addr, float value)
    {
        // Clamp to roughly [-2g, 2g] and map to signed 8-bit range.
        float clamped = value;
        if (clamped > 2.0f)
        {
            clamped = 2.0f;
        }
        else if (clamped < -2.0f)
        {
            clamped = -2.0f;
        }

        const float scaled = (clamped / 2.0f) * 127.0f;
        const int8_t raw = static_cast<int8_t>(scaled);

        accelerometer->memory->WriteByte(addr, static_cast<uint8_t>(raw));
    };

    // Store the latest normalized acceleration samples into the
    // accelerometer registers that the Pokéwalker firmware actually
    // reads for X/Y/Z. Empirically, the ROM uses 0x04/0x06/0x08 as its
    // sample bytes rather than the canonical datasheet MSB addresses.
    writeAxis(0x04, x); // X
    writeAxis(0x06, y); // Y
    writeAxis(0x08, z); // Z
}

void PokeWalker::ReadAccelerometerWindow(uint8_t start, uint8_t length, uint8_t* out) const
{
    if (!accelerometer || !accelerometer->memory || !out)
    {
        return;
    }

    const uint16_t maxSize = 0x7F;
    if (start >= maxSize)
    {
        return;
    }

    uint16_t end = static_cast<uint16_t>(start) + static_cast<uint16_t>(length);
    if (end > maxSize)
    {
        end = maxSize;
    }

    uint8_t idx = 0;
    for (uint16_t addr = start; addr < end; ++addr, ++idx)
    {
        out[idx] = accelerometer->memory->ReadByte(addr);
    }
}

void PokeWalker::SetWalkerShinyCheat(const bool shiny) const
{
    if (!eeprom || !eeprom->memory)
    {
        return;
    }

    const uint16_t base = 0x8F00;
    const uint16_t moreFlagsAddr = static_cast<uint16_t>(base + 0x0E);

    uint8_t flags = eeprom->memory->ReadByte(moreFlagsAddr);
    if (shiny)
    {
        flags = static_cast<uint8_t>(flags | 0x02u);
    }
    else
    {
        flags = static_cast<uint8_t>(flags & static_cast<uint8_t>(~0x02u));
    }

    eeprom->memory->WriteByte(moreFlagsAddr, flags);
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

    board->cpu->OnAddress(0x788A, [](Cpu* cpu)
    {
        cpu->registers->pc += 4;

        return SkipInstruction;
    });

    // hacky ir fix
    board->cpu->OnAddress(0x8EE, [](Cpu* cpu)
    {
        cpu->registers->pc += 2;

        return SkipInstruction;
    });

    // Track EEPROM -> RAM loads via eepromReadToRamAlso so we can recover
    // the originating EEPROM address for any given RAM-backed image.
    //
    // DISASSEMBLY notes show eepromReadToRamAlso at 0x5384 with
    //   (r0 = eepromAddr, e0 = ramDstPtr, r1 = nbytes)
    board->cpu->OnAddress(0x5384, [this](Cpu* cpu)
    {
        const uint32_t er0 = *cpu->registers->Register32(0x0);
        const uint32_t er1 = *cpu->registers->Register32(0x1);

        const uint16_t eepromAddr = static_cast<uint16_t>(er0 & 0xFFFFu);
        const uint16_t ramDst     = static_cast<uint16_t>((er0 >> 16) & 0xFFFFu);
        const uint16_t nbytes     = static_cast<uint16_t>(er1 & 0xFFFFu);

        const size_t index = eepromLoadHistoryCount % kEepromLoadHistorySize;
        eepromLoadHistory[index].eepromAddr = eepromAddr;
        eepromLoadHistory[index].ramDst = ramDst;
        eepromLoadHistory[index].length = nbytes;
        ++eepromLoadHistoryCount;

        return Continue;
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

        const uint16_t imageDataPtr = static_cast<uint16_t>((er0 >> 16) & 0xFFFFu);
        args.pixelPtr = cpu->ram->buffer + imageDataPtr;

        // Resolve the backing EEPROM address for this RAM-backed image so
        // higher layers can key off EEPROM ranges instead of fragile RAM
        // pointers.
        args.sourceAddr = ResolveEepromAddress(imageDataPtr);

        lcd->OnFirmwareDraw(args);

        return Continue; // Allow the original firmware draw to complete
    });

    // Log when the firmware enters handleAccelSteps (0x945A).
    board->cpu->OnAddress(0x945A, [](Cpu* cpu)
    {
        return Continue;
    });

    // Override checkAccelForSteps result at the jsr site inside handleAccelSteps
    // when we have pending fused steps from Android. The jsr @checkAccelForSteps
    // is at 0x94A8 with a return address of 0x94AC.
    board->cpu->OnAddress(0x94A8, [this](Cpu* cpu)
    {
        if (fusedStepBudget == 0)
        {
            // No pending fused steps; let the firmware call checkAccelForSteps
            // normally so native accel can still contribute.
            return Continue;
        }

        // Consume the current budget atomically w.r.t. this handler.
        const uint32_t fused = fusedStepBudget;
        fusedStepBudget = 0;

        // Map fused steps into an effective stepsDetected amplitude. The
        // batcher uses a 9-bit accumulator, so we choose a scale that is
        // large enough to cross its threshold without exploding counters.
        constexpr uint32_t kScale = 0x200; // 512
        uint32_t amplitude = fused * kScale;

        uint32_t* er0 = cpu->registers->Register32(0x0);
        *er0 = amplitude;

        cpu->registers->pc = 0x94AC; // skip over the jsr as if it had returned

        return SkipInstruction;
    });
}
