#include "PocketWalker/PokeWalker/PokeWalker.h"

class PocketWalkerState {
public:
    PocketWalkerState() = default;

    ~PocketWalkerState() {
        delete pokeWalker;
    }


    static PokeWalker* Emulator() {
        return Instance().Get();
    }

    static void SetEmulator(PokeWalker* pw) {
        Instance().Set(pw);
    }


private:
    PokeWalker* pokeWalker = nullptr;

    PocketWalkerState(const PocketWalkerState&) = delete;
    PocketWalkerState& operator=(const PocketWalkerState&) = delete;


    void Set(PokeWalker* pw) {
        pokeWalker = pw;
    }

    PokeWalker* Get() const {
        return pokeWalker;
    }



    static PocketWalkerState& Instance() {
        static PocketWalkerState instance;
        return instance;
    }

};