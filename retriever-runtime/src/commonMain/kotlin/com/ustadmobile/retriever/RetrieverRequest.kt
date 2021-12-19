package com.ustadmobile.retriever

data class RetrieverRequest (
    val originUrl: String,
    val checksumProvider: ChecksumProvider
){
}