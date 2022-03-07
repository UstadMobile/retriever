package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.AvailabilityObserverItemWithNetworkNode
import com.ustadmobile.retriever.*
import com.ustadmobile.retriever.checksumproviders.IgnoreChecksumProvider
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.ScanFileListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class ScanFileListController(
    context: Any,
    val db: RetrieverDatabase,
    val view: ScanFileListView,
    val retriever: Retriever) {

    fun onCreate(){
        view.watchList = db.availabilityObserverItemDao.getWatchListLive()

        GlobalScope.launch {
            retriever.forceStartJob()
        }
    }


    fun removeFileUrl(availableFileWithNumNodes: AvailabilityFileWithNumNodes){
        GlobalScope.launch {
            db.availabilityObserverItemDao.removeFromWatchList(availableFileWithNumNodes.aoiId)
        }
    }


    fun addToAvailabilityObserver(
        retrieverCommon: RetrieverCommon,
        availabilityObserver: AvailabilityObserver){

        GlobalScope.launch {

            retrieverCommon.addAvailabilityObserver(availabilityObserver)
        }
    }


    fun removeFromAvailabilityObserver(
        retrieverCommon: RetrieverCommon,
        availabilityObserver: AvailabilityObserver){

        GlobalScope.launch {
            retrieverCommon.removeAvailabilityObserver(availabilityObserver)
        }
    }

}