package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverCall
import com.ustadmobile.retriever.RetrieverRequest
import com.ustadmobile.retriever.checksumproviders.IgnoreChecksumProvider
import com.ustadmobile.retriever.checksumproviders.OriginServerChecksumProvider
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

        val rn = (1..5).random()
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

    fun retrieveTestFiles(retriever: Retriever){
        //TODO: Clear the files list on the view

        val ignoreChecksumProvider: IgnoreChecksumProvider = IgnoreChecksumProvider()
        val retrieverRequests: List<RetrieverRequest> = listOf(
            RetrieverRequest("https://path.to/the/file0.txt", ignoreChecksumProvider),
            RetrieverRequest("https://path.to/the/file1.txt", ignoreChecksumProvider),
            RetrieverRequest("https://path.to/the/file2.txt", ignoreChecksumProvider),
            RetrieverRequest("https://path.to/the/file3.txt", ignoreChecksumProvider),
            RetrieverRequest("https://path.to/the/file4.txt", ignoreChecksumProvider),
            RetrieverRequest("https://path.to/the/file5.txt", ignoreChecksumProvider)
        )
        GlobalScope.launch {
            val retrieverCall: RetrieverCall = retriever.retrieve(retrieverRequests)
            //TODO: Update the view with result

        }
    }

}