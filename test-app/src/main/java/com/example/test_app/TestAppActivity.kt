package com.example.test_app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.ActivityTestAppSingleBinding
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.controller.TestAppActivityController
import com.ustadmobile.retriever.view.RetrieverViewCallback
import com.ustadmobile.retriever.view.TestAppView

interface ClickAddFile{
    fun onClickAddFile()
    fun clearFileList()
    fun refreshRemoteFiles()
}

class TestAppActivity : AppCompatActivity(), ClickAddFile, TestAppView, RetrieverViewCallback,
        FileListener{

    private lateinit var binding: ActivityTestAppSingleBinding

    private lateinit var retriever: RetrieverAndroidImpl

    private lateinit var controller: TestAppActivityController

    private lateinit var localFilesRv: RecyclerView
    private lateinit var remoteFilesRv: RecyclerView

    private var localFilesRecyclerAdapter : FilesRecyclerAdapter? = null
    private var remoteFilesRecyclerAdapter : FilesRecyclerAdapter? = null

    private var localFilesLiveData: LiveData<PagedList<AvailableFile>>? = null
    private var remoteFilesLiveData: LiveData<PagedList<AvailableFile>>? = null

    private val localFilesObserver = Observer<PagedList<AvailableFile>?>{ t->
        run{
            localFilesRecyclerAdapter?.submitList(t)
        }
    }

    private val remoteFilesObserver = Observer<PagedList<AvailableFile>?>{ t->
        run{
            remoteFilesRecyclerAdapter?.submitList(t)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_test_app_single)
        binding.listener = this
        binding.port = "0"

        //Init database, start NetworkServiceDiscovery
        retriever  = RetrieverAndroidImpl(applicationContext, this)
        retriever.startNSD()
        controller = TestAppActivityController(applicationContext, retriever.database, this)

        title = "Retriever Test"

        localFilesRv = findViewById(R.id.single_local_files)
        remoteFilesRv = findViewById(R.id.single_remote_files)

        localFilesRv.layoutManager = LinearLayoutManager(applicationContext)
        remoteFilesRv.layoutManager = LinearLayoutManager(applicationContext)

        localFilesRecyclerAdapter = FilesRecyclerAdapter(this)
        remoteFilesRecyclerAdapter = FilesRecyclerAdapter(this)

        localFilesRv.adapter = localFilesRecyclerAdapter
        binding.singleLocalFiles.adapter = localFilesRecyclerAdapter

        remoteFilesRv.adapter = remoteFilesRecyclerAdapter
        binding.singleRemoteFiles.adapter = remoteFilesRecyclerAdapter

        controller.onCreate()

        binding.controller = controller

        binding.port = retriever.listeningPort.toString()

    }

    override fun onDestroy() {
        super.onDestroy()
        //TODO: fill
        localFilesLiveData = null

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
        Toast.makeText(applicationContext, "Adding a random file..", Toast.LENGTH_SHORT).show()
        controller.addRandomFile()

    }

    override fun clearFileList() {
        //Remove all files via presenter
        Toast.makeText(applicationContext, "Clearing all local files..", Toast.LENGTH_SHORT).show()
        controller.clearAllFiles()
    }

    override fun refreshRemoteFiles() {
        controller.retrieveTestFiles(retriever)
    }

    override var localFiles: DoorDataSourceFactory<Int, AvailableFile>? = null
        set(value) {
            localFilesLiveData?.removeObserver(localFilesObserver)
            localFilesLiveData = value?.asRepositoryLiveData(retriever.database.availableFileDao)
            field = value
            localFilesLiveData?.observe(this, localFilesObserver)
        }

    override var remoteFiles: DoorDataSourceFactory<Int, AvailableFile>? = null
        set(value) {
            remoteFilesLiveData?.removeObserver(remoteFilesObserver)
            remoteFilesLiveData = value?.asRepositoryLiveData(retriever.database.availableFileDao)
            field = value
            remoteFilesLiveData?.observe(this, remoteFilesObserver)
        }

    override var hostAndPort: String? = null
        set(value) {
            binding.port = value?:""
            field = value
        }

    override fun deleteFile(availableFile: AvailableFile) {

    }

    override fun handleClickFile(availableFile: AvailableFile) {

    }
}