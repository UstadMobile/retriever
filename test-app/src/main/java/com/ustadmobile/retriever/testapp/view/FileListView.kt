package com.ustadmobile.retriever.testapp.view

import androidx.lifecycle.LiveData
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.entities.LocallyStoredFileAndDownloadJobItem

interface FileListView{

    var localFileList: LiveData<List<LocallyStoredFileAndDownloadJobItem>>?

}