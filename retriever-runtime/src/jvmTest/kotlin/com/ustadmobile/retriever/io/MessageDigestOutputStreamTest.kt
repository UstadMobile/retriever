package com.ustadmobile.retriever.io

import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.CRC32

class MessageDigestOutputStreamTest {

    @Test
    fun givenInputData_whenUsed_thenShouldMatchMessageDigest() {
        val inputData = this::class.java.getResourceAsStream("/cat-pic0.jpg").readAllBytes()

        val crc32 = CRC32()
        val digests = MultiDigestOutputStream.arrayOfSupportedDigests()
        val multiDigestOut = MultiDigestOutputStream(NullOutputStream(), digests, crc32)
        ByteArrayInputStream(inputData).copyTo(multiDigestOut)
        multiDigestOut.flush()

        val checkCrc32 = CRC32()
        val checkDigests = MultiDigestOutputStream.arrayOfSupportedDigests()

        checkCrc32.update(inputData)
        Assert.assertEquals("CRC32 matches", checkCrc32.value, crc32.value)

        checkDigests.forEachIndexed { index, messageDigest ->
            checkDigests[index].update(inputData)
            Assert.assertArrayEquals("Digest $index matches ", messageDigest.digest(),
                digests[index].digest())
        }
    }

}