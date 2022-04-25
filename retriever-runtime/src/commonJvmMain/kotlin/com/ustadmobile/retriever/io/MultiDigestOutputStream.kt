package com.ustadmobile.retriever.io

import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Gets multiple digests simultaneously when writing. Similar to MessageDigestOutputStream. Should be faster because
 * we use send ByteArray buffers directly to the underlying outputStream and digests (unlike FilterOutputStream by default)
 */
class MultiDigestOutputStream(
    outStream: OutputStream,
    val digests: Array<MessageDigest>,
    val crc: CRC32? = null,
) : FilterOutputStream(outStream) {

    override fun write(b: Int) {
        out.write(b)
        crc?.update(b)
        digests.forEach {
            it.update(b.toByte())
        }
    }

    override fun write(b: ByteArray) {
        this.write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        crc?.update(b, off, len)
        digests.forEach {
            it.update(b, off, len)
        }
    }

    override fun toString(): String {
        return "MultiDigestOutputStream for: $out"
    }

    companion object {

        fun arrayOfSupportedDigests(): Array<MessageDigest> {
            return arrayOf(MessageDigest.getInstance("SHA-256"),
                MessageDigest.getInstance("SHA-384"),
                MessageDigest.getInstance("SHA-512"))
        }

        const val INDEX_SHA256 = 0

        const val INDEX_SHA384 = 1

        const val INDEX_SHA512 = 2

        val SUPPORTED_DIGEST_INDEX_MAP = mapOf("SHA-256" to INDEX_SHA256,
            "SHA-384" to INDEX_SHA384,
            "SHA-512" to INDEX_SHA512,
            "sha256" to INDEX_SHA256,
            "sha384" to INDEX_SHA384,
            "sha512" to INDEX_SHA512)

    }
}