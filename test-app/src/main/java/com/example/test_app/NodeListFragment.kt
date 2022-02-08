package com.example.test_app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.controller.NodeListController
import com.ustadmobile.retriever.view.NodeListView

class NodeListFragment(val retriever: RetrieverAndroidImpl): Fragment(), NodeListView{


    private lateinit var controller: NodeListController

    private lateinit var nodeListRecyclerView: RecyclerView

    private var nodeListLiveData: LiveData<PagedList<NetworkNode>>? = null

    private var nodeListRecyclerAdapter : NodeListRecyclerAdapter? = null

    private val nodeListObserver = Observer<PagedList<NetworkNode>?>{ t->
        run{
            nodeListRecyclerAdapter?.submitList(t)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_node_list, container, false)
        nodeListRecyclerView = root.findViewById(R.id.fragment_node_list_rv)
        nodeListRecyclerView.layoutManager = LinearLayoutManager(context)

        nodeListRecyclerAdapter = NodeListRecyclerAdapter()

        nodeListRecyclerView.adapter = nodeListRecyclerAdapter

        controller = NodeListController(requireContext(), retriever.database, this)
        controller.onCreate()

        return root


    }

    override var nodeList: DoorDataSourceFactory<Int, NetworkNode>? = null
        set(value) {
            nodeListLiveData?.removeObserver(nodeListObserver)
            nodeListLiveData = value?.asRepositoryLiveData(retriever.database.networkNodeDao)
            field = value
            nodeListLiveData?.observe(this, nodeListObserver)
        }


}