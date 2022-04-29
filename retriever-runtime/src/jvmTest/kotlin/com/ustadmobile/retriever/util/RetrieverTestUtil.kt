package com.ustadmobile.retriever.util

import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.ext.url
import java.io.File
import java.security.MessageDigest
import java.util.*

/**
 * Create a list of DownloadJobItems based on the h5pcontainer resource (to give an accurate test of downloading a
 * many files together - 1789 to be exact)
 *
 * @param downloadDestDir destination dir where files should be saved
 * @param resourcePathToUrlFn a function (e.g. uriServer.url) that will provide a url for a given resource path
 */
fun h5pDownloadJobItemList(
    downloadDestDir: File,
    resourcePathToUrlFn: (String) -> String,
): List<DownloadJobItem> {
    val sha256Digest = MessageDigest.getInstance("SHA-256")
    return (0..1789).map {
        DownloadJobItem().apply {
            djiOriginUrl = resourcePathToUrlFn("/h5pcontainer/$it.gz")
            djiDestPath = File(downloadDestDir, "$it.gz").absolutePath
            val resBytes = this::class.java.getResourceAsStream("/h5pcontainer/$it.gz")!!
                .readAllBytes()
            djiIntegrity = "sha256-" + Base64.getEncoder().encodeToString(sha256Digest.digest(resBytes))
        }
    }
}