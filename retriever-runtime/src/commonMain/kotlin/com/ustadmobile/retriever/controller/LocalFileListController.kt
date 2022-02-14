package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.LocalFileListView
import com.ustadmobile.retriever.view.NodeListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URL


class LocalFileListController(
    context: Any,
    val db: RetrieverDatabase,
    val view: LocalFileListView) {

    fun onCreate(){
        view.localFileList = db.availableFileDao.findAllAvailableFilesLive()
    }


    fun deleteFile(availableFile: AvailableFile){
        GlobalScope.launch {
            db.availableFileDao?.removeFile(availableFile.availableFileUid)
        }
    }

    fun addDownloadedFile(url: String,localPath: String,size: Long){
        GlobalScope.launch {

            val fileSize = if(size == 0L
            ){
                getFileSize(URL(url))
            }else{
                size
            }
            db?.availableFileDao?.insert(AvailableFile(url, localPath, fileSize))
        }
    }

    private fun getFileSize(url: URL): Long {
        var conn: HttpURLConnection? = null
        return try {
            conn = url.openConnection() as HttpURLConnection
            conn.setRequestMethod("HEAD")
            conn.getContentLengthLong()
        } catch (e: IOException) {
            throw RuntimeException(e)
        } finally {
            conn?.disconnect()
        }
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