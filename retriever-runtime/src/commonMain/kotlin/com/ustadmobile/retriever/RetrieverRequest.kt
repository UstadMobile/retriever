package com.ustadmobile.retriever

data class RetrieverRequest (
    val originUrl: String,
    val destinationFilePath: String,
    val sriIntegrity: String?,
)