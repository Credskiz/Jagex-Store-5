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
package io.guthix.cache.js5

import io.guthix.cache.js5.util.WHIRLPOOL_HASH_SIZE
import io.kotlintest.specs.StringSpec

class Js5CacheChecksumTest : StringSpec({
    val checksum = Js5CacheChecksum(arrayOf(
        Js5ArchiveChecksum(
            crc = 32493,
            version = 3893,
            fileCount = 10,
            uncompressedSize = 39,
            whirlpoolDigest = null
        ),
        Js5ArchiveChecksum(
            crc = 642,
            version = 34,
            fileCount = 1,
            uncompressedSize = 214,
            whirlpoolDigest = null
        )
    ))
    "After encoding and decoding the checksum should be the same as the original" {
        Js5CacheChecksum.decode(checksum.encode())
    }

    checksum.archiveChecksums.forEach { it.whirlpoolDigest = ByteArray(WHIRLPOOL_HASH_SIZE) }
    "After encoding and decoding the checksum with whirlpool it should be the same as the original" {
        Js5CacheChecksum.decode(checksum.encode(), whirlpool = true)
    }
})