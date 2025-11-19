#include "PokeWalker.h"

#include "../H8/Ssu/Ssu.h"
#include "../../SleepConfig.h"

#if ANDROID
#include <android/log.h>
#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, "PWStep", fmt, ##__VA_ARGS__)
#else
#define LOGD(fmt, ...) (void)0
#endif

PokeWalker::PokeWalker(uint8_t* ramBuffer, uint8_t* eepromBuffer) : H8300H(ramBuffer)
{
    SetupAddressHandlers();

    eeprom = new Eeprom(eepromBuffer);
    RegisterIOComponent(eeprom, Ssu::PORT_1, Ssu::PIN_2);

    accelerometer = new Accelerometer();
    RegisterIOComponent(accelerometer, Ssu::PORT_9, Ssu::PIN_0);

    // todo combine lcd into one component with multiple pins
    lcd = new Lcd();
    RegisterIOComponent(lcd, Ssu::PORT_1,Ssu::PIN_0);
    
    lcdData = new LcdData(lcd);
    RegisterIOComponent(lcdData, Ssu::PORT_1, Ssu::PIN_1);

    // TODO proper FTIOB and FTIOC usage, just placeholder for now
    // TODO dont use explicit timer w reference
    // TODO output only components need to be handled differently
    beeper = new Beeper(board->timer->w);
    RegisterIOComponent(beeper, Ssu::PORT_8, Ssu::PIN_2);

    // TODO input only components
    // TODO remove explicit portB ref
    // TODO use proper pins for each button instead of generalizing, placeholder for now
    buttons = new Buttons(board->ssu->portB);
    RegisterIOComponent(buttons, Ssu::PORT_B, Ssu::PIN_0);
}

void PokeWalker::Tick(uint64_t cycles)
{
    H8300H::Tick(cycles);

    if (cycles % (Cpu::TICKS / Lcd::TICKS) == 0)
    {
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

    // Uncap the main event loop timing by skipping a sleep-related
    // instruction at 0x788A.
    board->cpu->OnAddress(0x788A, [](Cpu* cpu)
    {
        LOGD("main loop sleep bypass at pc=%04x", cpu->registers->pc);

        cpu->registers->pc += 4;

        return SkipInstruction;
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

    // Log when the firmware enters handleAccelSteps (0x945A). This is a
    // read-only hook for now; it does not modify CPU state or RAM.
    board->cpu->OnAddress(0x945A, [](Cpu* cpu)
    {
        (void)cpu;

        LOGD("handleAccelSteps entered (pc=%04x)", cpu->registers->pc);

        return Continue;
    });

    // Override checkAccelForSteps result at the jsr site inside handleAccelSteps.
    // The jsr @checkAccelForSteps is at 0x94A8 with a return address of 0x94AC.
    board->cpu->OnAddress(0x94A8, [](Cpu* cpu)
    {
        auto* er0 = cpu->registers->Register32(0);
        *er0 = 1;          // constant test stepsDetected value

        LOGD("checkAccelForSteps override at pc=%04x, er0 set to %ld", cpu->registers->pc,
             static_cast<long>(*er0));

        cpu->registers->pc = 0x94AC; // skip over the jsr as if it had returned

        return SkipInstruction;
    });
}
