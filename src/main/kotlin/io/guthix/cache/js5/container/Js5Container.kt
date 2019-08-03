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

import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.util.*
import io.guthix.cache.js5.util.xteaDecrypt
import io.guthix.cache.js5.util.xteaEncrypt
import java.io.IOException
import java.nio.ByteBuffer

interface ContainerReaderWriter : ContainerReader, ContainerWriter

interface ContainerReader : AutoCloseable {
    val archiveCount: Int

    fun read(indexFileId: Int, containerId: Int): ByteBuffer
}

interface ContainerWriter : AutoCloseable {
    val archiveCount: Int

    fun write(indexFileId: Int, containerId: Int, data: ByteBuffer)
}

data class Container(var version: Int = -1, val data: ByteBuffer) {
    fun encode(js5Compression: Js5Compression, xteaKey: IntArray = XTEA_ZERO_KEY): ByteBuffer {
        require(xteaKey.size == XTEA_KEY_SIZE)
        val compressedData = js5Compression.compress(data.array())
        val buffer = ByteBuffer.allocate(
            ENC_HEADER_SIZE + js5Compression.headerSize + compressedData.size + if(isVersioned) 2 else 0
        )
        buffer.put(js5Compression.opcode.toByte())
        buffer.putInt(compressedData.size)
        if(js5Compression != Js5Compression.NONE) buffer.putInt(data.limit())
        buffer.put(compressedData)
        if(isVersioned) buffer.putShort(version.toShort())
        return if(xteaKey.all { it != 0 }) {
            buffer.xteaEncrypt(
                key = xteaKey,
                start = ENC_HEADER_SIZE,
                end = ENC_HEADER_SIZE + js5Compression.headerSize + compressedData.size
            ).flip()
        } else buffer.flip()
    }

    val isVersioned get() = version != -1

    fun removeVersion() {
        version = -1
    }

    companion object {
        const val ENC_HEADER_SIZE = Int.SIZE_BYTES + Byte.SIZE_BYTES
        const val COMPRESSION_HEADER_SIZE = Int.SIZE_BYTES

        fun decode(buffer: ByteBuffer, xteaKey: IntArray = XTEA_ZERO_KEY): Container {
            require(xteaKey.size == XTEA_KEY_SIZE)
            val compression = Js5Compression.getByOpcode(buffer.uByte.toInt())
            val compressedSize = buffer.int
            if(xteaKey.all { it != 0 }) {
                buffer.xteaDecrypt(
                    key = xteaKey,
                    start = ENC_HEADER_SIZE,
                    end = ENC_HEADER_SIZE + compression.headerSize + compressedSize
                )
            }
            val dataBuffer = if(compression != Js5Compression.NONE) {
                val uncompressedSize = buffer.int
                val headerLength = ENC_HEADER_SIZE + compression.headerSize
                val uncompressed = compression.decompress(
                    buffer.array().sliceArray(headerLength until headerLength + compressedSize), uncompressedSize
                )
                if (uncompressed.size != uncompressedSize) throw IOException("Compression size mismatch.")
                ByteBuffer.wrap(uncompressed)
            } else ByteBuffer.wrap(buffer.array().sliceArray(
                ENC_HEADER_SIZE until ENC_HEADER_SIZE + compressedSize
            ))
            buffer.position(ENC_HEADER_SIZE + compression.headerSize + compressedSize)
            val version = if(buffer.remaining() >= 2) buffer.short.toInt() else -1
            return Container(version, dataBuffer)
        }
    }
}
