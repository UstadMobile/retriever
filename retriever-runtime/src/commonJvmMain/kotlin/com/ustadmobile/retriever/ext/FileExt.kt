package com.ustadmobile.retriever.ext

import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.io.FileChecksums
import com.ustadmobile.retriever.io.MultiDigestOutputStream
import com.ustadmobile.retriever.io.MultiDigestOutputStream.Companion.INDEX_SHA256
import com.ustadmobile.retriever.io.MultiDigestOutputStream.Companion.INDEX_SHA384
import com.ustadmobile.retriever.io.MultiDigestOutputStream.Companion.INDEX_SHA512
import com.ustadmobile.retriever.io.NullOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import java.io.FileInputStream
import java.util.*
import kotlinx.coroutines.runBlocking

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

suspend fun File.fileChecksums() : FileChecksums {
    val (digests, crc) = FileInputStream(this).use { fileIn ->
        MultiDigestOutputStream(NullOutputStream(), MultiDigestOutputStream.arrayOfSupportedDigests(), CRC32()).use { digestOut ->
            fileIn.copyToAsync(digestOut)
            digestOut.flush()
            digestOut.digests.map { it.digest() } to digestOut.crc!!.value
        }
    }

    return FileChecksums(digests[INDEX_SHA256], digests[INDEX_SHA384], digests[INDEX_SHA512], crc)
}

suspend fun File.asLocallyStoredFileAsync(originUrl: String) : LocallyStoredFile{
    val checksums = fileChecksums()
    val base64Encoder = Base64.getEncoder()
    return LocallyStoredFile(originUrl, this.absolutePath, this.length(), checksums.crc32,
        base64Encoder.encodeToString(checksums.sha256),
        base64Encoder.encodeToString(checksums.sha384),
        base64Encoder.encodeToString(checksums.sha512))
}

fun File.asLocallyStoredFile(originUrl: String) : LocallyStoredFile {
    return runBlocking { asLocallyStoredFileAsync(originUrl) }
}

