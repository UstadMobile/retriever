package com.ustadmobile.retriever.checksumproviders

import com.ustadmobile.retriever.ChecksumProvider
import com.ustadmobile.retriever.ChecksumType

class KnownSha256ChecksumProvider(val knownChecksum: ByteArray) : ChecksumProvider{

    override suspend fun getExpectedChecksum(url: String, checksumType: ChecksumType): ByteArray? {
        if(checksumType != ChecksumType.SHA256)
            throw IllegalArgumentException("Only supports SHA256")

        return knownChecksum
    }
}