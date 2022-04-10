package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.NetworkNodeFailure
import com.ustadmobile.lib.db.entities.NetworkNodeSuccess
import com.ustadmobile.retriever.db.RetrieverDatabase

/**
 *
 */
interface RetrieverNodeHandler {

    suspend fun handleNetworkNodeFailures(transactionDb: RetrieverDatabase, failures: List<NetworkNodeFailure>)

    suspend fun handleNetworkNodeSuccessful(transactionDb: RetrieverDatabase, successes: List<NetworkNodeSuccess>)

}