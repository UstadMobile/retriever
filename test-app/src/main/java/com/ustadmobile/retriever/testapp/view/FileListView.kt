package com.ustadmobile.retriever.testapp.view

import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.LocallyStoredFile

interface FileListView{

    var localFileList: DoorDataSourceFactory<Int, LocallyStoredFile>?

}