package com.ustadmobile.retriever.testapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ustadmobile.retriever.AvailabilityEventInfo
import com.ustadmobile.retriever.testapp.databinding.ItemDownloadAvailabilityOriginurlBinding

/**
 * RecyclerViewAdapter used in DownloadAvailability that shows the actual file url
 */
class DownloadAvailabilityOriginUrlRecyclerViewAdapter(

) : ListAdapter<AvailabilityEventInfo, DownloadAvailabilityOriginUrlRecyclerViewAdapter.OriginUrlViewHolder>(DIFFUTIL){

    class OriginUrlViewHolder(
        val binding: ItemDownloadAvailabilityOriginurlBinding
    ): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OriginUrlViewHolder {
        return OriginUrlViewHolder(ItemDownloadAvailabilityOriginurlBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: OriginUrlViewHolder, position: Int) {
        holder.binding.downloadAvailabilityInfo = getItem(position)
    }

    companion object {

        val DIFFUTIL = object: DiffUtil.ItemCallback<AvailabilityEventInfo>() {
            override fun areItemsTheSame(oldItem: AvailabilityEventInfo, newItem: AvailabilityEventInfo): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: AvailabilityEventInfo, newItem: AvailabilityEventInfo): Boolean {
                return oldItem.available == newItem.available && oldItem.checksPending == newItem.checksPending
            }
        }

    }

}