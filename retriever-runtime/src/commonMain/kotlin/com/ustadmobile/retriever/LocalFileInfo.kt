package com.ustadmobile.retriever

/**
 * @param sha256 SHA-256 sum (base64 encoded)
 * @param sha384 SHA-384 sum (base64 encoded)
 * @param sha512 SHA-512 sum (base64 encoded)
 */
class LocalFileInfo(
    val originUrl: String,
    val filePath: String,
    val sha256: String? = null,
    val sha384: String? = null,
    val sha512: String? = null,
)
