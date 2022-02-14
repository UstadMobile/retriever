package com.example.test_app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.ItemFileAvailableBinding
import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes

interface WatchListListener{
    fun deleteFile(availableFile: AvailabilityFileWithNumNodes)
    fun handleClickFile(availableFile: AvailabilityFileWithNumNodes)
}


class WatchListRecyclerAdapter(val fileListener: WatchListListener):
    PagedListAdapter<
            AvailabilityFileWithNumNodes,
            WatchListRecyclerAdapter.WatchListRecyclerViewHolder>
        (DIFF_CALLBACK){

    inner class WatchListRecyclerViewHolder(val itemBinding: ItemFileAvailableBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchListRecyclerViewHolder {
        val itemBinding = ItemFileAvailableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false)
        itemBinding.listener = fileListener
        return WatchListRecyclerViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: WatchListRecyclerViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemBinding.availableFileWithNumNodes = item
    }


    companion object {

        val DIFF_CALLBACK: DiffUtil.ItemCallback<AvailabilityFileWithNumNodes> = object
            : DiffUtil.ItemCallback<AvailabilityFileWithNumNodes>() {
            override fun areItemsTheSame(oldItem: AvailabilityFileWithNumNodes,
                                         newItem: AvailabilityFileWithNumNodes): Boolean {
                return oldItem.aoiId == newItem.aoiId
            }

            override fun areContentsTheSame(oldItem: AvailabilityFileWithNumNodes,
                                            newItem: AvailabilityFileWithNumNodes): Boolean {
                return oldItem.aoiId == newItem.aoiId &&
                        oldItem.aoiId == newItem.aoiId
            }

        }

    }
}