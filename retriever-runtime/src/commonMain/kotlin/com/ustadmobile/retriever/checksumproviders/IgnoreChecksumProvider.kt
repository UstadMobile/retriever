package com.ustadmobile.retriever.checksumproviders

import com.ustadmobile.retriever.ChecksumProvider
import com.ustadmobile.retriever.ChecksumType

class IgnoreChecksumProvider: ChecksumProvider {

    override suspend fun getExpectedChecksum(url: String, checksumType: ChecksumType) = null
}