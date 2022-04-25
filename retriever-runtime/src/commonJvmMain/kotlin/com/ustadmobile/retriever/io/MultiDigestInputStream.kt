package com.ustadmobile.retriever.io

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest

class MultiDigestInputStream(
    inStream: InputStream,
    private val digests: Array<MessageDigest>,
): FilterInputStream(inStream) {

    override fun read(): Int {
        return `in`.read().also { readResult ->
            digests.forEach {
                it.update(readResult.toByte())
            }
        }
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return `in`.read(b, off, len).also { readCount ->
            digests.forEach { digest ->
                digest.update(b, off, readCount)
            }
        }
    }
}