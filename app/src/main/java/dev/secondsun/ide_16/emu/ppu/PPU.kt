package dev.secondsun.ide_16.emu.ppu

import dev.secondsun.ide_16.emu.Configuration

class PPU {




    var ppu: PPU = PPU();

    var latch = PPULatch(ppu);
    var io = PPUIO(ppu);

    var vram = UShortArray(32 * 1024);
    var cgram = UShortArray(256);
    var objects = Array(128, { i -> PPUObject(ppu) });
    var configuration = Configuration()
    //[unserialized]
    var output: UShortArray = UShortArray(0);
    var outputIndex = 0;
    var lightTable = arrayOf(UShortArray(32768));

    var ItemLimit: UInt = 0u;
    var TileLimit: UInt = 0u;

    var lines = Array<PPULine>(240) { i -> PPULine(this) }

    data class Time
        (
        var interlace: Boolean = false,
        var field: Boolean = false,
        var vperiod: UInt = 0u,
        var hperiod: UInt = 0u,
        var vcounter: UInt = 0u,
        var hcounter: UInt = 0u
    )

    data class Display (
        var interlace : Boolean = false,
        var overscan : Boolean = false,
        var vdisp : UInt = 0u,
    )

    var display = Display()
    var time = Time()

    data class Last(
        var vperiod: UInt = 0u,
        var hperiod: UInt = 0u
    )

    var last = Last()

    init {
        this.output = UShortArray(2304 * 2160)

        for( l in 0 until 16) {
            lightTable[l] = UShortArray(32768);
            for( r in 0 until 32) {
                for( g in 0 until (32)) {
                for( b  in 0 until (32)) {
                var luma :Double = l / 15.0;
                var ar :UInt = ((luma * r + 0.5).toUInt());
                var ag:UInt = ((luma * g + 0.5).toUInt());
                var ab :UInt = ((luma * b + 0.5).toUInt());
                lightTable[l][r shl 10 or g shl 5 or b shl 0] =
                    (ab shl 10 or ag shl 5 or ar shl 0).toUShort();
            }
            }
            }
        }

        for( y in 0 until (240)) {
            lines[y].y = y.toUInt();
        }
    }

    fun tick() {
        time.hcounter += 2u;  //increment by smallest unit of time.
        if (time.hcounter == hperiod()) {
            last.hperiod = hperiod();
            time.hcounter = 0u;
            tickScanline();
        }
    }

    fun tick(clocks: UInt) {
        time.hcounter += clocks;
        if (time.hcounter >= hperiod()) {
            last.hperiod = hperiod();
            time.hcounter -= hperiod();
            tickScanline();
        }
    }


    fun tickScanline() {
        if (++time.vcounter == 128u) {
            //it's not important when this is captured: it is only needed at V=240 or V=311.
            time.interlace = interlace();
            time.vperiod += if (time.interlace && !field()) 1u else 0u;
        }

        if (vcounter() == vperiod()) {
            last.vperiod = vperiod();
            //this may be off by one until V=128, hence why vperiod() is a private function.
            time.vperiod = if (Region.NTSC()) 262u else 312u;
            time.vcounter = 0u;
            time.field = time.field xor true;
        }

        time.hperiod = 1364u;
        //NTSC and PAL scanline rates would not match up with color clocks if every scanline were 1364 clocks.
        //to offset for this error, NTSC has one short scanline, and PAL has one long scanline.
        if (Region.NTSC() && !time.interlace && field() && vcounter() == 240u) time.hperiod -= 4u;
        if (Region.PAL() && time.interlace && field() && vcounter() == 311u) time.hperiod += 4u;
        scanline();


    }


    fun field(): Boolean {
        return time.field
    }

    fun vcounter(): UInt {
        return time.vcounter;
    }

    fun hcounter(): UInt {
        return time.hcounter;
    }

    fun hdot(): UInt {
        return if (hperiod() == 1360u) {
            hcounter() shr 2
        } else {
            hcounter() - (if (hcounter() > 1292u) 1u else 0u shl 1) - (if (hcounter() > 1310u) 1u else 0u shl 1) shr 2
        }
    }

    fun vperiod(): UInt {
        return time.vperiod;
    }

    fun hperiod(): UInt {
        return time.hperiod;
    }

    fun vcounter(offset: UInt): UInt {
        if (offset <= hcounter()) return vcounter();
        if (vcounter() > 0u) return vcounter() - 1u;
        return last.vperiod - 1u;
    }

    fun hcounter(offset: UInt): UInt {
        if (offset <= hcounter()) return hcounter() - offset;
        return hcounter() + last.hperiod - offset;
    }

    fun reset() {
        time = Time();
        last = Last();

        last.vperiod = if (Region.NTSC()) 262u else 312u;
        time.vperiod = last.vperiod
        last.hperiod = 1364u;
        time.hperiod = last.hperiod
    }

    fun serialize(serializer: Serializer) {

    }

    var clock : ULong = 0u;

    fun synchronizeCPU()  {
        if(clock >= 0u){ scheduler.resume(cpu.thread);}
    }

    auto PPU::Enter() -> void {
        while(true) {
            scheduler.synchronize();
            ppu.main();
        }
    }

