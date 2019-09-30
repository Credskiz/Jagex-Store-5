/*
 * Copyright (C) 2019 Guthix
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.js5.container

import io.guthix.cache.js5.iterationFill
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.netty.buffer.Unpooled

class Js5ContainerTest : StringSpec({
    val data = Unpooled.buffer(5871).iterationFill()
    "After encoding and decoding an uncompressed container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), compression = Uncompressed()).encode()).data shouldBe data
    }

    "After encoding and decoding a BZIP2 compressed container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), compression = BZIP2()).encode()).data shouldBe data
    }

    "After encoding and decoding a GZIP compressed container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), compression = GZIP()).encode()).data shouldBe data
    }

    "After encoding and decoding a LZMA compressed container it should be the same as the original" {
        val lzma = LZMA().apply {
            header = Unpooled.buffer().apply { // write LZMA header
                writeByte(93)
                writeByte(0)
                writeByte(0)
                writeByte(64)
                writeByte(0)
            }
        }
        Js5Container.decode(Js5Container(data.copy(), compression = lzma).encode()).data shouldBe data
    }

    "After encoding and decoding a XTEA encrypted container it should be the same as the original" {
        val xteaKey = intArrayOf(3028, 927, 0, 658)
        Js5Container.decode(Js5Container(data.copy(), xteaKey = xteaKey).encode(), xteaKey = xteaKey).data shouldBe data
    }
})