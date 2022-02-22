package com.ustadmobile.retriever.util

expect open class ZipEntryKmp {

    open fun getName(): String

    open fun getCompressedSize(): Long

    open fun getComment(): String?

    open fun getExtra(): ByteArray?

}