package com.example.test_app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.FragmentLocalFileListBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.controller.LocalFileListController
import com.ustadmobile.retriever.view.LocalFileListView

interface ClickAddLocalFile{
    fun onClickAddRandom()
    fun onClickAddFromUrl()
}

class LocalFileListFragment(val retriever: RetrieverAndroidImpl): Fragment(), LocalFileListView,
    ClickAddLocalFile {

    private lateinit var binding: FragmentLocalFileListBinding

    private lateinit var controller: LocalFileListController

    private lateinit var localFileListRecyclerView: RecyclerView

    private var localFileListLiveData: LiveData<PagedList<AvailableFile>>? = null

    private var localFileListRecyclerAdapter : FilesRecyclerAdapter? = null

    private val localFileListObserver = Observer<PagedList<AvailableFile>?>{ t->
        run{
            localFileListRecyclerAdapter?.submitList(t)
        }
    }

    private var fabClicked: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView: View
        binding = FragmentLocalFileListBinding.inflate(inflater, container, false).also{
            rootView = it.root
            it.listener = this
        }

        //val root = inflater.inflate(R.layout.fragment_local_file_list, container, false)
        localFileListRecyclerView = rootView.findViewById(R.id.fragment_local_file_list_rv)
        localFileListRecyclerView.layoutManager = LinearLayoutManager(context)

        localFileListRecyclerAdapter = FilesRecyclerAdapter()

        localFileListRecyclerView.adapter = localFileListRecyclerAdapter

        controller = LocalFileListController(requireContext(), retriever.database, this)
        controller.onCreate()

        val fab: FloatingActionButton = rootView.findViewById(R.id.fragment_local_file_list_fab_add)

        fab.setOnClickListener {

            if (fabClicked) {
                fab.animate().rotation(-90f)
                showFabItems(rootView, View.GONE)
                fabClicked = false

            } else {
                fab.animate().rotation(45f)
                showFabItems(rootView, View.VISIBLE)
                fabClicked = true
            }
        }
        return rootView

    }

    private fun showFabItems(view: View, visibility: Int){
        if(visibility == View.VISIBLE) {
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_random).show()
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_url).show()
        }else{
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_random).hide()
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_url).hide()
        }
        view.findViewById<TextView>(R.id.fragment_local_file_list_random_tv).visibility = visibility
        view.findViewById<TextView>(R.id.fragment_local_file_list_url_tv).visibility = visibility
    }

    override var localFileList: DoorDataSourceFactory<Int, AvailableFile>? = null
        set(value) {
            localFileListLiveData?.removeObserver(localFileListObserver)
            localFileListLiveData = value?.asRepositoryLiveData(retriever.database.availableFileDao)
            field = value
            localFileListLiveData?.observe(this, localFileListObserver)
        }

    override fun onClickAddRandom() {
        controller.addRandomFile()
    }

    override fun onClickAddFromUrl() {
        //TODO: Show dialog and capture and all that


    }


}