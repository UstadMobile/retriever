package com.ustadmobile.retriever.io

import java.util.*


/**
 * Map in the form of the string expected at the start of the integrity tag to the name of the algorithm as per
 * MessageDigest.getInstance . Supports sha256, sha384, sha512 as per
 * https://developer.mozilla.org/en-US/docs/Web/Security/Subresource_Integrity
 */
private val SUPPORTED_DIGEST_MAP = mapOf("sha-256" to "SHA-256", "sha384" to "SHA-384", "sha512" to "SHA-512")

fun parseIntegrity(integrity: String): Pair<String, ByteArray> {
    val integrityParts = integrity.split(delimiters = arrayOf("-"), false, 2)
    if(integrityParts.size != 2)
        throw IllegalArgumentException("Invalid integrity string!")

    val digestName = SUPPORTED_DIGEST_MAP[integrityParts.first()]
        ?: throw IllegalArgumentException("Unsupported digest: $SUPPORTED_DIGEST_MAP")

    val expectedDigest = Base64.getDecoder().decode(integrityParts[1])

    return digestName to expectedDigest
}
