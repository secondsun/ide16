package dev.secondsun.ide_16.emu.ppu

import dev.secondsun.ide_16.emu.ppu.constants.Source
import dev.secondsun.ide_16.emu.ppu.constants.TileMode

class PPULine(ppu: PPU) {

    //unserialized:
    var y: UInt = 0u;  //constant
    var fieldID: Boolean = false;
    var ppu = ppu
    var io: PPUIO = ppu.io;
    var cgram: UShortArray = UShortArray(256)

    var items: Array<PPUObjectItem> =
        Array(128, { _ -> PPUObjectItem(ppu) });  //32 on real hardware
    var tiles: Array<PPUObjectTile> = Array(128, { _ -> PPUObjectTile(ppu) })

    var above: Array<PPUPixel> = Array(256 * 9 * 9, { _ -> PPUPixel(ppu) });
    var below: Array<PPUPixel> = Array(256 * 9 * 9, { _ -> PPUPixel(ppu) });

    var windowAbove: BooleanArray = BooleanArray(56);
    var windowBelow: BooleanArray = BooleanArray(56);

    //flush()
    companion object {
        var start = 0u;
        var count = 0u;
    }

    inline fun field(): Boolean {
        return fieldID; }

    fun flush() {
        if (PPULine.count != 0u) {
            /*if (ppu.hdScale() > 1u) cacheMode7HD();*/ //TODO: enable hd
            for (y in 0u until PPULine.count) {
                if (ppu.deinterlace()) {
                    if (!ppu.interlace()) {
                        //some games enable interlacing in 240p mode, just force these to even fields
                        ppu.lines[PPULine.start + y].render(false);
                    } else {
                        //for actual interlaced frames, render both fields every farme for 480i -> 480p
                        ppu.lines[PPULine.start + y].render(false);
                        ppu.lines[PPULine.start + y].render(true);
                    }
                } else {
                    //standard 240p (progressive) and 480i (interlaced) rendering
                    ppu.lines[PPULine.start + y].render(ppu.field());
                }
            }
            PPULine.start = 0u;
            PPULine.count = 0u;
        }
    }

    fun cache() {
        var y = ppu.vcounter();
        if (ppu.io.displayDisable || y >= ppu.vdisp()) {
            io.displayDisable = true;
        } else {
            io = ppu.io.clone()
            cgram = ppu.cgram.copyOf()
        }
        if (PPULine.count == 0u) {
            PPULine.start = y;
        }
        PPULine.count++;
    }

