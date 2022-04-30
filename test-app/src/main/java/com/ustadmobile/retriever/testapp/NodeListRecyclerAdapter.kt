package com.ustadmobile.retriever.testapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.testapp.databinding.ItemNetworknodeBinding

class NodeListRecyclerAdapter(): ListAdapter<NetworkNode, NodePagedListRecyclerAdapter.NodeListViewHolder> (
    NodePagedListRecyclerAdapter.DIFF_CALLBACK
){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodePagedListRecyclerAdapter.NodeListViewHolder {
        val itemBinding = ItemNetworknodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false)
        return NodePagedListRecyclerAdapter.NodeListViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: NodePagedListRecyclerAdapter.NodeListViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemBinding.node = item
    }
}