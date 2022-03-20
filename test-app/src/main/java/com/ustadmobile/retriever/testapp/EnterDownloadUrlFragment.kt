package com.ustadmobile.retriever.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ustadmobile.retriever.testapp.databinding.FragmentEnterDownloadUrlBinding
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI

class EnterDownloadUrlFragment: Fragment(), DIAware {

    override val di: DI by closestDI()

    private var mBinding: FragmentEnterDownloadUrlBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView: View
        mBinding = FragmentEnterDownloadUrlBinding.inflate(inflater, container, false).also { binding ->
            rootView = binding.root
            binding.entryImportLinkNextButton.setOnClickListener {
                findNavController().navigate(R.id.downloadavailability_dest,
                    bundleOf(DownloadAvailabilityFragment.ARG_URLS to binding.entryImportLinkEditText.text.toString()))
            }
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mBinding = null
    }
}