    fun render(fieldID: Boolean) {
        this.fieldID = fieldID;
        var y: UInt = this.y + if (!ppu.latch.overscan) 7u else 0u

        var hd: Boolean = ppu.hd();
        var ss: Boolean = ppu.ss();
        var scale: UInt = ppu.hdScale();
        var outputIndex :UInt = (ppu.outputIndex + (if (!hd)
            (y * 1024u + if (ppu.interlace() && field()) 512u else 0u)
        else (y * 256u * scale * scale)) as Int).toUInt();

        var width :UInt = if (!hd)
            (if (!ppu.hires()) 256u else 512u)
        else (256u * scale * scale);

        if (io.displayDisable) {
            for (x in outputIndex until width) {
                ppu.output[x.toInt()] = 0u;
            }
            return;
        }

        var hires: Boolean =
            io.pseudoHires || io.bgMode == (5u as UByte) || io.bgMode == (6u as UByte);
        var aboveColor: UShort = cgram[0];
        var belowColor: UShort = if (hires) cgram[0] else io.col.fixedColor;
        var xa: UInt =
            if ((hd || ss) && ppu.interlace() && field()) 256u * scale * scale / 2u else 0u;
        var xb: UInt =
            if (!(hd || ss)) 256u else if (ppu.interlace() && !field()) 256u * scale * scale / 2u else 256u * scale * scale;
        for (x: UInt in xa until xb) {
            above[x as Int] = PPUPixel(ppu, Source.COL.value, 0u, aboveColor)
            below[x as Int] = PPUPixel(ppu, Source.COL.value, 0u, belowColor);
        }

        //hack: generally, renderBackground/renderObject ordering do not matter.
        //but for HD mode 7, a larger grid of pixels are generated, and so ordering ends up mattering.
        //as a hack for Mohawk & Headphone Jack, we reorder things for BG2 to render properly.
        //longer-term, we need to devise a better solution that can work for every game.
        renderBackground(io.bg1, Source.BG1.value);
        if (!io.extbg) renderBackground(io.bg2, Source.BG2.value);
        renderBackground(io.bg3, Source.BG3.value);
        renderBackground(io.bg4, Source.BG4.value);
        renderObject(io.obj);
        if (io.extbg) renderBackground(io.bg2, Source.BG2.value);
        renderWindow(io.col.window, io.col.window.aboveMask, windowAbove);
        renderWindow(io.col.window, io.col.window.belowMask, windowBelow);

        var luma = ppu.lightTable[io.displayBrightness as Int];
        var curr: UInt = 0u
        var prev: UInt = 0u;
        if (hd) {
            for (x: UInt in 0u until (256u * scale * scale)) {
                ppu.output[ppu.outputIndex++] = luma[
                        pixel(
                            x / scale and 255u,
                            above[x.toInt()],
                            below[x.toInt()]
                        ).toInt()
                ];
            }
        } else if (width == 256u) for (x in 0u until 256u) {
            ppu.output[ppu.outputIndex++] = luma[pixel(x, above[x as Int], below[x as Int]) as Int];
        } else if (!hires) for (x in 0u until (256u)) {
            var color = luma[pixel(x, above[x], below[x]).toInt()];
            ppu.output[ppu.outputIndex++] = color;
            ppu.output[ppu.outputIndex++] = color;
        } else if (!ppu.configuration.video.blurEmulation) for (x in 0u until (256u)) {
            ppu.output[ppu.outputIndex++] = luma[pixel(x, below[x], above[x])];
            ppu.output[ppu.outputIndex++] = luma[pixel(x, above[x], below[x])];
        } else for (x in 0u until (256u)) {
            curr = luma[pixel(x, below[x], above[x])].toUInt();
            ppu.output[ppu.outputIndex++] =
                ((prev + curr - ((prev xor curr) and 0x0421u)) shr 1).toUShort();
            prev = curr;
            curr = luma[pixel(x, above[x], below[x])].toUInt();
            ppu.output[ppu.outputIndex++] =
                ((prev + curr - ((prev xor curr) and 0x0421u)) shr 1).toUShort();
            prev = curr;
        }
    }

    fun pixel(x: UInt, above: PPUPixel, below: PPUPixel): UShort {
        if (!windowAbove[x as Int]) above.color = 0x0000u;
        if (!windowBelow[x as Int]) return above.color;
        if (!io.col.enable[above.source as Int]) return above.color;
        if (!io.col.blendMode) return blend(
            above.color as UInt,
            io.col.fixedColor as UInt,
            io.col.halve && windowAbove[x]
        );
        return blend(
            above.color as UInt,
            below.color as UInt,
            io.col.halve && windowAbove[x] && below.source != Source.COL.value
        );
    }

    fun blend(x: UInt, y: UInt, halve: Boolean): UShort {
        if (!io.col.mathMode) {  //add
            if (!halve) {
                var sum = x + y;
                var carry = (sum - ((x xor y) and 0x0421u)) and 0x8420u;
                return ((sum - carry) or (carry - (carry shr 5))) as UShort;
            } else {
                return ((x + y - ((x xor y) and 0x0421u)) shr 1) as UShort;
            }
        } else {  //sub
            var diff = x - y + 0x8420u;
            var borrow = (diff - ((x xor y) and 0x8420u)) and 0x8420u;
            if (!halve) {
                return ((diff - borrow) and (borrow - (borrow shr 5))) as UShort;
            } else {
                return ((((diff - borrow) and (borrow - (borrow shr 5))) and 0x7bdeu) shr 1) as UShort;
            }
        }
    }


    inline fun directColor(paletteIndex: UInt, paletteColor: UInt): UShort {
        //paletteIndex = bgr
        //paletteColor = BBGGGRRR
        //output       = 0 BBb00 GGGg0 RRRr0
        return ((paletteColor shl 2 and 0x001cu) + (paletteIndex shl 1 and 0x0002u)   //R
                + (paletteColor shl 4 and 0x0380u) + (paletteIndex shl 5 and 0x0040u)   //G
                + (paletteColor shl 7 and 0x6000u) + (paletteIndex shl 10 and 0x1000u)) as UShort;  //B
    }

