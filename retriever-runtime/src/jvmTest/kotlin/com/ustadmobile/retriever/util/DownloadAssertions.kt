package com.ustadmobile.retriever.util

import com.ustadmobile.retriever.db.entities.DownloadJobItem
import com.ustadmobile.retriever.ext.requirePrefix
import com.ustadmobile.retriever.fetcher.RetrieverListener
import org.junit.Assert
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verifyBlocking
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Assert that the receiver DownloadJobItem completed successfully where the download has been served using the
 * resources UriResponder.
 *
 * @param mockRetrieverListener retriever listener mock on which to run verify calls
 * @param urlPrefix the url prefix before the resource path e.g.
 *   When the url was http://localhost:21213/resources/somepath/file.jpg
 *   and the resource itself is /somepath/file.jpg
 *   Then urlPrefix = http://localhost:21213/resources/
 */
fun DownloadJobItem.assertSuccessfullyCompleted(
    mockRetrieverListener: RetrieverListener,
    urlPrefix: String,
){
    val originalItemBytes = this::class.java.getResourceAsStream(
        djiOriginUrl!!.removePrefix(urlPrefix).requirePrefix("/"))!!.readBytes()
    Assert.assertArrayEquals("Content for ${urlPrefix} is the same",
        originalItemBytes, File(djiDestPath!!).readBytes())
    val expectedSha256 = MessageDigest.getInstance("SHA-256").digest(originalItemBytes)
    val expectedCrc32 = CRC32().also { it.update(originalItemBytes) }.value
    verifyBlocking(mockRetrieverListener, atLeastOnce()) {
        onRetrieverProgress(argWhere { evt ->
            evt.url == djiOriginUrl && evt.bytesSoFar > 0 && evt.bytesSoFar == evt.totalBytes
        })
    }

    verifyBlocking(mockRetrieverListener) {
        onRetrieverStatusUpdate(argWhere { evt ->
            evt.url == djiOriginUrl && evt.status == com.ustadmobile.retriever.Retriever.STATUS_SUCCESSFUL
                    && java.util.Arrays.equals(expectedSha256, evt.checksums?.sha256)
                    && evt.checksums?.crc32 == expectedCrc32
        })
    }
}