package com.example.test_app

import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityTestAppBinding
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.Retriever.Companion.DBNAME
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.controller.TestAppActivityController
import com.ustadmobile.retriever.db.RetrieverDatabase

interface ClickAddFile{
    fun onClickAddFile()
    fun clearFileList()
}

class TestAppActivity : AppCompatActivity(), ClickAddFile {

    private lateinit var binding: ActivityTestAppBinding

    private lateinit var retriever: RetrieverAndroidImpl

    private lateinit var controller: TestAppActivityController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Init database, start NetworkServiceDiscovery

        val database = DatabaseBuilder.databaseBuilder(
            applicationContext,
            RetrieverDatabase::class,
            DBNAME
        ).build()
        retriever  = RetrieverAndroidImpl(applicationContext)

        controller = TestAppActivityController(applicationContext, database)

        setContentView(R.layout.activity_test_app)
        title = "Retriever Test"

        setSupportActionBar(findViewById(R.id.toolbar))

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_test_app, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onClickAddFile() {
        //Add file via presenter
        controller.addRandomFile()

    }

    override fun clearFileList() {
        //Remove all files via presenter
        controller.clearAllFiles()
    }
}