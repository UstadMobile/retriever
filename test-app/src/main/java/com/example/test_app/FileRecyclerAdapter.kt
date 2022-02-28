package com.example.test_app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.ItemFileBinding
import com.ustadmobile.lib.db.entities.LocallyStoredFile

interface FileListener{
    fun deleteFile(availableFile: LocallyStoredFile)
    fun handleClickFile(availableFile: LocallyStoredFile)
}


class FilesRecyclerAdapter(val fileListener: FileListener):
    PagedListAdapter<
            LocallyStoredFile,
            FilesRecyclerAdapter.FilesRecyclerViewHolder>
        (DIFF_CALLBACK){

    inner class FilesRecyclerViewHolder(val itemBinding: ItemFileBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesRecyclerViewHolder {
        val itemBinding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false)
        itemBinding.listener = fileListener
        return FilesRecyclerViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: FilesRecyclerViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemBinding.availableFile = item
    }


    companion object {

        val DIFF_CALLBACK: DiffUtil.ItemCallback<LocallyStoredFile> = object
            : DiffUtil.ItemCallback<LocallyStoredFile>() {
            override fun areItemsTheSame(oldItem: LocallyStoredFile,
                                         newItem: LocallyStoredFile): Boolean {
                return oldItem.locallyStoredFileUid == newItem.locallyStoredFileUid
            }

            override fun areContentsTheSame(oldItem: LocallyStoredFile,
                                            newItem: LocallyStoredFile): Boolean {
                return oldItem.locallyStoredFileUid == newItem.locallyStoredFileUid &&
                        oldItem.locallyStoredFileUid == newItem.locallyStoredFileUid
            }

        }

    }
}