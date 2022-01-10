package dev.secondsun.ide_16.emu.ppu


class PPUObjectTile(ppu : PPU) {
    var valid: Boolean = false
    var x: UShort = 0u
    var y: UByte = 0u
    var priority: UByte = 0u
    var palette: UByte = 0u
    var hflip: Boolean = false
    var data: UInt = 0u
}