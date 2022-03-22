package com.ustadmobile.lib.db.entities

import androidx.room.Embedded

class LocallyStoredFileAndDownloadJobItem {

    @Embedded
    var locallyStoredFile: LocallyStoredFile? = null

    @Embedded
    var downloadJobItem: DownloadJobItem? = null

}