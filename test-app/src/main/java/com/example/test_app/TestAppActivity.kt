package com.example.test_app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityTestAppBinding
import com.ustadmobile.retriever.RetrieverAndroidImpl

class TestAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Trying to init:
        val retriever : RetrieverAndroidImpl = RetrieverAndroidImpl(applicationContext)
        retriever.startNSD()

        setContentView(R.layout.activity_test_app)
        setTitle("Retreiver Test")
//
//        binding = ActivityTestAppBinding.inflate(layoutInflater)
//        setContentView(binding.root)


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