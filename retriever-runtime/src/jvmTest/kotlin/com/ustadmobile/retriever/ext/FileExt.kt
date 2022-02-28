package com.ustadmobile.retriever.ext

import java.io.File
import java.util.zip.CRC32

val File.crc32 : Long
    get() {
        val crc = CRC32()
        crc.update(readBytes())
        return crc.value
    }
