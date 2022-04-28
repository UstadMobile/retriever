package com.ustadmobile.retriever

/**
 * Enum representing the different types of integrity checksum that are supported as per
 * https://developer.mozilla.org/en-US/docs/Web/Security/Subresource_Integrity
 */
enum class IntegrityChecksum (val messageDigestName: String){
    SHA256("SHA-256"),
    SHA384("SHA-384"),
    SHA512("SHA-512")
}