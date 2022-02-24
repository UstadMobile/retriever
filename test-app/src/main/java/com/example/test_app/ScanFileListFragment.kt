package com.example.test_app

import android.Manifest
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.databinding.FragmentScanFileListBinding
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.controller.ScanFileListController
import com.ustadmobile.retriever.view.ScanFileListView


interface ClickAddScan{
    fun onClickAddFromUrl()
}

class ScanFileListFragment(val retriever: RetrieverAndroidImpl): Fragment(), ScanFileListView,
    ClickAddScan, WatchListListener {

    private lateinit var binding: FragmentScanFileListBinding

    private lateinit var controller: ScanFileListController

    private lateinit var watchListRecyclerView: RecyclerView

    private var watchListLiveData: LiveData<PagedList<AvailabilityFileWithNumNodes>>? = null

    private var watchListRecyclerAdapter : WatchListRecyclerAdapter? = null

    private val watchListObserver = Observer<PagedList<AvailabilityFileWithNumNodes>?>{ t->
        run{
            watchListRecyclerAdapter?.submitList(t)
        }
    }

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
        binding = FragmentScanFileListBinding.inflate(inflater, container, false).also{
            rootView = it.root
            it.listener = this
        }

        watchListRecyclerView = rootView.findViewById(R.id.fragment_scan_file_list_rv)
        watchListRecyclerView.layoutManager = LinearLayoutManager(context)

        watchListRecyclerAdapter = WatchListRecyclerAdapter(this)

        watchListRecyclerView.adapter = watchListRecyclerAdapter

        controller = ScanFileListController(
            requireContext(),
            retriever.database,
            this,
            retriever)
        controller.onCreate()

        return rootView

    }


    override var watchList: DoorDataSourceFactory<Int, AvailabilityFileWithNumNodes>? = null
        set(value) {
            watchListLiveData?.removeObserver(watchListObserver)
            watchListLiveData = value?.asRepositoryLiveData(retriever.database.availableFileDao)
            field = value
            watchListLiveData?.observe(this, watchListObserver)
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
        builder.setTitle(R.string.enter_url_to_scan)
        builder.setPositiveButton(R.string.scan) { dialog, which ->
            //TODO: check
            controller.addUrlToScan(urlEditText.text.toString())
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.cancel) { dialog, which ->
            dialog.dismiss()
        }
        builder.show()
    }

    override fun deleteFile(availableFile: AvailabilityFileWithNumNodes) {
        controller.removeFileUrl(availableFile)
    }

    override fun handleClickFile(availableFile: AvailabilityFileWithNumNodes) {
        //TODO: Go to available node list
    }


}