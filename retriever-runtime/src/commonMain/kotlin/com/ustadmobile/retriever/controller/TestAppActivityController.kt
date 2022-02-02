package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class TestAppActivityController(context: Any, val db: RetrieverDatabase?) {

    fun onCreate(){

    }

    fun addRandomFile(){

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
        GlobalScope.launch {
            db?.availableFileDao?.removeAllAvailableFiles()
        }
    }

}