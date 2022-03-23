package com.ustadmobile.retriever.ext

import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

val File.crc32 : Long
    get() {
        val crc = CRC32()
        crc.update(readBytes())
        return crc.value
    }

val File.sha384: ByteArray
    get() {
        val digest = MessageDigest.getInstance("SHA-384")
        digest.update(readBytes())
        return digest.digest()
    }
