package com.ustadmobile.retriever.view

import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.LocallyStoredFile

interface LocalFileListView{

    var localFileList: DoorDataSourceFactory<Int, LocallyStoredFile>?

}