    inline fun plotAbove(x: UInt, source: UByte, priority: UByte, color: UShort) {
//        if (ppu.hd()) {
//            return plotHD(above, x, source, priority, color, false, false);
//        } TODO: enable hd
        if (priority > above[x as Int].priority) {
            above[x as Int] = PPUPixel(ppu, source, priority, color)
        }
    };


    inline fun plotBelow(x: UInt, source: UByte, priority: UByte, color: UShort) {
//        if (ppu.hd()) {
//            return plotHD(below, x, source, priority, color, false, false);
//        }TODO: enable hd
        if (priority > below[x as Int].priority) {
            below[x as Int] = PPUPixel(ppu, source, priority, color);
        }
    }

    inline fun plotHD(
        pixels: Array<PPUPixel>,
        x: UInt,
        source: UByte,
        priority: UByte,
        color: UShort,
        hires: Boolean,
        subpixel: Boolean
    ) {
        var scale = ppu.hdScale();
        var xss = if (hires && subpixel) (scale / 2u) else 0u;
        var ys = if (ppu.interlace() && field()) (scale / 2u) else 0u;
        if (priority > pixels[((x * scale) + xss + ((ys * 256u) * scale)) as Int].priority) {
            var p = PPUPixel(ppu, source, priority, color);
            var xsm = if (hires && !subpixel) scale / 2u else scale;
            var ysm = if (ppu.interlace() && !field()) scale / 2u else scale;
            for (xs in xss until xsm) {
                pixels[((x * scale) + xs + (ys * 256u) * scale) as Int] = p;
            }
            var size = (xsm - xss);
            var source = pixels[((x * scale) + xss + (ys * 256u) * scale) as Int];
            for (yst in (ys + 1u) until ysm) {
                pixels[((x * scale) + xss + (yst * 256u) * scale) as Int] = source.copy()
            }
        }
    }

