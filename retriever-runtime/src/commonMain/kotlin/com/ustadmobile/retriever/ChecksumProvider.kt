package com.ustadmobile.retriever

fun interface ChecksumProvider {

    suspend fun getExpectedChecksum(url: String, checksumType: ChecksumType): ByteArray?

}