package dev.secondsun.ide_16.emu.ppu.constants

enum class Source(val value : UByte) {
    BG1(0u), BG2(1u), BG3(2u), BG4(3u), OBJ1(4u), OBJ2(5u), COL(6u)
}

enum class TileMode(val value : UByte) {
    BPP2(0u), BPP4(1u), BPP8(2u), Mode7(3u), Inactive(4u)
}

enum class ScreenMode(val value : UByte) {
    Above(0u), Below(1u)
}