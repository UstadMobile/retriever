package com.ustadmobile.retriever.testapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ustadmobile.retriever.testapp.databinding.ItemFileBinding
import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.entities.LocallyStoredFileAndDownloadJobItem

interface FileListener{
    fun deleteFile(availableFile: LocallyStoredFile)
    fun handleClickFile(availableFile: LocallyStoredFile)
}


class FilesRecyclerAdapter(
    val fileListener: FileListener
): ListAdapter<LocallyStoredFileAndDownloadJobItem, FilesRecyclerAdapter.FilesRecyclerViewHolder>(DIFF_CALLBACK){

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
        holder.itemBinding.storedFile = item?.locallyStoredFile
        holder.itemBinding.downloadJobItem = item?.downloadJobItem
    }


    companion object {

        val DIFF_CALLBACK: DiffUtil.ItemCallback<LocallyStoredFileAndDownloadJobItem> = object
            : DiffUtil.ItemCallback<LocallyStoredFileAndDownloadJobItem>() {
            override fun areItemsTheSame(
                oldItem: LocallyStoredFileAndDownloadJobItem,
                newItem: LocallyStoredFileAndDownloadJobItem
            ): Boolean {
                return oldItem.locallyStoredFile?.locallyStoredFileUid == newItem.locallyStoredFile?.locallyStoredFileUid
                        && oldItem.downloadJobItem?.djiUid == newItem.downloadJobItem?.djiUid
            }

            override fun areContentsTheSame(
                oldItem: LocallyStoredFileAndDownloadJobItem,
                newItem: LocallyStoredFileAndDownloadJobItem
            ) : Boolean {
                return oldItem.locallyStoredFile?.lsfFileSize == newItem.locallyStoredFile?.lsfFileSize
                        && oldItem.downloadJobItem?.djiBytesSoFar == newItem.downloadJobItem?.djiBytesSoFar
                        && oldItem.downloadJobItem?.djiTotalSize == newItem.downloadJobItem?.djiTotalSize
            }

        }

    }
}