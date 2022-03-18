package com.ustadmobile.retriever.testapp.controller

import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes
import com.ustadmobile.retriever.AvailabilityObserver
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverCommon
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.testapp.view.ScanFileListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class ScanFileListController(
    context: Any,
    val db: RetrieverDatabase,
    val view: ScanFileListView,
    val retriever: Retriever) {

    fun onCreate(){
        view.watchList = db.availabilityObserverItemDao.getWatchListLive()
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