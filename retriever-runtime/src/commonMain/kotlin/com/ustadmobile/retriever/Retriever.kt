package com.ustadmobile.retriever

interface Retriever {

    //retriever.retrieve(RetrieverRequest("http://something.file"), SHA256ChecksumProvider("aabbccddee"))
    //    .saveTo(dirPath)
    fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall

}