    auto PPU::step(uint clocks) -> void {
        tick(clocks);
        ppubase.clock += clocks;
        synchronizeCPU();
    }

    auto PPU::main() -> void {
        scanline();

        if(system.frameCounter == 0 && !system.runAhead) {
            uint y = vcounter();
            if(y >= 1 && y <= 239) {
                step(renderCycle());
                bool mosaicEnable = io.bg1.mosaicEnable || io.bg2.mosaicEnable || io.bg3.mosaicEnable || io.bg4.mosaicEnable;
                if(y == 1) {
                    io.mosaic.counter = mosaicEnable ? io.mosaic.size + 1 : 0;
                }
                if(io.mosaic.counter && !--io.mosaic.counter) {
                    io.mosaic.counter = mosaicEnable ? io.mosaic.size + 0 : 0;
                }
                lines[y].cache();
            }
        }

        step(hperiod() - hcounter());
    }

    auto PPU::scanline() -> void {
        if(vcounter() == 0) {
            if(latch.overscan && !io.overscan) {
                //when disabling overscan, clear the overscan area that won't be rendered to:
                for(uint y = 1; y <= 240; y++) {
                    if(y >= 8 && y <= 231) continue;
                    auto output = ppu.output + y * 1024;
                    memory::fill<uint16>(output, 1024);
                }
            }

            ppubase.display.interlace = io.interlace;
            ppubase.display.overscan = io.overscan;
            latch.overscan = io.overscan;
            latch.hires = false;
            latch.hd = false;
            latch.ss = false;
            io.obj.timeOver = false;
            io.obj.rangeOver = false;
        }

        if(vcounter() > 0 && vcounter() < vdisp()) {
            latch.hires |= io.pseudoHires || io.bgMode == 5 || io.bgMode == 6;
            //supersampling and EXTBG mode are not compatible, so disable supersampling in EXTBG mode
            latch.hd |= io.bgMode == 7 && hdScale() > 1 && (hdSupersample() == 0 || io.extbg == 1);
            latch.ss |= io.bgMode == 7 && hdScale() > 1 && (hdSupersample() == 1 && io.extbg == 0);
        }

        if(vcounter() == vdisp()) {
            if(!io.displayDisable) oamAddressReset();
        }

        if(vcounter() == 240) {
            Line::flush();
        }
    }

    auto PPU::refresh() -> void {
        if(system.frameCounter == 0 && !system.runAhead) {
            auto output = this->output;
            uint pitch, width, height;
            if(!hd()) {
                pitch  = 512 << !interlace();
                width  = 256 << hires();
                height = 240 << interlace();
            } else {
                pitch  = 256 * hdScale();
                width  = 256 * hdScale();
                height = 240 * hdScale();
            }

            //clear the areas of the screen that won't be rendered:
            //previous video frames may have drawn data here that would now be stale otherwise.
            if(!latch.overscan && pitch != frame.pitch && width != frame.width && height != frame.height) {
                for(uint y : range(240)) {
                    if(y >= 8 && y <= 230) continue;  //these scanlines are always rendered.
                    auto output = this->output + (!hd() ? (y * 1024 + (interlace() && field() ? 512 : 0)) : (y * 256 * hdScale() * hdScale()));
                    auto width = (!hd() ? (!hires() ? 256 : 512) : (256 * hdScale() * hdScale()));
                    memory::fill<uint16>(output, width);
                }
            }

            if(auto device = controllerPort2.device) device->draw(output, pitch * sizeof(uint16), width, height);
            platform->videoFrame(output, pitch * sizeof(uint16), width, height, hd() ? hdScale() : 1);

            frame.pitch  = pitch;
            frame.width  = width;
            frame.height = height;
        }
        if(system.frameCounter++ >= system.frameSkip) system.frameCounter = 0;
    }

    auto PPU::load() -> bool {
        return true;
    }

    auto PPU::power(bool reset) -> void {
        PPUcounter::reset();
        memory::fill<uint16>(output, 1024 * 960);

        function<uint8 (uint, uint8)> reader{&PPU::readIO, this};
        function<void  (uint, uint8)> writer{&PPU::writeIO, this};
        bus.map(reader, writer, "00-3f,80-bf:2100-213f");

        if(!reset) {
            for(auto& word : vram) word = 0x0000;
            for(auto& color : cgram) color = 0x0000;
            for(auto& object : objects) object = {};
        }

        latch = {};
        io = {};
        updateVideoMode();

        #undef ppu
                ItemLimit = !configuration.hacks.ppu.noSpriteLimit ? 32 : 128;
        TileLimit = !configuration.hacks.ppu.noSpriteLimit ? 34 : 128;

        Line::start = 0;
        Line::count = 0;

        frame = {};
    }


    var scanline: () -> Unit = {};

    fun hdScale() : UInt { return 1u; /*configuration.hacks.ppu.mode7.scale*/ }

    fun interlace() : Boolean { return display.interlace; }
    fun overscan()  : Boolean { return display.overscan; }
    fun vdisp(): UInt { return display.vdisp; }
    fun hires(): Boolean { return latch.hires; }
    fun hd(): Boolean { return latch.hd; }
    fun ss(): Boolean { return latch.ss; }
    fun deinterlace() :Boolean { return configuration.hacks.ppu.deinterlace; }
}