package com.ustadmobile.retriever.testapp.view

import androidx.lifecycle.LiveData
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.lib.db.entities.LocallyStoredFileAndDownloadJobItem

interface FileListView{

    var localFileList: LiveData<List<LocallyStoredFileAndDownloadJobItem>>?

}