package com.ustadmobile.retriever.ext

import java.util.*

actual fun String.decodeBase64(): ByteArray {
    return Base64.getDecoder().decode(this)
}