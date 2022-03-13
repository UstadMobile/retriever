package com.ustadmobile.retriever.ext

import com.ustadmobile.retriever.util.ZipEntryKmp


/**
 * Calculate the size of the entry (including both data and headers) as outlined here:
 * https://jakewharton.com/calculating-zip-file-entry-true-impact/
 */
val ZipEntryKmp.totalSize: Long
    get() {
        val nameSize = getName().encodeToByteArray().size
        val extraSize = getExtra()?.size ?: 0
        val commentSize = getComment()?.encodeToByteArray()?.size ?: 0

        //Each entry has a 30 byte header, includes the name and extras twice, and then has 46 bytes at the end, and comment bytes (if any)
        return getCompressedSize() + 30 + nameSize + extraSize + 46 + nameSize + extraSize + commentSize
    }

val ZipEntryKmp.headerSize: Int
    get() {
        val nameSize = getName().encodeToByteArray().size
        val extraSize = getExtra()?.size ?: 0
        return 30  + nameSize + extraSize
    }

fun List<ZipEntryKmp>.totalZipSize(comment: String?): Long {
    return sumOf { it.totalSize } + (comment?.encodeToByteArray()?.size?.toLong() ?: 0L) + 22
}
