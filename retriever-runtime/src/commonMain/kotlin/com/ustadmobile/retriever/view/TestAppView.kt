package com.ustadmobile.retriever.view

import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.AvailableFile

interface TestAppView{

    var localFiles: DoorDataSourceFactory<Int, AvailableFile>?
    var remoteFiles: DoorDataSourceFactory<Int, AvailableFile>?

}