/*
GNU LGPL V3
Copyright (C) 2019 Bart van Helvert
B.A.J.v.Helvert@gmail.com

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.github.bartvhelvert.jagex.fs.io

import java.io.DataOutputStream
import java.io.IOException

fun DataOutputStream.writeMedium(value: Int): DataOutputStream {
    require(value <= 16777215)
    writeShort((value shr 8))
    writeByte(value)
    return this
}

fun DataOutputStream.writeLargeSmart(value: Int): DataOutputStream {
    if(value <= Short.MAX_VALUE) {
        writeShort(value)
    } else {
        writeInt(value)
    }
    return this
}

fun DataOutputStream.writeNullableLargeSmart(value: Int?): DataOutputStream {
    when {
        value == Short.MAX_VALUE.toInt() || value == null -> writeShort(-1)
        value < Short.MAX_VALUE -> writeShort(value)
        else -> writeInt(value)
    }
    return this
}

fun DataOutputStream.writeString(string: String): DataOutputStream {
    string.forEach { char ->
        writeByte(toEncodedChar(char))
    }
    writeByte(0)
    return this
}

fun DataOutputStream.writeParams(params: HashMap<Int, Any>): DataOutputStream {
    writeByte(params.size)
    params.forEach { key, value ->
        if(value is String) writeByte(1) else writeByte(0)
        writeMedium(key)
        when (value) {
            is String -> writeString(value)
            is Int -> writeInt(value)
            else -> throw IOException("Unsupported param value type")
        }
    }
    return this
}