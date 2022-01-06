package com.example.test_app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityTestAppBinding
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.RetrieverAndroidImpl

class TestAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestAppBinding

    private lateinit var retriever: RetrieverAndroidImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Trying to init:
        retriever  = RetrieverAndroidImpl(applicationContext)

        DatabaseBuilder.databaseBuilder(applicationContext, UmAppDatabase::class,
            "dbname").build()

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
}