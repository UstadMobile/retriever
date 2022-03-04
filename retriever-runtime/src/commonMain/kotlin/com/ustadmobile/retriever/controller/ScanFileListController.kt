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

//        //Call retrieve
//        startWatchListScan(retriever)
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


//    private fun startWatchListScan(retriever: Retriever) {
//
//        val ignoreChecksumProvider: IgnoreChecksumProvider = IgnoreChecksumProvider()
//
//        GlobalScope.launch {
//            val watchList: List<AvailabilityObserverItemWithNetworkNode> =
//                db.availabilityObserverItemDao.findPendingItems()
//            val retrieverRequests = watchList.map {
//                RetrieverRequest(it.aoiOriginalUrl ?: "", ignoreChecksumProvider)
//            }
//
//            val retrieverCall: RetrieverCall = retriever.retrieve(retrieverRequests)
//
//        }
//
//    }

}