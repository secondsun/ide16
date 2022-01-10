package dev.secondsun.ide_16.emu.ppu

class PPUPixel(val ppu: PPU, var source : UByte = 0u,
    var priority: UByte = 0u,
    var color: UShort = 0u
){
    fun copy(): PPUPixel {
        return PPUPixel(ppu, source, priority, color)
    }
}