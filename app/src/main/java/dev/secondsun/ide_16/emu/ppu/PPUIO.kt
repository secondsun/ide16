package dev.secondsun.ide_16.emu.ppu

import android.R.bool


class PPUIO(ppu: PPU) {

    var displayDisable: Boolean = true
    var displayBrightness: UByte = 0u
    var oamBaseAddress: UShort = 0u
    var oamAddress: UShort = 0u
    var oamPriority: Boolean = false
    var bgPriority: Boolean = false
    var bgMode: UByte = 0u
    var vramIncrementMode: Boolean = false
    var vramMapping: UByte = 0u
    var vramIncrementSize: UByte = 0u
    var vramAddress: UShort = 0u
    var cgramAddress: UByte = 0u
    var cgramAddressLatch: Boolean = false
    var hcounter: UShort = 0u //hdot

    var vcounter: UShort = 0u
    var interlace: Boolean = false
    var overscan: Boolean = false
    var pseudoHires: Boolean = false
    var extbg: Boolean = false

    var col : Color = Color();
    var obj = Object();
    var bg1 = Background();
    var bg2 = Background();
    var bg3 = Background();
    var bg4 = Background();
    var window = Window();
    var mode7 = Mode7();
    var mosaic = Mosaic();
    var ppu = ppu;
    fun serialize(serializer: Serializer) {

    }

    fun clone() : PPUIO {
        var io = PPUIO(ppu)

        io.displayDisable = this.displayDisable
        io.displayBrightness = this.displayBrightness
        io.oamBaseAddress = this.oamBaseAddress
        io.oamAddress = this.oamAddress
        io.oamPriority = this.oamPriority
        io.bgPriority = this.bgPriority
        io.bgMode = this.bgMode
        io.vramIncrementMode = this.vramIncrementMode
        io.vramMapping = this.vramMapping
        io.vramIncrementSize = this.vramIncrementSize
        io.vramAddress = this.vramAddress
        io.cgramAddress = this.cgramAddress
        io.cgramAddressLatch = this.cgramAddressLatch
        io.hcounter = this.hcounter //hdot

        io.vcounter = this.vcounter
        io.interlace = this.interlace
        io.overscan = this.overscan
        io.pseudoHires = this.pseudoHires
        io.extbg = this.extbg

        io.col = this.col.copy();
        io.obj = this.obj.copy();
        io.bg1 = this.bg1.copy();
        io.bg2 = this.bg2.copy();
        io.bg3 = this.bg3.copy();
        io.bg4 = this.bg4.copy();
        io.window = this.window.copy()
        io.mode7 = this.mode7.copy();
        io.mosaic = this.mosaic.copy();

        return io
    }

    data class Mosaic(var serializer: Serializer= Serializer(), var size: UByte = 1u, var counter: UByte = 0u)
    data class Mode7(
        var serializer: Serializer= Serializer(),
        var hflip: Boolean = false,
        var vflip: Boolean = false,
        var repeat: UInt = 0u,
        var a: UShort = 0u,
        var b: UShort = 0u,
        var c: UShort = 0u,
        var d: UShort = 0u,
        var x: UShort = 0u,
        var y: UShort = 0u,
        var hoffset: UShort = 0u,
        var voffset: UShort = 0u
    )

    data class Object(
        var serializer: Serializer= Serializer(),
        var window: WindowLayer = WindowLayer(),

        var aboveEnable: Boolean = false,
        var belowEnable: Boolean = false,
        var interlace: Boolean = false,
        var baseSize: UByte = 0u,
        var nameselect: UByte = 0u,
        var tiledataAddress: UShort = 0u,
        var first: UByte = 0u,
        var rangeOver: Boolean = false,
        var timeOver: Boolean = false,
        var priority: UByteArray = UByteArray(4)

    )

    data class Color(
        var serializer: Serializer = Serializer(),
        var window: WindowColor = WindowColor(),
        var enable: BooleanArray = BooleanArray(7),
        var directColor: Boolean = false,
        var blendMode: Boolean = false,  //0 = fixed; 1 = pixel
        var halve: Boolean = false,
        var mathMode: Boolean = false,   //0 = add; 1 = sub
        var fixedColor: UShort = 0u
    )

    data class Background(
        var serializer: Serializer= Serializer(),
        var window: WindowLayer = WindowLayer(),

        var aboveEnable: Boolean = false,
        var belowEnable: Boolean = false,
        var mosaicEnable: Boolean = false,
        var tiledataAddress: UShort = 0u,
        var screenAddress: UShort = 0u,
        var screenSize: UByte = 0u,
        var tileSize: Boolean = false,
        var hoffset: UShort = 0u,
        var voffset: UShort = 0u,
        var tileMode: UByte = 0u,
        var priority: UByteArray = UByteArray(2)
    )

    data class WindowColor(
        var serializer: Serializer= Serializer(),
        var oneEnable: Boolean = false,
        var oneInvert: Boolean = false,
        var twoEnable: Boolean = false,
        var twoInvert: Boolean = false,
        var mask: UInt = 0u,
        var aboveMask: UInt = 0u,
        var belowMask: UInt = 0u
    )

    data class WindowLayer(
        var serializer: Serializer= Serializer(),
        var oneEnable: Boolean = false,
        var oneInvert: Boolean = false,
        var twoEnable: Boolean = false,
        var twoInvert: Boolean = false,
        var mask: UInt = 0u,
        var aboveEnable: Boolean = false,
        var belowEnable: Boolean = false
    )

    data class Window(
        var serializer: Serializer= Serializer(),
        var oneLeft: UByte = 0u,
        var oneRight: UByte = 0u,
        var twoLeft: UByte = 0u,
        var twoRight: UByte = 0u,
    )
}