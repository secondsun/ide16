package dev.secondsun.ide_16.emu.ppu

class PPULatch(ppu: PPU) {
    fun serialize(serializer: Serializer) {
    }

    var interlace = false;
    var overscan = false;
    var hires = false;
    var hd = false;
    var ss = false;

    var vram: UShort = 0u;
    var oam: UByte = 0u;
    var cgram: UByte = 0u;

    var oamAddress: UShort = 0u;
    var cgramAddress: UByte = 0u;

    var mode7: UByte = 0u;
    var counters = false;
    var hcounter = false;  //hdot
    var vcounter = false;

    data class PPUState(
        //serialization.cpp
        //auto serialize(serializer&) -> void;
        var mdr: UByte = 0u,
        var bgofs: UByte = 0u
    );

    var ppu1 = PPUState()
    var ppu2 = PPUState()

}