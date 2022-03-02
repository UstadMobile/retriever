package com.example.test_app

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
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.FragmentLocalFileListBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.controller.LocalFileListController
import com.ustadmobile.retriever.view.LocalFileListView


interface ClickAddLocalFile{
    fun onClickAddRandom()
    fun onClickAddFromUrl()
}

class LocalFileListFragment(val retriever: Retriever): Fragment(), LocalFileListView,
    ClickAddLocalFile, FileListener {

    private lateinit var binding: FragmentLocalFileListBinding

    private lateinit var controller: LocalFileListController

    private lateinit var localFileListRecyclerView: RecyclerView

    private var localFileListLiveData: LiveData<PagedList<LocallyStoredFile>>? = null

    private var localFileListRecyclerAdapter : FilesRecyclerAdapter? = null

    private val localFileListObserver = Observer<PagedList<LocallyStoredFile>?>{ t->
        run{
            localFileListRecyclerAdapter?.submitList(t)
        }
    }

    private var fabClicked: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Do if the permission is granted
        }
        else {
            // Do otherwise
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView: View
        binding = FragmentLocalFileListBinding.inflate(inflater, container, false).also{
            rootView = it.root
            it.listener = this
        }

        localFileListRecyclerView = rootView.findViewById(R.id.fragment_local_file_list_rv)
        localFileListRecyclerView.layoutManager = LinearLayoutManager(context)

        localFileListRecyclerAdapter = FilesRecyclerAdapter(this)

        localFileListRecyclerView.adapter = localFileListRecyclerAdapter

        controller = LocalFileListController(requireContext(), (retriever as RetrieverAndroidImpl).database, this)
        controller.onCreate()

        val fab: FloatingActionButton = rootView.findViewById(R.id.fragment_local_file_list_fab_add)

        fab.setOnClickListener {

            fabClicked = if (fabClicked) {
                fab.animate().rotation(-90f)
                showFabItems(rootView, View.GONE)
                false

            } else {
                fab.animate().rotation(45f)
                showFabItems(rootView, View.VISIBLE)
                true
            }
        }
        return rootView

    }

    private fun showFabItems(view: View, visibility: Int){
        if(visibility == View.VISIBLE) {
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_random).show()
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_url).show()
        }else{
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_random).hide()
            view.findViewById<FloatingActionButton>(R.id.fragment_local_file_list_fab_url).hide()
        }
        view.findViewById<TextView>(R.id.fragment_local_file_list_random_tv).visibility = visibility
        view.findViewById<TextView>(R.id.fragment_local_file_list_url_tv).visibility = visibility
    }

    override var localFileList: DoorDataSourceFactory<Int, LocallyStoredFile>? = null
        set(value) {
            localFileListLiveData?.removeObserver(localFileListObserver)
            localFileListLiveData = value?.asRepositoryLiveData(
                (retriever as RetrieverAndroidImpl).database.locallyStoredFileDao
            )
            field = value
            localFileListLiveData?.observe(this, localFileListObserver)
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