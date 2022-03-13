package com.ustadmobile.retriever

import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.responder.FileResponder
import java.io.InputStream

/**
 * Implementation of IFileSource so we can directly use resource streams to provide file responses with NanoHTTPD (
 * using support for range responses, etc)
 */
class ResourceFileSource(
    private val clazz: Class<*>,
    private val path: String,
) : FileResponder.IFileSource{

    override val length: Long by lazy {
        clazz.getResourceAsStream(path)?.readAllBytes()?.size?.toLong() ?: -1
    }

    override val lastModifiedTime: Long
        get() = startTime

    override val inputStream: InputStream
        get() = clazz.getResourceAsStream(path) ?: throw IllegalStateException("Resource $path does not exist")

    override val name: String
        get() = path.substringAfterLast("/")

    override val exists: Boolean
        get() = length > 0

    override val eTag: String?
        get() = null

    companion object {

        val startTime = systemTimeInMillis()

    }
}