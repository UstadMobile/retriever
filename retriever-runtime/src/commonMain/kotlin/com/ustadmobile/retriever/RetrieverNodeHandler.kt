package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.NetworkNodeFailure
import com.ustadmobile.retriever.db.RetrieverDatabase

/**
 *
 */
interface RetrieverNodeHandler {

    suspend fun handleNetworkNodeFailures(transactionDb: RetrieverDatabase, failures: List<NetworkNodeFailure>)

    suspend fun handleNetworkNodeSuccessful()

    //HERE : allow components to listen for strike off and restore events.

}