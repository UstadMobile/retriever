package com.ustadmobile.retriever

import android.content.Context
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.db.RetrieverDatabase

class RetrieverBuilder private constructor(
    private val context: Context,
    private val nsdServiceName: String,
){

    fun build() : Retriever {
        val db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class, DB_NAME)
            .build()

        return RetrieverAndroidImpl(db, nsdServiceName, context, /* Add availability checker impl class here*/)


    }

    companion object {
        private const val DB_NAME = "retrieverdb"

        fun builder(context: Context, nsdServiceName: String) = RetrieverBuilder(context, nsdServiceName)

    }

}