package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.TestAppView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class TestAppActivityController(context: Any, val db: RetrieverDatabase, val view: TestAppView) {

    fun onCreate(){

        GlobalScope.launch {
            val allFiles: List<AvailableFile> = db.availableFileDao.findAllAvailableFiles()
            println("TestAppActivityController : Total files : " + allFiles.size )
            val z = 1
        }
        view.localFiles = db.availableFileDao.findAllAvailableFilesLive()
    }

    fun addRandomFile(){

        println("TestAppActivityController: Adding a random file..")
        val rn = (0..100).random()
        GlobalScope.launch {
            db?.availableFileDao?.insert(
                AvailableFile(
                    "https://path.to/the/file" + rn + ".txt",
                    "file://path.to/the/file" + rn + ".txt"
                )
            )
        }
    }

    fun clearAllFiles(){
        println("TestAppActivityController: Clearing all local file..")
        GlobalScope.launch {
            db?.availableFileDao?.removeAllAvailableFiles()
        }
    }

}