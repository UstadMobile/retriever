package com.ustadmobile.retriever.util

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DoorDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

suspend fun <T : DoorDatabase> T.waitUntilOrTimeout(
    timeMillis: Long,
    tableNames: List<String>,
    block: suspend (T) -> Boolean
) {
    if(block(this))
        return

    val completable = CompletableDeferred<Boolean>()

    val changeListener = ChangeListenerRequest(tableNames){
        GlobalScope.launch {
            if(block(this@waitUntilOrTimeout)) {
                completable.complete(true)
            }
        }
    }

    addChangeListener(changeListener)

    try {
        withTimeout(timeMillis) {
            completable.await()
        }
    }finally {
        removeChangeListener(changeListener)
    }
}