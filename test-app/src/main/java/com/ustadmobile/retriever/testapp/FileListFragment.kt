package com.ustadmobile.retriever.testapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.entities.LocallyStoredFileAndDownloadJobItem
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.testapp.databinding.FragmentFileListBinding
import com.ustadmobile.retriever.testapp.view.FileListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance
import java.io.File


class FileListFragment(): Fragment(), FileListView, FileListener, DIAware {

    override val di: DI by closestDI()

    private var binding: FragmentFileListBinding? = null

    private var localFileListRecyclerAdapter : FilesRecyclerAdapter? = null

    private val retriever: Retriever by instance()

    private val localFileListObserver = Observer<List<LocallyStoredFileAndDownloadJobItem>?>{ t->
        localFileListRecyclerAdapter?.submitList(t)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View
        binding = FragmentFileListBinding.inflate(inflater, container, false).also{
            rootView = it.root
            it.fragmentLocalFileListFabAdd.setOnClickListener {
                findNavController().navigate(R.id.enterdownloadurl_dest)
            }
        }

        binding?.fragmentLocalFileListRv?.layoutManager = LinearLayoutManager(context)

        localFileListRecyclerAdapter = FilesRecyclerAdapter(this)

        binding?.fragmentLocalFileListRv?.adapter = localFileListRecyclerAdapter

        val db = (retriever as RetrieverAndroidImpl).database
        localFileList = db.locallyStoredFileDao.findAllAvailableFilesLive()

        return rootView

    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.fragmentLocalFileListRv?.adapter = null
        binding = null
    }

    override var localFileList: LiveData<List<LocallyStoredFileAndDownloadJobItem>>? = null
        set(value) {
            field?.removeObserver(localFileListObserver)
            field = value
            field?.observe(viewLifecycleOwner, localFileListObserver)
        }



    override fun deleteFile(availableFile: LocallyStoredFile) {
        GlobalScope.launch(Dispatchers.IO) {
            retriever.deleteFilesByUrl(listOfNotNull(availableFile.lsfOriginUrl))
        }
    }

    override fun handleClickFile(availableFile: LocallyStoredFile) {
        //open it
        val filePath = availableFile.lsfFilePath ?: return
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider",
            file)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: "*/*"
        intent.setDataAndType(uri, mimeType)
        if(intent.resolveActivity(requireContext().packageManager) != null) {
            requireContext().startActivity(intent)
        }else {
            Toast.makeText(requireContext(), R.string.no_app_found, Toast.LENGTH_LONG)
        }
    }




}