    fun renderBackground(self: PPUIO.Background, source: UByte) {
        if (!self.aboveEnable && !self.belowEnable) return;
        if (self.tileMode == TileMode.Mode7.value) return renderMode7(self, source);
        if (self.tileMode == TileMode.Inactive.value) return;

        var windowAbove: BooleanArray = BooleanArray(256);
        var windowBelow: BooleanArray = BooleanArray(256);
        renderWindow(self.window, self.window.aboveEnable, windowAbove);
        renderWindow(self.window, self.window.belowEnable, windowBelow);

        var hires = io.bgMode == 5u as UByte || io.bgMode == 6u as UByte;
        var offsetPerTileMode =
            io.bgMode == (2u as UByte) || io.bgMode == (4u as UByte) || io.bgMode == 6u as UByte;
        var directColorMode =
            io.col.directColor && source == Source.BG1.value && (io.bgMode == 3u as UByte || io.bgMode == 4u as UByte);
        var colorShift = 3u + self.tileMode;
        var width = 256u shl (if (hires) 1 else 0);

        var tileHeight = 3u + if (self.tileSize) 1u else 0u;
        var tileWidth = if (!hires) tileHeight else 4u;
        var tileMask = 0x0fff shr self.tileMode as Int;
        var tiledataIndex = (self.tiledataAddress.toUInt() shr 3).toUShort() + self.tileMode;

        var paletteBase = if (io.bgMode == 0u as UByte) (source.toUInt() shl 5).toUByte() else 0u;
        var paletteShift = 2 shl self.tileMode.toInt();

        var hscroll = self.hoffset;
        var vscroll = self.voffset;
        var hmask =
            (width shl (if (self.tileSize) 1 else 0) shl if ((self.screenSize and 1u) > 0u) 1 else 0) - 1u;
        var vmask =
            (width shl (if (self.tileSize) 1 else 0) shl if ((self.screenSize and 2u) > 0u) 1 else 0) - 1u;

        var y = this.y;

        if (hires) {
            hscroll = (hscroll.toUInt() shl 1).toUShort();
            if (io.interlace) {
                y = (y shl 1) or (if (field() && !self.mosaicEnable) 1u else 0u)
            };
        }
        if (self.mosaicEnable) {
            y -= (io.mosaic.size - io.mosaic.counter) shl if (hires && io.interlace) 1 else 0;
        }

        var mosaicCounter = 1u;
        var mosaicPalette = 0u;
        var mosaicPriority: UByte = 0u;
        var mosaicColor: UShort = 0u;

        var x = (0u - (hscroll and 7u));
        while (x < width) {
            var hoffset: UInt = x + hscroll;
            var voffset: UInt = y + vscroll;
            if (offsetPerTileMode) {
                var validBit: UInt = 0x2000u shl source.toInt();
                var offsetX: UInt = x + (hscroll and 7u);
                if (offsetX >= 8u) {  //first column is exempt
                    var hlookup: UInt =
                        getTile(
                            io.bg3,
                            (offsetX - 8u) + (io.bg3.hoffset and 7u.inv().toUShort()),
                            io.bg3.voffset + 0u
                        ).toUInt();
                    if (io.bgMode == 4u.toUByte()) {
                        if ((hlookup and validBit) != 0u) {
                            if ((hlookup and 0x8000u) == 0u) {
                                hoffset = offsetX + (hlookup and 7u.inv());
                            } else {
                                voffset = y + hlookup;
                            }
                        }
                    } else {
                        var vlookup: UInt = getTile(
                            io.bg3,
                            (offsetX - 8u) + (io.bg3.hoffset and 7u.inv().toUShort()),
                            io.bg3.voffset + 8u
                        ).toUInt();
                        if ((hlookup and validBit) != 0u) {
                            hoffset = offsetX + (hlookup and 7u.inv());
                        }
                        if ((vlookup and validBit) != 0u) {
                            voffset = y + vlookup;
                        }
                    }
                }
            }
            hoffset = hoffset and hmask;
            voffset = voffset and vmask;

            var tileNumber: UInt = getTile(self, hoffset, voffset);
            var mirrorY: UInt = if ((tileNumber and 0x8000u) != 0u) 7u else 0u;
            var mirrorX: UInt = if ((tileNumber and 0x4000u) != 0u) 7u else 0u;
            var tilePriority: UByte = self.priority[if ((tileNumber and 0x2000u) == 0u) 0 else 1];
            var paletteNumber: UInt = (tileNumber shr 10) and 7u;
            var paletteIndex: UInt = paletteBase + (paletteNumber shl paletteShift) and 0xffu;

            if (tileWidth == 4u && ((if ((hoffset and 8u) == 0u) 0u else 1u) xor (if (mirrorX == 0u) 0u else 1u)) != 0u) tileNumber += 1u;
            if (tileHeight == 4u && 0u != (bool(voffset and 8u) xor bool(mirrorY))) tileNumber += 16u;
            tileNumber = (tileNumber and 0x03ffu) + tiledataIndex and tileMask.toUInt();

            var address: UShort;
            address =
                ((tileNumber shl colorShift.toInt()) + (voffset and 7u xor mirrorY) and 0x7fffu).toUShort();

            var data: ULong;
            data = (ppu.vram[(address + 0u).toInt()].toUInt() shl 0).toULong();
            data = data or (ppu.vram[(address + 8u).toInt()].toInt() shl 16).toULong();
            data = data or ppu.vram[(address + 16u).toInt()].toULong() shl 32;
            data = data or ppu.vram[(address + 24u).toInt()].toULong() shl 48;

            for (tileX: UInt in 0u until 8u) {

                if ((x and width) != 0u) {
                    x++; continue
                };  //x < 0 || x >= width
                if (--mosaicCounter == 0u) {
                    var color: UInt;
                    var shift = if (mirrorX != 0u) tileX else 7u - tileX;

                    color = (data shr (shift + 0u).toInt() and 1u).toUInt();
                    color = (color + data shr (shift + 7u).toInt() and 2u).toUInt();

                    if (self.tileMode >= TileMode.BPP4.value) {
                        color += (data shr ((shift + 14u).toInt()) and 4u).toUInt();
                        color += (data shr ((shift + 21u).toInt()) and 8u).toUInt();
                    }
                    if (self.tileMode >= TileMode.BPP8.value) {
                        color += (data shr ((shift + 28u).toInt()) and 16u).toUInt();
                        color += (data shr ((shift + 35u).toInt()) and 32u).toUInt();
                        color += (data shr ((shift + 42u).toInt()) and 64u).toUInt();
                        color += (data shr ((shift + 49u).toInt()) and 128u).toUInt();
                    }

                    mosaicCounter =
                        if (self.mosaicEnable) io.mosaic.size.toUInt() shl (if (hires) 1 else 0) else 1u;
                    mosaicPalette = color;
                    mosaicPriority = tilePriority;
                    if (directColorMode) {
                        mosaicColor = directColor(paletteNumber, mosaicPalette);
                    } else {
                        mosaicColor = cgram[(paletteIndex + mosaicPalette).toInt()];
                    }
                }
                if (mosaicPalette == 0u) {
                    x++; continue
                };

                if (!hires) {
                    if (self.aboveEnable && !windowAbove[x.toInt()]) plotAbove(
                        x,
                        source,
                        mosaicPriority,
                        mosaicColor
                    );
                    if (self.belowEnable && !windowBelow[x.toInt()]) plotBelow(
                        x,
                        source,
                        mosaicPriority,
                        mosaicColor
                    );
                } else {
                    var X: UInt = x shr 1;
                    if (!ppu.hd()) {
                        if ((x and 1u) != 0u) {
                            if (self.aboveEnable && !windowAbove[X.toInt()]) plotAbove(
                                X,
                                source,
                                mosaicPriority,
                                mosaicColor
                            );
                        } else {
                            if (self.belowEnable && !windowBelow[X.toInt()]) plotBelow(
                                X,
                                source,
                                mosaicPriority,
                                mosaicColor
                            );
                        }
                    } else {
                        if (self.aboveEnable && !windowAbove[X.toInt()]) plotHD(
                            above,
                            X,
                            source,
                            mosaicPriority,
                            mosaicColor,
                            true,
                            (x and 1u) != 0u
                        );
                        if (self.belowEnable && !windowBelow[X.toInt()]) plotHD(
                            below,
                            X,
                            source,
                            mosaicPriority,
                            mosaicColor,
                            true,
                            (x and 1u) != 0u
                        );
                    }
                }
                x++;
            }
        }
    }

