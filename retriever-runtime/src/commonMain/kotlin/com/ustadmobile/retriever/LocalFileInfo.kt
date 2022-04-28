package com.ustadmobile.retriever

import com.ustadmobile.retriever.io.FileChecksums

/**
 * @param originUrl the origin url for this file
 * @param filePath the file path of the file on the disk (e.g. /path/to/file, not a file uri)
 * @param checksums - the checksums for this file (if already known)
 */
class LocalFileInfo(
    val originUrl: String,
    val filePath: String,
    val checksums: FileChecksums? = null,
)
