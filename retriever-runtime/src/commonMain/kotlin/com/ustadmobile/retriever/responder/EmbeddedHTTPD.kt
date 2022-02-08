package com.ustadmobile.retriever.responder


import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.jvm.JvmOverloads

/**
 * Embedded HTTP Server
 */
open class EmbeddedHTTPD @JvmOverloads constructor(portNum: Int) : RouterNanoHTTPD(portNum) {

    private val id: Int

    private val responseListeners = Vector<ResponseListener>()


    /**
     * Returns the local URL in the form of http://localhost;PORT/
     *
     * @return Local URL as above including a trailing slash
     */
    val localURL: String
        get() = "http://localhost:$listeningPort/"

    /**
     * Get the local HTTP server url with the URL as it is to be used for access over the loopback
     * interface
     *
     * @return Local http server url e.g. http://127.0.0.1:PORT/
     */
    val localHttpUrl: String
        get() = "http://127.0.0.1:$listeningPort/"


    interface ResponseListener {

        fun responseStarted(session: NanoHTTPD.IHTTPSession, response: NanoHTTPD.Response)

        fun responseFinished(session: NanoHTTPD.IHTTPSession, response: NanoHTTPD.Response?)

    }


    init {
        id = idCounter
        idCounter++

        addRoute("/:${RequestResponder.PARAM_FILE_REQUEST_URL}/",
            RequestResponder::class.java)

    }


    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        val response = super.serve(session)
        if (!responseListeners.isEmpty() && response != null) {
            fireResponseStarted(session, response)
            response.data = InputStreamWithCloseListener(response.data, object : InputStreamWithCloseListener.OnCloseListener {
                override fun onStreamClosed() {
                    fireResponseFinished(session, response)
                }
            })
        }

        return response
    }

    override fun addMappings() {
        super.addMappings()
    }


    override fun toString(): String {
        return "EmbeddedHTTPServer on port : $listeningPort id: $id"
    }



    /**
     * Add an entry response listener. This will receive response events when entries are sent to
     * clients.
     *
     * @param listener
     */
    fun addResponseListener(listener: ResponseListener) {
        responseListeners.add(listener)
    }

    /**
     * Remove an entry response listener.
     *
     * @param listener
     */
    fun removeResponseListener(listener: ResponseListener) {
        responseListeners.remove(listener)
    }

    protected fun fireResponseStarted(session: NanoHTTPD.IHTTPSession, response: NanoHTTPD.Response) {
        synchronized(responseListeners) {
            for (listener in responseListeners) {
                listener.responseStarted(session, response)
            }
        }
    }

    protected fun fireResponseFinished(session: NanoHTTPD.IHTTPSession, response: NanoHTTPD.Response?) {
        synchronized(responseListeners) {
            for (listener in responseListeners) {
                listener.responseFinished(session, response)
            }
        }
    }

    fun newSession(inputStream: InputStream, outputStream: OutputStream): IHTTPSession =
        HTTPSession(tempFileManagerFactory.create(), inputStream, outputStream)

    companion object {

        var idCounter = 0
    }
}
