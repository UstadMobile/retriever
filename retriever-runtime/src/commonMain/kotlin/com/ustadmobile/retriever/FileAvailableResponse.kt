package com.ustadmobile.retriever

import kotlinx.serialization.Serializable

/**
 * As per the README, peers reply to an availability query with a list of those items that are available.
 * FileAvailableResponse models the JSON element that is used in that list.
 */
@Serializable
data class FileAvailableResponse(val originUrl: String, val sha256: String, val size: Long)
