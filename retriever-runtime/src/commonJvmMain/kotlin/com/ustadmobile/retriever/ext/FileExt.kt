package com.ustadmobile.retriever.ext

import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.IntegrityChecksum
import com.ustadmobile.retriever.io.FileChecksums
import com.ustadmobile.retriever.io.MultiDigestOutputStream
import com.ustadmobile.retriever.io.NullOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
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

suspend fun File.fileChecksums(
    checksums: List<IntegrityChecksum> = listOf(IntegrityChecksum.SHA256, IntegrityChecksum.SHA384, IntegrityChecksum.SHA512)
) : FileChecksums {
    val digestMap = checksums.associate { it.messageDigestName to MessageDigest.getInstance(it.messageDigestName) }
    val digests = digestMap.map { it.value }.toTypedArray()
    val crc32 = CRC32()
    FileInputStream(this).use { fileIn ->
        MultiDigestOutputStream(NullOutputStream(), digests, crc32).use { digestOut ->
            fileIn.copyToAsync(digestOut)
            digestOut.flush()
            digestOut.digests.map { it.digest() } to digestOut.crc!!.value
        }
    }

    return FileChecksums(
        digestMap[IntegrityChecksum.SHA256.messageDigestName]?.digest(),
        digestMap[IntegrityChecksum.SHA384.messageDigestName]?.digest(),
        digestMap[IntegrityChecksum.SHA512.messageDigestName]?.digest(),
        crc32.value)
}

/**
 * Convert this File object to a LocallyStoredFile
 *
 * @param originUrl the origin url from which this file was downloaded
 * @param integrityChecksumTypes a list of the types of IntegrityChecksum that are required (e.g. as per RetrieverConfig)
 * @param checksums the checksums for this file, if already known (can improve performance by avoiding the need
 *        to read the file and calculate checksums again
 */
suspend fun File.asLocallyStoredFileAsync(
    originUrl: String,
    integrityChecksumTypes: Array<IntegrityChecksum>,
    checksums: FileChecksums? = null,
) : LocallyStoredFile{
    val missingChecksumTypes = checksums?.findUnsetChecksumTypes(integrityChecksumTypes)
    val checksumsVal = if(checksums != null && missingChecksumTypes?.isEmpty() == true) {
        checksums
    }else {
        fileChecksums(integrityChecksumTypes.toList())
    }

    val base64Encoder = Base64.getEncoder()
    return LocallyStoredFile(originUrl, this.absolutePath, this.length(), checksumsVal.crc32,
        checksumsVal.sha256?.let { base64Encoder.encodeToString(it) },
        checksumsVal.sha384?.let { base64Encoder.encodeToString(it) },
        checksumsVal.sha512?.let { base64Encoder.encodeToString(it) })
}

internal fun File.asLocallyStoredFile(originUrl: String) : LocallyStoredFile {
    return runBlocking { asLocallyStoredFileAsync(originUrl, IntegrityChecksum.values()) }
}

