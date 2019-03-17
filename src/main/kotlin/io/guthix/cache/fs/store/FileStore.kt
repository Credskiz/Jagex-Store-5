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
package io.guthix.cache.fs.store

import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

class FileStore(directory: File) {
    private val dataChannel: DataChannel

    private val dictionaryChannels: Array<IndexChannel>

    private val attributeIndexChannel: IndexChannel

    internal val dictionaryCount get() = dictionaryChannels.size

    init {
        if(!directory.isDirectory) throw IOException("$directory is not a directory or doesn't exist.")
        val dataFile = directory.resolve("$FILE_NAME.$DATA_FILE_EXTENSION")
        if(!dataFile.isFile) {
            logger.info("Could not find .dat2 file")
            if(!dataFile.createNewFile()) {
                throw IOException("Could not create .dat2 file")
            } else {
                logger.info("Created empty .dat2 file")
            }
        } else {
            logger.info("Found .dat2 file")
        }
        dataChannel = DataChannel(
            RandomAccessFile(
                dataFile,
                accessMode
            ).channel
        )
        val indexChannelList = mutableListOf<IndexChannel>()
        for (indexFileId in 0 until ATTRIBUTE_INDEX) {
            val indexFile = directory.resolve("$FILE_NAME.$INDEX_FILE_EXTENSION$indexFileId")
            if(!indexFile.isFile)  {
                logger.info("Found $indexFileId index ${if(indexFileId == 1) "file " else "files"}")
                break
            }
            indexChannelList.add(
                IndexChannel(
                    RandomAccessFile(
                        indexFile,
                        accessMode
                    ).channel
                )
            )
        }
        dictionaryChannels = indexChannelList.toTypedArray()
        val attributeFile = directory.resolve("$FILE_NAME.$INDEX_FILE_EXTENSION$ATTRIBUTE_INDEX")
        if(!attributeFile.isFile) {
            logger.info("Could not find .idx255 file")
            if(!attributeFile.createNewFile()) {
                throw IOException("Could not create .idx255 file")
            } else {
                logger.info("Created empty .idx255 file")
            }
        } else {
            logger.info("Found .idx255 file:")
        }
        attributeIndexChannel = IndexChannel(
            RandomAccessFile(
                attributeFile,
                accessMode
            ).channel
        )
    }

    @ExperimentalUnsignedTypes
    internal fun read(indexFileId: Int, containerId: Int): ByteBuffer {
        if((indexFileId < 0 || indexFileId >= dictionaryChannels.size) && indexFileId != ATTRIBUTE_INDEX)
            throw IOException("Index file does not exist")
        val index = if(indexFileId == ATTRIBUTE_INDEX) {
            attributeIndexChannel.read(containerId)
        } else {
            dictionaryChannels[indexFileId].read(containerId)
        }
        return dataChannel.read(indexFileId, index, containerId)
    }

    @ExperimentalUnsignedTypes
    internal fun write(indexFileId: Int, containerId: Int, data: ByteBuffer) {
        if((indexFileId < 0 || indexFileId >= dictionaryChannels.size) && indexFileId != ATTRIBUTE_INDEX)
            throw IOException("Index file does not exist")
        val indexChannel = if(indexFileId == ATTRIBUTE_INDEX) attributeIndexChannel else dictionaryChannels[indexFileId]
        val overwriteIndex= indexChannel.containsIndex(containerId)
        val firstSegmentPos = if(overwriteIndex) {
            indexChannel.read(containerId).segmentPos
        } else {
            (indexChannel.dataSize / Segment.SIZE).toInt()
        }
        val index = Index(data.limit(), firstSegmentPos)
        indexChannel.write(containerId, index)
        dataChannel.write(indexFileId, containerId, index, data)
    }

    companion object {
        private const val accessMode = "rw"
        private const val DATA_FILE_EXTENSION = "dat2"
        private const val INDEX_FILE_EXTENSION = "idx"
        private const val FILE_NAME = "main_file_cache"
        const val ATTRIBUTE_INDEX = 255
    }
}