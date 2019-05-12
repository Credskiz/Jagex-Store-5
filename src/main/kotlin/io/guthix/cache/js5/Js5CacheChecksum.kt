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

import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.util.rsaCrypt
import io.guthix.cache.js5.util.whirlPoolHash
import io.guthix.cache.js5.util.WP_HASH_BYTE_COUNT
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer

data class Js5CacheChecksum(val archiveChecksums: Array<ArchiveChecksum>) {
    fun encode(whirlpool: Boolean = false, mod: BigInteger? = null, pubKey: BigInteger? = null): ByteBuffer {
        val buffer = ByteBuffer.allocate(if(whirlpool)
            WP_ENCODED_SIZE + ArchiveChecksum.WP_ENCODED_SIZE * archiveChecksums.size
        else
            ArchiveChecksum.ENCODED_SIZE * archiveChecksums.size
        )
        if(whirlpool) buffer.put(archiveChecksums.size.toByte())
        for(indexFileChecksum in archiveChecksums) {
            buffer.putInt(indexFileChecksum.crc)
            buffer.putInt(indexFileChecksum.version)
            if(whirlpool) {
                buffer.putInt(indexFileChecksum.fileCount)
                buffer.putInt(indexFileChecksum.size)
                buffer.put(indexFileChecksum.whirlpoolDigest)
            }
        }
        if(whirlpool) {
            val hash = if(mod != null && pubKey != null) {
                rsaCrypt(
                    whirlPoolHash(buffer.array().sliceArray(1 until buffer.position())),
                    mod,
                    pubKey
                )
            } else {
                whirlPoolHash(buffer.array().sliceArray(1 until buffer.position()))
            }
            buffer.put(hash)
        }
        return buffer.flip()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5CacheChecksum
        if (!archiveChecksums.contentEquals(other.archiveChecksums)) return false
        return true
    }

    override fun hashCode(): Int {
        return archiveChecksums.contentHashCode()
    }

    companion object {
        const val WP_ENCODED_SIZE = WP_HASH_BYTE_COUNT + 1

        @ExperimentalUnsignedTypes
        fun decode(buffer: ByteBuffer, whirlpool: Boolean, mod: BigInteger?, privateKey: BigInteger?): Js5CacheChecksum {
            val indexFileCount = if (whirlpool) {
                buffer.uByte.toInt()
            } else {
                buffer.limit() / ArchiveChecksum.ENCODED_SIZE
            }
            val indexFileEncodedSize = if(whirlpool) {
                ArchiveChecksum.WP_ENCODED_SIZE * indexFileCount
            } else {
                ArchiveChecksum.ENCODED_SIZE * indexFileCount
            }
            val indexFileEncodedStart = if (whirlpool) 1 else 0
            val calculatedDigest = whirlPoolHash(
                buffer.array().sliceArray(indexFileEncodedStart until indexFileEncodedStart + indexFileEncodedSize)
            )
            val indexFileChecksums = Array(indexFileCount) {
                val crc = buffer.int
                val version = buffer.int
                val fileCount = if (whirlpool) buffer.int else 0
                val indexFileSize = if (whirlpool) buffer.int else 0
                val whirlPoolDigest = if (whirlpool) {
                    val digest = ByteArray(WP_HASH_BYTE_COUNT)
                    buffer.get(digest)
                    digest
                } else null
                ArchiveChecksum(crc, version, fileCount, indexFileSize, whirlPoolDigest)
            }
            if (whirlpool) {
                val hash= if (mod != null && privateKey != null) {
                    rsaCrypt(
                        buffer.array().sliceArray(buffer.position() until buffer.position() + buffer.remaining()),
                        mod,
                        privateKey
                    )
                } else {
                    buffer.array().sliceArray(buffer.position() until buffer.position() + buffer.remaining())
                }
                if (!hash!!.contentEquals(calculatedDigest)) throw IOException("Whirlpool digest does not match.")
            }
            return Js5CacheChecksum(indexFileChecksums)
        }
    }
}

data class ArchiveChecksum(
    val crc: Int,
    val version: Int,
    val fileCount: Int,
    val size: Int,
    val whirlpoolDigest: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArchiveChecksum
        if (crc != other.crc) return false
        if (version != other.version) return false
        if (fileCount != other.fileCount) return false
        if (size != other.size) return false
        if (whirlpoolDigest != null) {
            if (other.whirlpoolDigest == null) return false
            if (!whirlpoolDigest.contentEquals(other.whirlpoolDigest)) return false
        } else if (other.whirlpoolDigest != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = crc
        result = 31 * result + version
        result = 31 * result + fileCount
        result = 31 * result + size
        result = 31 * result + (whirlpoolDigest?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        internal const val ENCODED_SIZE = 8
        internal const val WP_ENCODED_SIZE = ENCODED_SIZE + WP_HASH_BYTE_COUNT + 8
    }
}
