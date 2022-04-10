package com.ustadmobile.retriever

data class RetrieverConfig(
    val nsdServiceName: String,
    val strikeOffTimeWindow: Long = 3 * 60 * 1000,
    val strikeOffMaxFailures: Int = 3,
    val pingInterval: Long = 30000,
    val pingRetryInterval: Long = 5000,
    val pingTimeout: Long = 5000,
    val port: Int = 0,
) {
}