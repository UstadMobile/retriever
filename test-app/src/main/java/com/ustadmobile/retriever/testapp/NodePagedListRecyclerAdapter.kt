package com.ustadmobile.retriever.testapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ustadmobile.retriever.testapp.databinding.ItemNetworknodeBinding
import com.ustadmobile.lib.db.entities.NetworkNode

interface NodeListener{
    fun onClickNode(node: NetworkNode)

    fun onDeleteNode(node: NetworkNode)
}

class NodePagedListRecyclerAdapter(val listener: NodeListener):
    PagedListAdapter<
            NetworkNode,
            NodePagedListRecyclerAdapter.NodeListViewHolder>
        (DIFF_CALLBACK){

    class NodeListViewHolder(val itemBinding: ItemNetworknodeBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeListViewHolder {
        val itemBinding = ItemNetworknodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false)
        itemBinding.listener = listener
        return NodeListViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: NodeListViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemBinding.node = item
    }


    companion object {

        val DIFF_CALLBACK: DiffUtil.ItemCallback<NetworkNode> = object
            : DiffUtil.ItemCallback<NetworkNode>() {
            override fun areItemsTheSame(oldItem: NetworkNode,
                                         newItem: NetworkNode): Boolean {
                return oldItem.networkNodeId == newItem.networkNodeId
            }

            override fun areContentsTheSame(oldItem: NetworkNode,
                                            newItem: NetworkNode): Boolean {
                return oldItem.networkNodeEndpointUrl == newItem.networkNodeEndpointUrl
                        && oldItem.lastSuccessTime == newItem.lastSuccessTime
                        && oldItem.networkNodeStatus == newItem.networkNodeStatus
            }

        }

    }
}