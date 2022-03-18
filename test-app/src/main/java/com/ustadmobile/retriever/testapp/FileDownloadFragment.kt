package com.ustadmobile.retriever.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ustadmobile.retriever.testapp.databinding.FragmentFileDownloadBinding
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI

class FileDownloadFragment: Fragment(), DIAware {

    override val di: DI by closestDI()

    private var mBinding: FragmentFileDownloadBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView: View
        mBinding = FragmentFileDownloadBinding.inflate(inflater, container, false).also {
            rootView = it.root
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mBinding = null
    }
}