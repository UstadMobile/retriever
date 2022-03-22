package com.ustadmobile.retriever.testapp.util

import com.ustadmobile.lib.db.entities.LocallyStoredFile
import kotlin.math.round

private const val UNIT_GB = 1024L * 1024L * 1024L

private const val UNIT_MB = 1024L * 1024L

private const val UNIT_KB: Long = 1024

fun formatTextFileSize(fileSize: Long): String {
    val unit: String
    val factor: Long
    if (fileSize > UNIT_GB) {
        factor = UNIT_GB
        unit = "GB"
    } else if (fileSize > UNIT_MB) {
        factor = UNIT_MB
        unit = "MB"
    } else if (fileSize > UNIT_KB) {
        factor = UNIT_KB
        unit = "kB"
    } else {
        factor = 1
        unit = "bytes"
    }

    var unitSize = fileSize.toDouble() / factor.toDouble()
    unitSize = round(unitSize * 100) / 100.0
    return "$unitSize $unit"
}
