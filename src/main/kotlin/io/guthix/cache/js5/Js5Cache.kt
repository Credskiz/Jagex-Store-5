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

import io.guthix.cache.js5.container.ContainerReader
import io.guthix.cache.js5.container.ContainerWriter
import io.guthix.cache.js5.container.Container
import io.guthix.cache.js5.container.ContainerReaderWriter
import io.guthix.cache.js5.container.filesystem.Js5FileSystem
import io.guthix.cache.js5.util.*
import mu.KotlinLogging
import java.io.IOException
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

open class Js5Cache(
    private val reader: ContainerReader,
    private val writer: ContainerWriter,
    private val settingsXtea: MutableMap<Int, IntArray> = mutableMapOf()
) : AutoCloseable {
    constructor(
        readerWriter: ContainerReaderWriter,
        settingsXtea: MutableMap<Int, IntArray> = mutableMapOf()
    ) : this(reader = readerWriter, writer = readerWriter, settingsXtea = settingsXtea)

    protected val archiveSettings = MutableList(reader.archiveCount) {
        Js5ArchiveSettings.decode(
            Container.decode(
                reader.read(
                    Js5FileSystem.MASTER_INDEX,
                    it
                ),
                settingsXtea[it] ?: XTEA_ZERO_KEY
            )
        )
    }

    init {
        logger.info("Loaded cache with ${archiveSettings.size} archives")
    }

    fun groupIds(archiveId: Int) = getArchiveSettings(archiveId).js5GroupSettings.keys

    fun fileIds(archiveId: Int, groupId: Int) =
        getArchiveSettings(archiveId).js5GroupSettings[groupId]?.fileSettings?.keys

    open fun readData(indexId: Int, containerId: Int): ByteBuffer = reader.read(indexId, containerId)

    open fun readGroup(
        archiveId: Int,
        groupId: Int,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val archiveSettings = getArchiveSettings(archiveId)
        val groupSettings = archiveSettings.js5GroupSettings[groupId] ?: throw IOException("Js5Group does not exist.")
        val groupContainer = Container.decode(readData(archiveId, groupId), xteaKey)
        logger.info("Reading group $groupId from archive $archiveId")
        return Js5Group.decode(groupContainer, groupSettings)
    }

    open fun readGroup(
        archiveId: Int,
        groupName: String,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val archiveSettings = getArchiveSettings(archiveId)
        val nameHash = groupName.hashCode()
        val groupSettings =  archiveSettings.js5GroupSettings.values.first { it.nameHash == nameHash }
        logger.info("Reading group ${groupSettings.id} from archive $archiveId")
        val groupContainer = Container.decode(readData(archiveId, groupSettings.id), xteaKey)
        return Js5Group.decode(groupContainer, groupSettings)
    }

    open fun readArchive(
        archiveId: Int,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Map<Int, Js5Group> {
        val archiveSettings = getArchiveSettings(archiveId)
        val groups = mutableMapOf<Int, Js5Group>()
        archiveSettings.js5GroupSettings.forEach { (groupId, groupSettings) ->
            val xtea = xteaKeys[groupId] ?: XTEA_ZERO_KEY
            logger.info("Reading group ${groupSettings.id} from archive $archiveId")
            val groupContainer = Container.decode(readData(archiveId, groupId), xtea)
            groups[groupId] = Js5Group.decode(groupContainer, groupSettings)
        }
        return groups
    }

    open fun writeGroup(
        archiveId: Int,
        group: Js5Group,
        groupSegmentCount: Int = 1,
        groupVersion: Int = -1,
        groupArchiveSettingsVersion: Int = -1,
        groupXteaKey: IntArray = XTEA_ZERO_KEY,
        groupSettingsXteaKey: IntArray = XTEA_ZERO_KEY,
        groupCompression: Js5Compression = Js5Compression.NONE,
        groupSettingsCompression: Js5Compression = Js5Compression.NONE
    ) {
        if(archiveId > writer.archiveCount) throw IOException(
            "Can not create archive with id $archiveId expected: ${writer.archiveCount}."
        )
        logger.info("Writing group ${group.id} from archive $archiveId")
        val compressedSize = writeGroupData(
            archiveId, group, groupSegmentCount, groupVersion, groupXteaKey, groupCompression
        )
        if(group.sizes == null) {
            group.sizes = Js5GroupSettings.Size(compressedSize, group.files.values.sumBy { it.data.limit() })
        }
        writeGroupSettings(
            archiveId,
            group,
            groupArchiveSettingsVersion,
            groupSettingsXteaKey,
            groupSettingsCompression
        )
    }

    private fun writeGroupData(
        archiveId: Int,
        group: Js5Group,
        segmentCount: Int = 1,
        dataVersion: Int = -1,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Js5Compression.NONE
    ): Int {
        logger.info("Writing group data for group ${group.id} from archive $archiveId")
        val versionedData = group.encode(segmentCount, dataVersion)
        val data = versionedData.encode(compression, xteaKey)
        writer.write(archiveId, group.id, data)
        return data.limit()
    }

    private fun writeGroupSettings(
        archiveId: Int,
        group: Js5Group,
        archiveSettingsVersion: Int = -1,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Js5Compression.NONE
    ) {
        logger.info("Writing group settings for group ${group.id} from archive $archiveId")
        val archiveSettings = if(archiveId == archiveSettings.size) {
            val settings = Js5ArchiveSettings(0, mutableMapOf())
            archiveSettings.add(archiveId, settings)
            settings
        } else {
            getArchiveSettings(archiveId)
        }
        val fileSettings = mutableMapOf<Int, Js5FileSettings>()
        group.files.forEach { (id, file) ->
            fileSettings[id] = Js5FileSettings(id, file.nameHash)
        }
        if(!xteaKey.contentEquals(XTEA_ZERO_KEY)) settingsXtea[archiveId] = xteaKey
        archiveSettings.version = archiveSettingsVersion
        archiveSettings.js5GroupSettings[group.id] = Js5GroupSettings(
            group.id,
            group.nameHash,
            group.crc,
            group.unknownHash,
            group.whirlpoolHash,
            group.sizes,
            group.version,
            fileSettings
        )
        writer.write(
            Js5FileSystem.MASTER_INDEX,
            archiveId,
            archiveSettings.encode().encode(compression, xteaKey)
        )
    }

    fun generateChecksum(): Js5CacheChecksum {
        logger.info("Generating cache checksum")
        return Js5CacheChecksum(
            Array(archiveSettings.size) { archiveId ->
                val archiveSettings = archiveSettings[archiveId]
                val settingsData = reader.read(Js5FileSystem.MASTER_INDEX, archiveId)
                ArchiveChecksum(
                    settingsData.crc(),
                    archiveSettings.version,
                    archiveSettings.js5GroupSettings.size,
                    archiveSettings.js5GroupSettings.values
                        .sumBy { if(it.sizes?.compressed != null) it.sizes.uncompressed else 0 },
                    whirlPoolHash(settingsData.array())
                )
            }
        )
    }

    private fun getArchiveSettings(archiveId: Int): Js5ArchiveSettings {
        if(archiveId !in 0..archiveSettings.size) throw IOException("Archive does not exist.")
        return archiveSettings[archiveId]
    }

    override fun close() {
        reader.close()
        writer.close()
    }
}