    private inline fun bool(voffset: UInt): UInt {
        return if (voffset == 0u) 0u else 1u
    }

    fun getTile(self: PPUIO.Background, hoffset: UInt, voffset: UInt): UInt {
        var hires: Boolean = io.bgMode == (5u as UByte) || io.bgMode == (6u as UByte);
        var tileHeight: UInt = 3u + if (self.tileSize) 1u else 0u;
        var tileWidth: UInt = if (!hires) tileHeight else 4u;
        var screenX: UInt = if ((self.screenSize and 1u) != (0u as UByte)) (32u shl 5) else 0u;
        var screenY: UInt =
            if (self.screenSize and 2u != (0u as UByte)) ((32 shl 5).toUInt() + (self.screenSize and 1u).toUInt()) else 0u;
        var tileX: UInt = hoffset shr (tileWidth as Int);
        var tileY: UInt = voffset shr (tileHeight as Int);
        var offset: UInt = (tileY and 0x1fu) shl 5 or (tileX and 0x1fu);
        if ((tileX and 0x20u) != 0u) {
            offset += screenX
        };
        if ((tileY and 0x20u) != 0u) {
            offset += screenY
        };
        return ppu.vram[(self.screenAddress + offset and 0x7fffu) as Int].toUInt();
    }

    fun renderMode7(self: PPUIO.Background, source: UByte) {
        //HD mode 7 support
//        if(!ppu.hdMosaic() || !self.mosaicEnable || io.mosaic.size == 1u) {
//            if(ppu.hdScale() > 1u) return renderMode7HD(self, source);
//        } TODO: enable hd

        var Y: UInt = this.y;
        if (self.mosaicEnable) Y -= io.mosaic.size - io.mosaic.counter;
        var y: UInt = if (!io.mode7.vflip) Y else 255u - Y;

        var a = io.mode7.a;
        var b = io.mode7.b;
        var c = io.mode7.c;
        var d = io.mode7.d;
        var hcenter = io.mode7.x;
        var vcenter = io.mode7.y;
        var hoffset = io.mode7.hoffset;
        var voffset = io.mode7.voffset;

        var mosaicCounter: UInt = 1u;
        var mosaicPalette: UInt = 0u;
        var mosaicPriority: UByte = 0u;
        var mosaicColor: UShort = 0u;

        var clip = { n: UInt -> if ((n and 0x2000u) != 0u) (n or 1023u.inv()) else (n and 1023u) }
        var originX =
            (a * clip(hoffset - hcenter) and 63u.inv()) + (b * clip(voffset - vcenter) and 63u.inv()) + (b * y and 63u.inv()) + (hcenter.toUInt() shl 8);
        var originY =
            (c * clip(hoffset - hcenter) and 63u.inv()) + (d * clip(voffset - vcenter) and 63u.inv()) + (d * y and 63u.inv()) + (vcenter.toUInt() shl 8);

        var windowAbove = BooleanArray(256);
        var windowBelow = BooleanArray(256);
        renderWindow(self.window, self.window.aboveEnable, windowAbove);
        renderWindow(self.window, self.window.belowEnable, windowBelow);

        for (X in 0u until 256u) {
            var x: UInt = if (!io.mode7.hflip) X else 255u - X;
            var pixelX: UInt = (originX + a * x shr 8);
            var pixelY: UInt = (originY + c * x shr 8);
            var tileX: UInt = pixelX shr 3 and 127u;
            var tileY: UInt = pixelY shr 3 and 127u;
            var outOfBounds: Boolean =
                if ((pixelX or pixelY) and 1023u.inv() != 0u) true else false;
            var tileAddress: UShort = (tileY * 128u + tileX).toUShort();
            var paletteAddress: UShort = (((pixelY and 7u) shl 3) + (pixelX and 7u)).toUShort();
            var tile: UByte =
                if (io.mode7.repeat == 3u && outOfBounds) 0u else (ppu.vram[tileAddress].toUInt() shr 0).toUByte();
            var palette: UByte =
                if (io.mode7.repeat == 2u && outOfBounds) 0u else (ppu.vram[(tile.toUInt() shl 6 or paletteAddress.toUInt()).toInt()].toUInt() shr 8).toUByte();

            var priority: UByte = 0u;
            if (source == Source.BG1.value) {
                priority = self.priority[0];
            } else if (source == Source.BG2.value) {
                priority = self.priority[(palette.toUInt() shr 7).toInt()];
                palette = palette and 0x7fu;
            }

            if (--mosaicCounter == 0u) {
                mosaicCounter = if (self.mosaicEnable) io.mosaic.size.toUInt() else 1u;
                mosaicPalette = palette.toUInt();
                mosaicPriority = priority.toUByte();
                if (io.col.directColor && source == Source.BG1.value) {
                    mosaicColor = directColor(0u, palette.toUInt());
                } else {
                    mosaicColor = cgram[palette.toInt()];
                }
            }
            if (mosaicPalette == 0u) continue;

            if (self.aboveEnable && !windowAbove[X.toInt()]) plotAbove(
                X,
                source,
                mosaicPriority,
                mosaicColor
            );
            if (self.belowEnable && !windowBelow[X.toInt()]) plotBelow(
                X,
                source,
                mosaicPriority,
                mosaicColor
            );
        }
    }

