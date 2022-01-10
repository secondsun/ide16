package dev.secondsun.ide_16.emu.ppu

import android.R.bool




class PPUObject(ppu : PPU)  {
    fun serialize(serializer: Serializer) {

    }

    var x: UShort = 0u
    var y: UByte = 0u
    var character: UByte = 0u
    var nameselect: Boolean = false
    var vflip: Boolean = false
    var hflip: Boolean = false
    var priority: UByte = 0u
    var palette: UByte = 0u
    var size: Boolean = false

}