package com.example.test_app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.ItemFileBinding
import com.ustadmobile.lib.db.entities.AvailableFile

class FilesRecyclerAdapter:
    PagedListAdapter<
            AvailableFile,
            FilesRecyclerAdapter.FilesRecyclerViewHolder>
        (DIFF_CALLBACK){

    inner class FilesRecyclerViewHolder(val itemBinding: ItemFileBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesRecyclerViewHolder {
        val itemBinding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false)
        return FilesRecyclerViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: FilesRecyclerViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemBinding.availableFile = item
    }


    companion object {

        val DIFF_CALLBACK: DiffUtil.ItemCallback<AvailableFile> = object
            : DiffUtil.ItemCallback<AvailableFile>() {
            override fun areItemsTheSame(oldItem: AvailableFile,
                                         newItem: AvailableFile): Boolean {
                return oldItem.availableFileUid == newItem.availableFileUid
            }

            override fun areContentsTheSame(oldItem: AvailableFile,
                                            newItem: AvailableFile): Boolean {
                return oldItem.availableFileUid == newItem.availableFileUid &&
                        oldItem.availableFileUid == newItem.availableFileUid
            }

        }

    }
}