    fun renderObject(self: PPUIO.Object) {
        if (!self.aboveEnable && !self.belowEnable) return;

        var windowAbove = BooleanArray(256);
        var windowBelow = BooleanArray(256);
        renderWindow(self.window, self.window.aboveEnable, windowAbove);
        renderWindow(self.window, self.window.belowEnable, windowBelow);

        var itemCount = 0u;
        var tileCount = 0u;
        for (n in 0 until ppu.ItemLimit.toInt()) items[n].valid = false;
        for (n in 0 until ppu.TileLimit.toInt()) tiles[n].valid = false;

        for (n in 0 until 128) {
            var item = PPUObjectItem(ppu)
            with(item) { valid = true; index = (self.first + n.toUInt() and 127u).toUByte() };
            val obj = ppu.objects[item.index];

            if (!obj.size) {
                var widths = arrayOf(8u, 8u, 8u, 16u, 16u, 32u, 16u, 16u);
                var heights = arrayOf(8u, 8u, 8u, 16u, 16u, 32u, 32u, 32u);
                item.width = widths[self.baseSize.toInt()].toUByte();
                item.height = heights[self.baseSize.toInt()].toUByte();
                if (self.interlace && self.baseSize >= 6u) item.height = 16u;  //hardware quirk
            } else {
                var widths = arrayOf(16u, 32u, 64u, 32u, 64u, 64u, 32u, 32u);
                var heights = arrayOf(16u, 32u, 64u, 32u, 64u, 64u, 64u, 32u);
                item.width = widths[self.baseSize.toInt()].toUByte();
                item.height = heights[self.baseSize.toInt()].toUByte();
            }

            if (obj.x > 256u && obj.x + item.width - 1u < 512u) continue;
            var height: UInt = item.height.toUInt() shr if (self.interlace) 1 else 0;
            if ((y >= obj.y && y < obj.y + height)
                || (obj.y + height >= 256u && y < (obj.y + height and 255u))
            ) {
                if (itemCount++ >= ppu.ItemLimit) break;
                items[(itemCount - 1u).toInt()] = item;
            }
        }

        for (n in (ppu.ItemLimit - 1u) downTo 0u) {
            var item = items[n];
            if (!item.valid) continue;

            var obj = ppu.objects[item.index];
            var tileWidth: UInt = item.width.toUInt() shr 3;
            var x = obj.x;
            var y = this.y - obj.y and 0xffu;
            if (self.interlace) y = y shl 1;

            if (obj.vflip) {
                if (item.width == item.height) {
                    y = item.height - 1u - y;
                } else if (y < item.width) {
                    y = item.width - 1u - y;
                } else {
                    y = item.width + (item.width - 1u) - (y - item.width);
                }
            }

            if (self.interlace) {
                y = if (!obj.vflip) y + if (field()) 1u else 0u else y - if (field()) 1u else 0u;
            }

            x = x and 511u;
            y = y and 255u;

            var tiledataAddress: UShort = self.tiledataAddress;
            if (obj.nameselect) {
                tiledataAddress = (tiledataAddress + (1u + self.nameselect shl 12)).toUShort();
            }
            var characterX: UShort = (obj.character and 15u).toUShort();
            var characterY: UShort =
                (((obj.character.toUInt() shr 4) + (y shr 3) and 15u) shl 4).toUShort();

            for (tileX in 0 until (tileWidth.toInt())) {
                var objectX: UInt = x + (tileX.toUInt() shl 3) and 511u;
                if (x.toUInt() != 256u && objectX >= 256u && objectX + 7u < 512u) continue;

                var tile = PPUObjectTile(ppu);
                tile.valid = true;
                tile.x = objectX.toUShort();
                tile.y = y.toUByte();
                tile.priority = obj.priority;
                tile.palette = (128u + (obj.palette.toUInt() shl 4)).toUByte();
                tile.hflip = obj.hflip;

                var mirrorX: UInt =
                    if (!obj.hflip) tileX.toUInt() else tileWidth - 1u - tileX.toUInt();
                var address: UInt =
                    tiledataAddress + ((characterY + (characterX + mirrorX and 15u)) shl 4);
                address = (address and 0x7ff0u) + (y and 7u);
                tile.data = ppu.vram[(address + 0u).toInt()].toUInt() shl 0;
                tile.data = tile.data or ppu.vram[(address + 8u).toInt()].toUInt() shl 16;

                if (tileCount++ >= ppu.TileLimit) break;
                tiles[(tileCount - 1u).toInt()] = tile;
            }
        }

        ppu.io.obj.rangeOver = ppu.io.obj.rangeOver || itemCount > ppu.ItemLimit;
        ppu.io.obj.timeOver = ppu.io.obj.timeOver || tileCount > ppu.TileLimit;

        var palette = UByteArray(256);
        var priority = UByteArray(256);


        for (n in 0 until (ppu.TileLimit.toInt())) {
            var tile = tiles[n];
            if (!tile.valid) continue;

            var tileX = tile.x;
            for (x in 0 until 8) {
                tileX = tileX and 511u;
                if (tileX < 256u) {
                    var color: UInt = 0u;
                    var shift: UInt = if (tile.hflip) x.toUInt() else 7u - x.toUInt();
                    color = tile.data shr (shift + 0u).toInt() and 1u;
                    color += tile.data shr (shift + 7u).toInt() and 2u;
                    color += tile.data shr (shift + 14u).toInt() and 4u;
                    color += tile.data shr (shift + 21u).toInt() and 8u;
                    if (color != 0u) {
                        palette[tileX.toInt()] = (tile.palette + color).toUByte();
                        priority[tileX.toInt()] = self.priority[tile.priority.toInt()];
                    }
                }
                tileX++;
            }
        }

        for (x in 0u until (256u)) {
            if (priority[x.toInt()].toUInt() == 0u) continue;
            var source: UByte =
                if (palette[x.toInt()] < 192u) Source.OBJ1.value else Source.OBJ2.value;
            if (self.aboveEnable && !windowAbove[x.toInt()]) plotAbove(
                x,
                source,
                priority[x.toInt()],
                cgram[palette[x.toInt()].toInt()]
            );
            if (self.belowEnable && !windowBelow[x.toInt()]) plotBelow(
                x,
                source,
                priority[x.toInt()],
                cgram[palette[x.toInt()].toInt()]
            );
        }
    }


