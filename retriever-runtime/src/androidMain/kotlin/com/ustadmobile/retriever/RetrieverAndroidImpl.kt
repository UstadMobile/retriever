package com.ustadmobile.retriever

import android.content.Context

class RetrieverAndroidImpl(
    val appContext: Context
): Retriever {

    init {

    }

    //TODO: on initialization - start advertising and discovery.

    override fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall {
        TODO("Not yet implemented")
    }
}