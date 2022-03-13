package com.ustadmobile.retriever.ext

import fi.iki.elonen.NanoHTTPD

/**
 * Given a path on the server, get the full http url
 */
fun NanoHTTPD.url(path: String): String {
    return "http://localhost:${this.listeningPort}${path.requirePrefix("/")}"
}
