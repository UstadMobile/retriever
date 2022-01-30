package com.ustadmobile.retriever.util

object MimeUtilCommon {

    //Copied from NanoHTTPD
    private val theMimeTypes = mapOf(
        "htm" to "text/html",
        "html" to "text/html",
        "xhtml" to "application/xhtml+xml",
        "xml" to "text/xml",
        "txt" to "text/plain",
        "webp" to "image/webp",
        "webm" to "video/webm",
        "css" to "text/css",
        "asc" to "text/plain",
        "gif" to "image/gif",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "mp3" to "audio/mpeg",
        "m3u" to "audio/mpeg-url",
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "ogv" to "video/ogg",
        "flv" to "video/x-flv",
        "mov" to "video/quicktime",
        "swf" to "application/x-shockwave-flash",
        "js" to "application/javascript",
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "ogg" to "application/x-ogg",
        "zip" to "application/octet-stream",
        "exe" to "application/octet-stream",
        "wav" to "audio/wav",
        "class" to "application/octet-stream",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )

    fun getMimeTypeFromExtension(extension: String) : String{
        return theMimeTypes[extension.substringAfterLast(".")] ?: "application/octet-stream"
    }

}