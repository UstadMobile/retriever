package com.ustadmobile.retriever.util

import java.lang.Exception
import java.net.ServerSocket
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.isActive

/**
 * Find a random port that is available.
 *
 * @param preferred - the preferred port number to try first. Normally the port number we last used. If 0, this will be
 *                    passed to the server socket, which itself will setup a random port.
 * @param from bottom of the range to try
 * @param to top of the range to try
 *
 * @return an Int representing a port that is free
 */
suspend fun findAvailableRandomPort(preferred: Int = 0, from: Int = 1024, to: Int = 65535): Int {
    try {
        return ServerSocket(preferred).use {
            it.localPort
        }
    }catch(e: Exception) {
        //Do nothing - proceed to find a new random port
    }


    while(coroutineContext.isActive) {
        try {
            val portNum = Random.nextInt(from, to)
            ServerSocket(portNum).close()
            return portNum
        }catch(e: Exception) {
            //try again - do nothing here
        }
    }

    return -1
}
