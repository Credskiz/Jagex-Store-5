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
package io.guthix.cache.js5.io.bytebufferextensions

import io.guthix.cache.js5.io.medium
import io.guthix.cache.js5.io.putMedium
import io.guthix.cache.js5.io.uMedium
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediumTest {
    @ParameterizedTest
    @MethodSource("mediumTestValues")
    fun `medium put get`(input: Int, expected: Int) {
        val buffer = ByteBuffer.allocate(3).putMedium(input).flip() as ByteBuffer
        val mediumNumber = buffer.medium
        Assertions.assertEquals(expected, mediumNumber)
    }

    @ParameterizedTest
    @MethodSource("unsignedMediumTestValues")
    fun `unsigned medium put get`(input: Int, expected: Int) {
        val buffer = ByteBuffer.allocate(3).putMedium(input).flip() as ByteBuffer
        val mediumNumber = buffer.uMedium
        Assertions.assertEquals(expected, mediumNumber)
    }

    companion object {
        @JvmStatic
        fun mediumTestValues() = listOf(
            Arguments.of(0, 0),
            Arguments.of(10, 10),
            Arguments.of(16777215, -1),
            Arguments.of(-1, -1),
            Arguments.of(-10, -10),
            Arguments.of(8388607, 8388607)
        )

        @JvmStatic
        fun unsignedMediumTestValues() = listOf(
            Arguments.of(0, 0),
            Arguments.of(10, 10),
            Arguments.of(16777215, 16777215),
            Arguments.of(-1, 16777215),
            Arguments.of(-10, 16777206),
            Arguments.of(8388607, 8388607)
        )
    }
}