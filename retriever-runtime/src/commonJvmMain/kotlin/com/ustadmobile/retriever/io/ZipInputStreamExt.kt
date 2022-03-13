package com.ustadmobile.retriever.io

import com.ustadmobile.retriever.ProgressListener
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

suspend fun ZipInputStream.extractToDir(
    destProvider: (ZipEntry) -> File,
    progressListener: ProgressListener? = null,
) {
    lateinit var zipEntry: ZipEntry
    val buffer = ByteArray(8192)
    var bytesRead = 0

    while(nextEntry?.also { zipEntry = it } != null) {
        val destFile = destProvider(zipEntry)
        FileOutputStream(destFile, true).use { fileOut ->
            while(coroutineContext.isActive && this.read(buffer).also { bytesRead = it } != -1) {
                fileOut.write(buffer, 0, bytesRead)
                //TODO: call progress listener
            }
        }
    }
}
