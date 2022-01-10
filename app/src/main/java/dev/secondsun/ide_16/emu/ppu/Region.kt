package dev.secondsun.ide_16.emu.ppu

class Region {
    companion object {
        fun NTSC(): Boolean {
            return true
        }

        fun PAL(): Boolean {
            return false
        }
    }
}