    fun renderWindow(self : PPUIO.WindowLayer ,  enable :Boolean,  output: BooleanArray)
    {
        if (!enable || (!self.oneEnable && !self.twoEnable)) {
            for (x in 0 until 256) {
                output[x] = false
            }
            return;
        }

        if (self.oneEnable && !self.twoEnable) {
            var set : Boolean = true xor self.oneInvert;
            var  clear :Boolean  = !set;
            for ( x in 0 until 256) {
                output[x] = if (x.toUInt() >= io.window.oneLeft && x.toUInt() <= io.window.oneRight)  set else clear;
            }
            return;
        }

        if (self.twoEnable && !self.oneEnable) {
            var set :Boolean = true xor self.twoInvert
            var clear = !set;
            for ( x in 0u until (256u)) {
                output[x.toInt()] = if(x >= io.window.twoLeft && x <= io.window.twoRight)  set else clear;
            }
            return;
        }

        for ( x in 0u until 256u) {
        var oneMask : Boolean = (x >= io.window.oneLeft && x <= io.window.oneRight) xor self.oneInvert;
        var twoMask : Boolean = (x >= io.window.twoLeft && x <= io.window.twoRight) xor self.twoInvert;
        when(self.mask) {
             0u -> output[x.toInt()] = (oneMask or twoMask) ;
             1u -> output[x.toInt()] = (oneMask and twoMask) ;
             2u -> output[x.toInt()] = (oneMask xor twoMask) ;
             3u -> output[x.toInt()] = !(oneMask xor twoMask) ;
        }
    }
    }

