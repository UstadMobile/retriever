package com.ustadmobile.retriever.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem.Companion.MODE_INC_AVAILABLE_NODES
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.AvailabilityEvent
import com.ustadmobile.retriever.AvailabilityObserver
import com.ustadmobile.retriever.OnAvailabilityChanged
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.testapp.databinding.FragmentDownloadAvailabilityBinding
import com.ustadmobile.retriever.testapp.work.DownloadWorker
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance

class DownloadAvailabilityFragment: Fragment(), DIAware, OnAvailabilityChanged {

    override val di: DI by closestDI()

    private val retriever: Retriever by instance()

    private var mBinding: FragmentDownloadAvailabilityBinding? = null

    private var availabilityObserver: AvailabilityObserver? = null

    private var mConcatAdapter: ConcatAdapter? = null

    private var recyclerAdapterMap: Map<String,
            Pair<DownloadAvailabilityOriginUrlRecyclerViewAdapter, NodeListRecyclerAdapter>>? = null

    private var urls: List<String> = listOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        urls = arguments?.getString(ARG_URLS)?.let { listOf(it) }
            ?: throw IllegalArgumentException("No urls specified!")

        recyclerAdapterMap = urls.associateWith { url ->
            (DownloadAvailabilityOriginUrlRecyclerViewAdapter() to NodeListRecyclerAdapter())
        }

        val recyclerAdaptersList = recyclerAdapterMap?.entries?.map {
            listOf(it.value.first, it.value.second)
        }?.flatten() ?: listOf()

        AvailabilityObserver(urls, this, MODE_INC_AVAILABLE_NODES).also {
            availabilityObserver = it
            retriever.addAvailabilityObserver(it)
        }

        return FragmentDownloadAvailabilityBinding.inflate(inflater, container, false).also { binding ->
            mBinding = binding
            mConcatAdapter = ConcatAdapter(*recyclerAdaptersList.toTypedArray())
            binding.downloadAvailabilityRecyclerview.adapter = mConcatAdapter
            binding.downloadAvailabilityRecyclerview.layoutManager = LinearLayoutManager(requireContext())
            binding.downloadNowButton.setOnClickListener {
                val inputData = Data.Builder()
                    .putString(DownloadWorker.KEY_URL, urls.first())
                    .build()
                val workRequest = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
                    .setInputData(inputData)
                    .build()
                WorkManager.getInstance(requireContext()).enqueueUniqueWork(urls.first(), ExistingWorkPolicy.KEEP,
                    workRequest)
            }
        }.root
    }

    override fun onAvailabilityChanged(evt: AvailabilityEvent) {
        evt.availabilityInfo.values.forEach {
            val adapters = recyclerAdapterMap?.get(it.url) ?: return@forEach
            adapters.first.submitList(listOf(it))
            adapters.second.submitList(it.availableEndpoints.map { endpoint ->
                NetworkNode().apply {
                    networkNodeEndpointUrl = endpoint
                }
            })
        }
    }

    override fun onDestroyView() {
        availabilityObserver?.also { observer ->
            retriever.removeAvailabilityObserver(observer)
            availabilityObserver = null
        }

        super.onDestroyView()
    }

    companion object {

        const val ARG_URLS = "urls"

    }
}