package com.ustadmobile.retriever.ext

import com.ustadmobile.door.ext.writeToFile
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TestZipEntryKmpExt {
    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    lateinit var catPicFile: File

    lateinit var overlayFile: File

    @Before
    fun setup() {
        catPicFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/cat-pic0.jpg").writeToFile(catPicFile)

        overlayFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/animated-overlay.gif").writeToFile(overlayFile)
    }

    @Test
    fun givenZipEntries_whenZipped_thenSizeShouldMatch() {

        val byteArrayOut = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(byteArrayOut)
        zipOut.setLevel(0)
        zipOut.setMethod(ZipOutputStream.STORED)
        val crc = CRC32()
        val fileBytes1 = catPicFile.readBytes()
        crc.update(fileBytes1)
        val entry1 = ZipEntry("caz.jpg").also {
            it.size = catPicFile.length()
            it.compressedSize = it.size
            it.crc = crc.value
        }
        zipOut.putNextEntry(entry1)
        zipOut.write(fileBytes1)
        zipOut.closeEntry()

        crc.reset()

        val fileBytes2 = overlayFile.readBytes()
        crc.update(fileBytes2)
        val entry2 = ZipEntry("cat.jpg").also {
            it.size = overlayFile.length()
            it.compressedSize = it.size
            it.crc = crc.value
        }
        zipOut.putNextEntry(entry2)
        zipOut.write(fileBytes2)
        zipOut.closeEntry()
        zipOut.flush()
        zipOut.close()

        val zipOutLen = byteArrayOut.toByteArray().size.toLong()

        assertEquals("Length predicted correctly", zipOutLen,
            listOf(entry1, entry2).totalZipSize(null))

    }
}