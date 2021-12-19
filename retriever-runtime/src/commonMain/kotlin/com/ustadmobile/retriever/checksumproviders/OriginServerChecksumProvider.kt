package com.ustadmobile.retriever.checksumproviders

import com.ustadmobile.retriever.ChecksumProvider
import com.ustadmobile.retriever.ChecksumType

class OriginServerChecksumProvider: ChecksumProvider {

    override suspend fun getExpectedChecksum(url: String, checksumType: ChecksumType): ByteArray? {
        TODO("make an http request for url.sha256 file, then return it")
    }
}