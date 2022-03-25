package com.ustadmobile.retriever.testapp.controller

import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.testapp.view.FileListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class FileListController(
    context: Any,
    val db: RetrieverDatabase,
    val view: FileListView
) {

    fun onCreate(){

        view.localFileList = db.locallyStoredFileDao.findAllAvailableFilesLive()
    }


    fun deleteFile(availableFile: LocallyStoredFile){
        GlobalScope.launch {
            TODO("Fix this implementation - should actually delete file")
            //db.locallyStoredFileDao.removeFile(availableFile.locallyStoredFileUid)
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
            db.locallyStoredFileDao.insert(LocallyStoredFile(url, localPath, fileSize, -1))
        }
    }

    private fun getFileSize(url: URL): Long {
        var conn: HttpURLConnection? = null
        return try {
            conn = url.openConnection() as HttpURLConnection
            conn.setRequestMethod("HEAD")
            conn.getHeaderField("content-length")?.toLong() ?: -1L
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: Throwable){
            e.printStackTrace()
            return 0
        }finally {
            conn?.disconnect()
        }
    }

    fun addRandomFile(){

        val rn = (1..5).random()
        val rs = (1..10000000).random().toLong()
        GlobalScope.launch {
            db?.locallyStoredFileDao?.insert(
                LocallyStoredFile(
                    "https://path.to/the/file$rn.txt",
                    "file://path.to/the/file$rn.txt",
                    rs,
                    -1
                )
            )
        }
    }

}