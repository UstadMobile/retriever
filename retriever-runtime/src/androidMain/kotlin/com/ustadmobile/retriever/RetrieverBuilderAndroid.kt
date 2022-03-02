package com.ustadmobile.retriever

import android.content.Context
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.db.RetrieverDatabase

/**
 * Usage : RetrieverBuilder.builder(context, "serviceName").build()
 */
class RetrieverBuilderAndroid private constructor(
    private val context: Context,
    private val nsdServiceName: String,
){

    fun build() : Retriever {
        val db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class, DB_NAME)
            .build()

        val availabilityChecker: AvailabilityCheckerAndroidImpl = AvailabilityCheckerAndroidImpl(db)
        return RetrieverAndroidImpl(db, nsdServiceName, context, availabilityChecker)

    }

    companion object {
        private const val DB_NAME = "retrieverdb"

        fun builder(context: Context, nsdServiceName: String) = RetrieverBuilderAndroid(context, nsdServiceName)

    }

}