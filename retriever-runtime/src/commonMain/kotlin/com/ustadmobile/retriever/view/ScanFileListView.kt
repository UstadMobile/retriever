package com.ustadmobile.retriever.view

import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes

interface ScanFileListView{

    var watchList: DoorDataSourceFactory<Int, AvailabilityFileWithNumNodes>?

}