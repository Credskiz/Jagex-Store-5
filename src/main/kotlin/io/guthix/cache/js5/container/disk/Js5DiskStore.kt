/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.container.disk

import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Js5Store
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * A [Js5DiskStore] used to read and write [Js5Container] data to and from disk a store on disk.
 *
 * @property root The root folder containing the cache data.
 * @property dat2File The [Dat2File] containing the cache data.
 * @property indexFiles The index files
 * @property archiveCount The amount of archives in the [Js5DiskStore].
 */
public class Js5DiskStore private constructor(
    private val root: Path,
    private val dat2File: Dat2File,
    private val indexFiles: MutableMap<Int, IdxFile>,
    override var archiveCount: Int
) : Js5Store {
    override fun read(indexId: Int, containerId: Int): ByteBuf {
        require(indexId in 0 until archiveCount || indexId == Js5Store.MASTER_INDEX) {
            "Can't read data because $FILE_NAME.${IdxFile.EXTENSION}${indexId} does not exist."
        }
        val indexFile = indexFiles.getOrPut(indexId, { openIndexFile(indexId) })
        val index = indexFile.read(containerId)
        if (index.dataSize == 0) {
            logger.warn {
                "Could not read index file ${indexFile.id} container $containerId because the index does not exist"
            }
            return Unpooled.EMPTY_BUFFER
        } else {
            logger.debug { "Reading index file ${indexFile.id} container $containerId" }
        }
        return dat2File.read(indexFile.id, containerId, index)
    }

    override fun write(indexId: Int, containerId: Int, data: ByteBuf) {
        require(indexId in 0..archiveCount || indexId == Js5Store.MASTER_INDEX) {
            "Can't write data because $FILE_NAME.${IdxFile.EXTENSION}${indexId} does not exist and can't be created."
        }
        logger.debug { "Writing index file $indexId container $containerId" }
        val indexFile = if (indexId == archiveCount) {
            createNewArchive()
        } else {
            indexFiles.getOrPut(indexId, { openIndexFile(indexId) })
        }
        val overWriteIndex = indexFile.containsIndex(containerId)
        val firstSegNumber = if (overWriteIndex) {
            indexFile.read(containerId).sectorNumber
        } else {
            ceil(dat2File.size.toDouble() / Sector.SIZE).toInt() // last sector of the data file
        }
        val index = Index(data.readableBytes(), firstSegNumber)
        indexFile.write(containerId, index)
        dat2File.write(indexFile.id, containerId, index, data)
    }

    /**
     * Removes an [Index] from the [Js5DiskStore]. Note that this method does not remove the actual data but only the
     * reference to the data. It is recommended to defragment the cache after calling this method.
     */
    override fun remove(indexId: Int, containerId: Int) {
        require(indexId in 0 until archiveCount || indexId == Js5Store.MASTER_INDEX) {
            "Can't remove data because $FILE_NAME.${IdxFile.EXTENSION}${indexId} does not exist."
        }
        val indexFile = indexFiles.getOrPut(indexId, { openIndexFile(indexId) })
        logger.debug { "Removing index file ${indexFile.id} container $containerId" }
        indexFile.remove(containerId)
    }

    /**
     * Creates a new archive [IdxFile] in this [Js5DiskStore].
     */
    private fun createNewArchive(): IdxFile {
        val file = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$archiveCount")
        logger.debug { "Created index file ${file.fileName}" }
        Files.createFile(file)
        val indexFile = IdxFile.open(archiveCount, file)
        indexFiles[archiveCount++] = indexFile
        return indexFile
    }

    /**
     * Opens an [IdxFile] in this [Js5DiskStore].
     */
    private fun openIndexFile(indexFileId: Int) = IdxFile.open(
        indexFileId, root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
    )

    override fun close() {
        logger.debug { "Closing Js5DiskStore at $root" }
        dat2File.close()
        indexFiles.values.forEach { it.close() }
    }

    public companion object {
        /**
         * The default cache file name.
         */
        private const val FILE_NAME = "main_file_cache"

        /**
         * Opens a [Js5DiskStore].
         *
         * @param root The folder where the cache is located.
         */
        public fun open(root: Path): Js5DiskStore {
            require(Files.isDirectory(root)) { "$root is not a directory or doesn't exist." }
            val dataPath = root.resolve("$FILE_NAME.${Dat2File.EXTENSION}")
            if (Files.exists(dataPath)) {
                logger.debug { "Found .dat2 file" }
            } else {
                logger.debug { "Could not find .dat2 file\nCreated empty .dat2 file" }
                Files.createFile(dataPath)
                logger.debug { "Created empty .dat2 file\"" }
            }
            val dataFile = Dat2File.open(dataPath)
            val masterIndexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}${Js5Store.MASTER_INDEX}")
            if (Files.exists(masterIndexPath)) {
                logger.debug { "Found .idx255 file" }
            } else {
                logger.debug { "Could not find .idx255 file" }
                Files.createFile(masterIndexPath)
                logger.debug { "Created empty .idx255 file" }
            }
            val masterIndexFile = IdxFile.open(Js5Store.MASTER_INDEX, masterIndexPath)
            var archiveCount = 0
            for (indexFileId in 0 until Js5Store.MASTER_INDEX) {
                val indexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
                if (!Files.exists(indexPath)) {
                    archiveCount = indexFileId
                    break
                }
            }
            logger.debug { "Created disk store with archive count $archiveCount" }
            return Js5DiskStore(root, dataFile, mutableMapOf(Js5Store.MASTER_INDEX to masterIndexFile), archiveCount)
        }
    }
}