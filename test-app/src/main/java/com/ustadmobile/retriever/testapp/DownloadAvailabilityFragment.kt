package com.ustadmobile.retriever.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.testapp.databinding.FragmentDownloadAvailabilityBinding
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance

class DownloadAvailabilityFragment: Fragment(), DIAware {

    override val di: DI by closestDI()

    private val retriever: Retriever by instance()

    private val mBinding: FragmentDownloadAvailabilityBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        return null

    }


    override fun onDestroyView() {
        super.onDestroyView()
    }

    companion object {

        const val ARG_URLS = "urls"

    }
}