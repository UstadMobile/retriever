package com.ustadmobile.retriever.view

import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.AvailableFile

interface LocalFileListView{

    var localFileList: DoorDataSourceFactory<Int, AvailableFile>?

}