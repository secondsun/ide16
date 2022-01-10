package dev.secondsun.ide_16.emu

class Configuration {

    data class CPU(
        var version: UInt = 2u
    );
    data class VRAM(
        var size: UInt = 0x10000u,
    );

    data class PPU1(
        var version: UInt = 1u,
        var vram: VRAM = VRAM()
    )

    data class PPU2(
        var version: UInt = 3u,
    );

    data class Serialization(
        var method: String = "Fast"
    )

    data class System(
        var cpu: CPU = CPU(),
        var ppu1: PPU1 = PPU1(),
        var ppu2: PPU2 = PPU2(),
        var serialization: Serialization = Serialization(),
    );

    var system = System()

    data class Video(
        var blurEmulation: Boolean = true,
        var colorEmulation: Boolean = true
    );

    var video = Video()

    data class HacksCPU(
        var overclock: UInt = 100u,
        var fastMath: Boolean = false
    );

    data class Mode7(
        var scale: UInt = 1u,
        var perspective: Boolean = true,
        var supersample: Boolean = false,
        var mosaic: Boolean = true
    )

    data class PPU(
        var fast: Boolean = true,
        var deinterlace: Boolean = true,
        var noSpriteLimit: Boolean = false,
        var noVRAMBlocking: Boolean = false,
        var renderCycle: UInt = 512u,
        var mode7: Mode7 = Mode7()
    );
    ;

    data class DSP(
        var fast: Boolean = true,
        var cubic: Boolean = false,
        var echoShadow: Boolean = false
    );

    data class Coprocessor(
        var delayedSync: Boolean = true,
        var preferHLE: Boolean = false
    )

    data class SA1(
        var overclock: UInt = 100u
    )

    data class SuperFX(
        var overclock: UInt = 100u
    );
    data class Hacks(
        var hotfixes: Boolean = true,
        var entropy: String = "Low",

        var cpu: HacksCPU = HacksCPU(),
        var ppu: PPU = PPU(),

        var dsp: DSP = DSP(),
        var coprocessor: Coprocessor = Coprocessor(),
        var sa1: SA1 = SA1(),
        var superfx: SuperFX = SuperFX()
    );

    var hacks = Hacks()
}