    fun renderWindow( self :PPUIO.WindowColor,  mask :UInt,  output :BooleanArray)
    {
        var set :Boolean = false
        var clear :Boolean = false

        when(mask) {

             0u-> {
                    for (x in 0 until 255){
                        output[x] = true;
                    }
                   return;
                }  //always
             1u-> {set = true; clear = false}   //inside
             2u-> {set = false; clear = true;}  //outside
             3u-> {
                 for (x in 0 until 255){
                     output[x] = false;
                 }
                 return;
             }  //never
        }

        if (!self.oneEnable && !self.twoEnable) {
            for (x in 0 until 255){
                output[x] = clear;
            }
            return;
        }

        if (self.oneEnable && !self.twoEnable) {
            if (self.oneInvert) {set = set xor true; clear = clear xor true;}
            for ( x in 0u until 256u) {
                output[x.toInt()] = if (x >= io.window.oneLeft && x <= io.window.oneRight)  set else clear;
            }
            return;
        }

        if (self.twoEnable && !self.oneEnable) {
            if (self.twoInvert) {set = set xor true; clear = clear xor true;}
            for ( x in 0u until 256u) {
                output[x.toInt()] = if (x >= io.window.twoLeft && x <= io.window.twoRight)  set else clear;
            }
            return;
        }

        for ( x in 0u until 256u) {
        var oneMask :Boolean =(x >= io.window.oneLeft && x <= io.window.oneRight) xor self.oneInvert;
        var twoMask :Boolean =(x >= io.window.twoLeft && x <= io.window.twoRight) xor self.twoInvert;
        when(self.mask) {
            0u -> output[x.toInt()] = if((oneMask or twoMask) )  set else clear;
            1u -> output[x.toInt()] = if((oneMask and twoMask) )  set else clear;
            2u -> output[x.toInt()] = if((oneMask xor twoMask) )  set else clear;
            3u -> output[x.toInt()] = if(!(oneMask xor twoMask) )  set else clear;
        }
    }
    }


    /**
     * TODO

    //mode7hd.cpp
    static auto cacheMode7HD() -> void;
    auto renderMode7HD(PPU::IO::Background&, uint8 source) -> void;
    alwaysinline auto lerp(float pa, float va, float pb, float vb, float pr) -> float;
*/
}

private infix fun UShort.and(inv: ULong): UShort {
    return and(inv.toUShort())
}

private operator fun UShortArray.get(pixel: UShort): UShort {
    return get(pixel.toInt())
}

private operator fun <T> Array<T>.get(uInt: UInt): T {
    return get(uInt.toInt())
}

private operator fun <T> Array<T>.get(uInt: UShort): T {
    return get(uInt.toInt())
}

private operator fun <T> Array<T>.get(uInt: UByte): T {
    return get(uInt.toInt())
}
