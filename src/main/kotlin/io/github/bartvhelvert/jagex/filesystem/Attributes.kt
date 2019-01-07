package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.io.smart
import io.github.bartvhelvert.jagex.filesystem.io.uByte
import io.github.bartvhelvert.jagex.filesystem.io.uShort
import io.github.bartvhelvert.jagex.filesystem.io.writeSmart
import io.github.bartvhelvert.jagex.filesystem.transform.whirlPoolHashByteCount
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

data class DictionaryAttributes(val version: Int, val archiveAttributes: MutableMap<Int, ArchiveAttributes>) {
    internal fun encode(format: Format): ByteBuffer {
        val byteStr = ByteArrayOutputStream()
        DataOutputStream(byteStr).use { os ->
            os.writeByte(format.opcode)
            if(format != Format.UNVERSIONED) os.writeInt(version)
            var flags = 0
            val hasNameHashes = archiveAttributes.values.any { attr ->
                attr.nameHash != null || attr.fileAttributes.values.any { file -> file.nameHash != null }
            }
            val hasUnknownHashes = archiveAttributes.values.any { it.unknownHash != null }
            val hasWhirlPoolHashes = archiveAttributes.values.any { it.whirlpoolHash != null }
            val hasSizes = archiveAttributes.values.any { it.sizes != null }
            if(hasNameHashes) flags = flags or MASK_NAME_HASH
            if(hasUnknownHashes) flags = flags or MASK_UNKNOWN_HASH
            if(hasWhirlPoolHashes) flags = flags or MASK_WHIRLPOOL_HASH
            if(hasSizes) flags = flags or MASK_SIZES
            os.writeByte(flags)
            if(format == Format.VERSIONEDLARGE) {
                os.writeSmart(archiveAttributes.size)
            } else {
                os.writeShort(archiveAttributes.size)
            }
            var prevArchiveId = 0
            for(id in archiveAttributes.keys) {
                val delta = Math.abs(prevArchiveId - id)
                if(format == Format.VERSIONEDLARGE) os.writeSmart(delta) else os.writeShort(delta)
                prevArchiveId = id
            }
            if(hasNameHashes) {
                for(attr in archiveAttributes.values) os.writeInt(attr.nameHash ?: 0)
            }
            for(attr in archiveAttributes.values) os.writeInt(attr.crc)
            if(hasUnknownHashes) {
                for(attr in archiveAttributes.values) os.writeInt(attr.unknownHash ?: 0)
            }
            if(hasWhirlPoolHashes) {
                for(attr in archiveAttributes.values) {
                    os.write(attr.whirlpoolHash ?: ByteArray(whirlPoolHashByteCount))
                }
            }
            if(hasSizes) {
                for(attr in archiveAttributes.values) {
                    os.writeInt(attr.sizes?.compressed ?: 0)
                    os.writeInt(attr.sizes?.uncompressed ?: 0)
                }
            }
            for(attr in archiveAttributes.values) {
                os.writeInt(attr.version)
            }
            for(attr in archiveAttributes.values) {
                if(format == Format.VERSIONEDLARGE) {
                    os.writeSmart(attr.fileAttributes.size)
                } else {
                    os.writeShort(attr.fileAttributes.size)
                }
            }
            for(attr in archiveAttributes.values) {
                var prevFileId = 0
                for(id in attr.fileAttributes.keys) {
                    val delta = Math.abs(prevFileId - id)
                    if(format == Format.VERSIONEDLARGE) {
                        os.writeSmart(delta)
                    } else {
                        os.writeShort(delta)
                    }
                    prevFileId = id
                }
            }
            if(hasNameHashes) {
                for(attr in archiveAttributes.values) {
                    for(file in attr.fileAttributes.values) {
                        os.writeInt(file.nameHash ?: 0)
                    }
                }
            }
        }
        return ByteBuffer.wrap(byteStr.toByteArray())
    }

    enum class Format(val opcode: Int) { UNVERSIONED(5), VERSIONED(6), VERSIONEDLARGE(7) }

