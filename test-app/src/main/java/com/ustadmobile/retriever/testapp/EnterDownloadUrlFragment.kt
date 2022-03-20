package com.ustadmobile.retriever.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ustadmobile.retriever.testapp.databinding.FragmentEnterDownloadUrlBinding
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI

class EnterDownloadUrlFragment: Fragment(), DIAware {

    override val di: DI by closestDI()

    private var mBinding: FragmentEnterDownloadUrlBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView: View
        mBinding = FragmentEnterDownloadUrlBinding.inflate(inflater, container, false).also {
            rootView = it.root
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mBinding = null
    }
}