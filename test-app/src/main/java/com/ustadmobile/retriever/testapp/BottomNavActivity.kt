package com.ustadmobile.retriever.testapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ustadmobile.retriever.Retriever
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.direct
import org.kodein.di.instance


class BottomNavActivity : AppCompatActivity(), DIAware{

    override val di by closestDI()

    private lateinit var navController: NavController

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_botton_nav)
        setSupportActionBar(findViewById(R.id.activity_bottom_nav_toolbar))

        val host: NavHostFragment = supportFragmentManager
            .findFragmentById(R.id.activity_navhost_fragment) as NavHostFragment? ?: return
        navController = host.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        val bottomNavView: BottomNavigationView = findViewById(R.id.activity_bottom_nav_bottomnav)
        bottomNavView.setupWithNavController(navController)
        setupActionBarWithNavController(navController, AppBarConfiguration(bottomNavView.menu))
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.activity_navhost_fragment).navigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        val retriever: Retriever = di.direct.instance()
        retriever.close()
    }
}