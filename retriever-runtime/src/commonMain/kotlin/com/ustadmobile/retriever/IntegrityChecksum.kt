package com.ustadmobile.retriever

/**
 * Enum representing the different types of integrity checksum that are supported as per
 * https://developer.mozilla.org/en-US/docs/Web/Security/Subresource_Integrity
 */
enum class IntegrityChecksum (val messageDigestName: String, val integritySriPrefix: String){
    SHA256("SHA-256", "sha256"),
    SHA384("SHA-384", "sha384"),
    SHA512("SHA-512", "sha512")
}
