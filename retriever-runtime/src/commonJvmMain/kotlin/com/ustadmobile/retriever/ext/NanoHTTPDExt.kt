package com.ustadmobile.retriever.ext

import fi.iki.elonen.NanoHTTPD

/**
 * Given a path on the server, get the full http url
 */
fun NanoHTTPD.url(path: String): String {
    return "http://127.0.0.1:${this.listeningPort}${path.requirePrefix("/")}"
}
