package com.ustadmobile.retriever.testapp

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.lib.db.entities.LocallyStoredFileAndDownloadJobItem
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.testapp.controller.FileListController
import com.ustadmobile.retriever.testapp.databinding.FragmentFileListBinding
import com.ustadmobile.retriever.testapp.view.FileListView
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance


interface ClickAddLocalFile{
    fun onClickAddRandom()
    fun onClickAddFromUrl()
}

class FileListFragment(): Fragment(), FileListView, ClickAddLocalFile, FileListener, DIAware {

    override val di: DI by closestDI()

    private var binding: FragmentFileListBinding? = null

    private lateinit var controller: FileListController

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
            it.listener = this
        }

        binding?.fragmentLocalFileListRv?.layoutManager = LinearLayoutManager(context)

        localFileListRecyclerAdapter = FilesRecyclerAdapter(this)

        binding?.fragmentLocalFileListRv?.adapter = localFileListRecyclerAdapter

        controller = FileListController(requireContext(), (retriever as RetrieverAndroidImpl).database, this)
        controller.onCreate()

        val fab: FloatingActionButton = rootView.findViewById(R.id.fragment_local_file_list_fab_add)

        fab.setOnClickListener {
            findNavController().navigate(R.id.enterdownloadurl_dest)
        }
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

    override fun onClickAddRandom() {
        controller.addRandomFile()
    }

    override fun onClickAddFromUrl() {

        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_text_edit_entry,
            null,
            false)
        val urlEditText = dialogView.findViewById<EditText>(R.id.dialog_text_edit_entry_et)
        val catPicBtn = dialogView.findViewById<Button>(R.id.dialog_text_edit_entry_cat_btn)
        val pigeonPicBtn = dialogView.findViewById<Button>(R.id.dialog_text_edit_entry_pigeon_btn)
        val clearBtn = dialogView.findViewById<ImageButton>(R.id.dialog_text_edit_entry_clear_btn)

        catPicBtn.setOnClickListener(View.OnClickListener {
            urlEditText.setText("https://github.com/UstadMobile/retriever/raw/main/retriever-runtime/src/jvmTest/resources/cat-pic0.jpg")
        })
        pigeonPicBtn.setOnClickListener(View.OnClickListener {
            urlEditText.setText("https://github.com/UstadMobile/retriever/raw/main/retriever-runtime/src/jvmTest/resources/pigeon1.png")
        })
        clearBtn.setOnClickListener(View.OnClickListener {
            urlEditText.setText("")
        })


        builder.setView(dialogView)
        builder.setTitle(R.string.enter_url)
        builder.setPositiveButton(R.string.download) { dialog, which ->
            downloadFile(urlEditText.text.toString())
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.cancel) { dialog, which ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun downloadFile(url: String){

        val uri: Uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)

        val fileName = URLUtil.guessFileName(url, null, null)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) // to notify when download is complete

        val manager = context?.getSystemService(DOWNLOAD_SERVICE) as DownloadManager?
        manager?.enqueue(request)


        val localFilePath = "file://"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()+fileName
        controller.addDownloadedFile(url, localFilePath, 0)

    }


    override fun deleteFile(availableFile: LocallyStoredFile) {
        controller.deleteFile(availableFile)
    }

    override fun handleClickFile(availableFile: LocallyStoredFile) {
        val clipboard: ClipboardManager? = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("", availableFile.lsfOriginUrl)
        clipboard?.setPrimaryClip(clip)

        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()


    }




}