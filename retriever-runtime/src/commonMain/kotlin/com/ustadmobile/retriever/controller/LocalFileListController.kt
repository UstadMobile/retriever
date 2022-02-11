package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.LocalFileListView
import com.ustadmobile.retriever.view.NodeListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class LocalFileListController(
    context: Any,
    val db: RetrieverDatabase,
    val view: LocalFileListView) {

    fun onCreate(){
        view.localFileList = db.availableFileDao.findAllAvailableFilesLive()
    }


    fun addRandomFile(){

        val rn = (1..5).random()
        val rs = (1..10000000).random().toLong()
        GlobalScope.launch {
            db?.availableFileDao?.insert(
                AvailableFile(
                    "https://path.to/the/file" + rn + ".txt",
                    "file://path.to/the/file" + rn + ".txt",
                    rs
                )
            )
        }
    }

}