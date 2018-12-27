package io.github.bartvhelvert.jagex.filesystem.store

import io.github.bartvhelvert.jagex.filesystem.Container
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class FileStore(directory: File) {
    private val dataChannel: DataChannel

    private val dictionaryChannels: Array<IndexChannel>

    private val attributeIndexChannel: IndexChannel

    internal val indexFileCount get() = dictionaryChannels.size

    init {
        if(!directory.isDirectory) throw IOException("$directory is not a directory or doesn't exist")
        val dataFile = directory.resolve("$FILE_NAME.$DATA_FILE_EXTENSION")
        if(!dataFile.isFile) throw IOException("$dataFile is not a file or doesn't exist")
        dataChannel = DataChannel(RandomAccessFile(dataFile, accessMode).channel)
        val indexChannelList = mutableListOf<IndexChannel>()
        for (indexFileId in 0 until ATTRIBUTE_INDEX) {
            val indexFile = directory.resolve("$FILE_NAME.$INDEX_FILE_EXTENSION$indexFileId")
            if(!indexFile.isFile) break
            indexChannelList.add(IndexChannel(RandomAccessFile(indexFile, accessMode).channel))
        }
        dictionaryChannels = indexChannelList.toTypedArray()
        val attributeFile = directory.resolve("$FILE_NAME.$INDEX_FILE_EXTENSION$ATTRIBUTE_INDEX")
        if(!attributeFile.isFile) throw IOException("$attributeFile is not a file or doesn't exist")
        attributeIndexChannel = IndexChannel(RandomAccessFile(attributeFile, accessMode).channel)
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
    internal fun write(indexFileId: Int, containerId: Int, container: Container) {
        if((indexFileId < 0 || indexFileId >= dictionaryChannels.size) && indexFileId != ATTRIBUTE_INDEX)
            throw IOException("Index file does not exist")
        val channel = if(indexFileId == ATTRIBUTE_INDEX) attributeIndexChannel else dictionaryChannels[indexFileId]
        val overwrite = channel.containsIndex(containerId)
        val segmentPos = if(overwrite) {
            channel.read(containerId).segmentPos
        } else {
            ((channel.dataSize + Segment.SIZE - 1) / Segment.SIZE).toInt()
        }
        val index = Index(container.data.limit(), segmentPos)
        channel.write(containerId, index)
    }

    companion object {
        private const val accessMode = "rw"
        private const val DATA_FILE_EXTENSION = "dat2"
        private const val INDEX_FILE_EXTENSION = "idx"
        private const val FILE_NAME = "main_file_cache"
        const val ATTRIBUTE_INDEX = 255
    }
}