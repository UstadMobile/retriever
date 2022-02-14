package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.ScanFileListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class ScanFileListController(
    context: Any,
    val db: RetrieverDatabase,
    val view: ScanFileListView) {

    fun onCreate(){
        //view.localFileList = db.availableFileDao.findAllAvailableFilesLive()
    }


    fun removeFileUrl(availableFileWithNumNodes: AvailabilityFileWithNumNodes){
        GlobalScope.launch {
            //TODO
        }
    }

    fun addUrlToScan(url: String){
        //TODO
    }

}