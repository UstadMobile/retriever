package com.example.test_app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.ItemNetworknodeBinding
import com.ustadmobile.lib.db.entities.NetworkNode

class NodeListRecyclerAdapter:
    PagedListAdapter<
            NetworkNode,
            NodeListRecyclerAdapter.NodeListRecyclerViewHolder>
        (DIFF_CALLBACK){

    inner class NodeListRecyclerViewHolder(val itemBinding: ItemNetworknodeBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeListRecyclerViewHolder {
        val itemBinding = ItemNetworknodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false)
        return NodeListRecyclerViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: NodeListRecyclerViewHolder, position: Int) {
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
                return oldItem.networkNodeId == newItem.networkNodeId &&
                        oldItem.networkNodeId == newItem.networkNodeId
            }

        }

    }
}