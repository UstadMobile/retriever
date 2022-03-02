package com.example.test_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.RetrieverBuilderAndroid


class BottomNavActivity : AppCompatActivity(){

    private lateinit var retriever: Retriever

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_botton_nav)

        retriever = RetrieverBuilderAndroid
            .builder(applicationContext, "UstadRetriever")
            .build()
        (retriever as RetrieverAndroidImpl).startNSD()

        val localFragment = LocalFileListFragment(retriever)
        val scanFragment = ScanFileListFragment(retriever)
        val nodesFragment = NodeListFragment(retriever)

        setCurrentFragment(localFragment, "Local")

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.activity_bottom_nav_bottomnav)
        bottomNavigationView.setOnNavigationItemSelectedListener {
            when(it.itemId){
                R.id.menu_bottom_nav_local->setCurrentFragment(localFragment, "Local")
                R.id.menu_bottom_nav_scan->setCurrentFragment(scanFragment, "Scan")
                R.id.menu_bottom_nav_nodes->setCurrentFragment(nodesFragment, "Nodes")

            }
            true
        }

    }
    private fun setCurrentFragment(fragment: Fragment, title: String) {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.activity_bottom_nav_toolbar).title = title
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.activity_bottom_nav_fl, fragment)
            commit()
        }
    }

}