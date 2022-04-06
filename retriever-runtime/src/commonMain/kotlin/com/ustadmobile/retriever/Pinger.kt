package com.ustadmobile.retriever

/**
 * Simple interface that makes a simple HTTP "ping" call as per the spec. This is separated out into an interface
 * for testability purposes.
 */
fun interface Pinger {

    /**
     * Make a "ping" http request to the given remote endpoint. If successful, returns and stays silent. Throws an
     * exception if unsuccessful
     *
     * @param endpoint - the remote endpoint to ping
     * @param localListeningPort - the local listening port of the local node. This enables the remote node to
     *         "discover" the local node if it failed to do so via Network Service Discovery and/or update the
     *         lastSuccessTime
     */
    suspend fun ping(endpoint: String, localListeningPort: Int)

}
