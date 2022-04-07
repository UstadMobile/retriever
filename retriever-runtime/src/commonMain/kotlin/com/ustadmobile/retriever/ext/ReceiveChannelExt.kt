package com.ustadmobile.retriever.ext

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.delay

/**
 * When using a produce-await queue system, we don't need to run the queue once for each item in the check queue signal
 * channel.
 *
 * This function will wait for the first receive, and then wait a nominal length of time (10ms by default) for any
 * other items that are available on the channel.
 */
suspend fun <T> Channel<T>.receiveThenTryReceiveAllAvailable(
    waitAfterFirstItem: Long = 10
): List<T> {
    //wait for first item
    val firstItem = receive()
    delay(waitAfterFirstItem)
    return (listOf(firstItem) + tryReceiveAllAvailable())
}

fun <T> Channel<T>.tryReceiveAllAvailable() : List<T> {
    val itemsReceived = mutableListOf<T>()
    var channelResult: ChannelResult<T>
    while (tryReceive().also { channelResult = it }.isSuccess) {
        itemsReceived += channelResult.getOrThrow()
    }
    return itemsReceived.toList()
}