    companion object {
        private const val MASK_NAME_HASH = 0x01
        private const val MASK_WHIRLPOOL_HASH = 0x02
        private const val MASK_SIZES = 0x04
        private const val MASK_UNKNOWN_HASH = 0x08

        @ExperimentalUnsignedTypes
        internal fun decode(container: Container): DictionaryAttributes {
            val buffer = container.data
            val formatOpcode = buffer.uByte.toInt()
            val format = Format.values().find { it.opcode == formatOpcode }
            require(format != null)
            val version = if (format == Format.UNVERSIONED) 0 else buffer.int
            val flags = buffer.uByte.toInt()
            val archiveCount = if (format == Format.VERSIONEDLARGE) buffer.smart else buffer.uShort.toInt()
            val archiveIds = IntArray(archiveCount)
            var archiveIdAccumulator = 0
            for(archiveIndex in archiveIds.indices) {
                val delta = if (format == Format.VERSIONEDLARGE) buffer.smart else buffer.uShort.toInt() // difference with previous id
                archiveIdAccumulator += delta
                archiveIds[archiveIndex] = archiveIdAccumulator
            }
            val archiveNameHashes = if (flags and MASK_NAME_HASH != 0) IntArray(archiveCount) {
                buffer.int
            } else null
            val archiveCRCs = IntArray(archiveCount) { buffer.int }
            val archiveUnkownHashes = if (flags and MASK_UNKNOWN_HASH != 0) IntArray(archiveCount) {
                buffer.int
            } else null
            val archiveWhirlpoolHashes = if (flags and MASK_WHIRLPOOL_HASH != 0) Array(archiveCount) {
                ByteArray(whirlPoolHashByteCount) { buffer.get() }
            } else null
            val archiveSizes = if(flags and MASK_SIZES != 0) Array(archiveCount) {
                ArchiveAttributes.Size(compressed = buffer.int, uncompressed = buffer.int)
            } else null
            val archiveVersions = Array(archiveCount) { buffer.int }
            val archiveFileIds = Array(archiveCount) {
                IntArray(if (format == Format.VERSIONEDLARGE) buffer.smart else buffer.uShort.toInt()) // decode file count
            }
            for(archive in archiveFileIds) {
                var fileIdAccumulator = 0
                for(fileIndex in archive.indices) {
                    val delta = if (format == Format.VERSIONEDLARGE) buffer.smart else buffer.uShort.toInt() // difference with previous id
                    fileIdAccumulator += delta
                    archive[fileIndex] = fileIdAccumulator
                }
            }
            val archiveFileNameHashes = if (flags and MASK_NAME_HASH != 0) {
                Array(archiveCount) {
                    IntArray(archiveFileIds[it].size) {
                        buffer.int
                    }
                }
            } else null

            val archiveAttributes = mutableMapOf<Int, ArchiveAttributes>()
            for(archiveIndex in archiveIds.indices) {
                val fileAttributes = mutableMapOf<Int, FileAttributes>()
                for(fileIndex in archiveFileIds[archiveIndex].indices) {
                    fileAttributes[archiveFileIds[archiveIndex][fileIndex]] = FileAttributes(
                        archiveFileIds[archiveIndex][fileIndex],
                        archiveFileNameHashes?.get(archiveIndex)?.get(fileIndex)
                    )
                }
                archiveAttributes[archiveIds[archiveIndex]] = ArchiveAttributes(
                    archiveIds[archiveIndex],
                    archiveNameHashes?.get(archiveIndex),
                    archiveCRCs[archiveIndex],
                    archiveUnkownHashes?.get(archiveIndex),
                    archiveWhirlpoolHashes?.get(archiveIndex),
                    archiveSizes?.get(archiveIndex),
                    archiveVersions[archiveIndex],
                    fileAttributes
                )
            }
            return DictionaryAttributes(version, archiveAttributes)
        }
    }
}

data class ArchiveAttributes(
    val id: Int,
    val nameHash: Int?,
    val crc: Int,
    val unknownHash: Int?,
    val whirlpoolHash: ByteArray?,
    val sizes: Size?,
    val version: Int,
    val fileAttributes: MutableMap<Int, FileAttributes>

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArchiveAttributes
        if (id != other.id) return false
        if (nameHash != other.nameHash) return false
        if (crc != other.crc) return false
        if (unknownHash != other.unknownHash) return false
        if (whirlpoolHash != null) {
            if (other.whirlpoolHash == null) return false
            if (!whirlpoolHash.contentEquals(other.whirlpoolHash)) return false
        } else if (other.whirlpoolHash != null) return false
        if (sizes != other.sizes) return false
        if (version != other.version) return false
        if (fileAttributes != other.fileAttributes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + (nameHash ?: 0)
        result = 31 * result + crc
        result = 31 * result + (unknownHash ?: 0)
        result = 31 * result + (whirlpoolHash?.contentHashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        result = 31 * result + version
        result = 31 * result + fileAttributes.hashCode()
        return result
    }

    class Size(val compressed: Int, val uncompressed: Int)
}

data class FileAttributes(val id: Int, val nameHash: Int?)