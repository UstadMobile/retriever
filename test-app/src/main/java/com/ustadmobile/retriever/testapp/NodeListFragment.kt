package com.ustadmobile.retriever.testapp

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import com.ustadmobile.retriever.testapp.databinding.FragmentNodeListBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverAndroidImpl
import com.ustadmobile.retriever.testapp.controller.NodeListController
import com.ustadmobile.retriever.testapp.view.NodeListView
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

interface ClickAddNode{
    fun clickAddNote()
}
class NodeListFragment(): Fragment(), NodeListView, NodeListener, ClickAddNode, DIAware {

    override val di: DI by closestDI()

    private val retriever: Retriever by instance()

    private var controller: NodeListController? = null

    private var nodeListLiveData: LiveData<PagedList<NetworkNode>>? = null

    private var nodeListRecyclerAdapter : NodePagedListRecyclerAdapter? = null

    private val nodeListObserver = Observer<PagedList<NetworkNode>?>{ t->
        run{
            nodeListRecyclerAdapter?.submitList(t)
        }
    }

    private val BT_ON_REQUEST_CODE = 87

    private var binding: FragmentNodeListBinding? = null

    private var fabClicked: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val rootView: View

        val deviceStartTime = systemTimeInMillis()

        Napier.d("Retriever: Got device list in ${systemTimeInMillis() - deviceStartTime}ms")

        binding = FragmentNodeListBinding.inflate(inflater, container, false).also {
            rootView = it.root
            it.listener = this
            it.fragmentNodeListRv.layoutManager = LinearLayoutManager(context)
            nodeListRecyclerAdapter = NodePagedListRecyclerAdapter(this)
            it.fragmentNodeListRv.adapter = nodeListRecyclerAdapter
        }

        controller = NodeListController(
            requireContext(), (retriever as RetrieverAndroidImpl).database, this)
        controller?.onCreate()


        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {

            val allDeviceAddrs = withContext(Dispatchers.IO) {
                Collections.list(NetworkInterface.getNetworkInterfaces()).filter { !it.isLoopback }.flatMap {
                    Collections.list(it.inetAddresses)
                }.filterIsInstance<Inet4Address>()
            }

            val listeningPort = retriever.listeningPort()

            binding?.localNodeAddr = allDeviceAddrs.joinToString { addr -> "http://${addr.hostAddress}:$listeningPort/" }
        }
    }


    private fun checkBluetooth(){
        val bluetoothManager: BluetoothManager =
            context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

        if(!bluetoothAdapter.isEnabled){
            val enableBluetoothIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            val requestLaunch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result -> if(result.resultCode == Activity.RESULT_OK){
                    startCompanionDevicePairing()
                }
            }
            requestLaunch.launch(enableBluetoothIntent)

        }else{
            startCompanionDevicePairing()
        }
    }

    private val deviceManager: CompanionDeviceManager by lazy {
        context?.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }


    fun startCompanionDevicePairing(){

        //Device filter
        val bluetoothDeviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            //.setNamePattern(Pattern.compile("My device"))
            //.addServiceUuid(ParcelUuid(UUID(0x123abcL, -1L)), null)
            .build()

        //Request
        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(bluetoothDeviceFilter)
            .setSingleDevice(false)
            .build()

        val allAssociations = deviceManager.associations

        deviceManager.associate(pairingRequest,
            object: CompanionDeviceManager.Callback() {

                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    Napier.d("P2P Test1")
                    //show
                    startIntentSenderForResult(
                        chooserLauncher,
                        BT_ON_REQUEST_CODE,
                        null, 0, 0, 0,null)

                    Napier.d("P2P Retriever: Found bluetooth device.")
                }

                override fun onFailure(error: CharSequence?) {
                    Napier.w("P2P Retriever : Failed association device Manager")
                }

            }, null)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            BT_ON_REQUEST_CODE ->when(resultCode){
                Activity.RESULT_OK ->{
                    val deviceToPair: BluetoothDevice? =
                        data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)

                    if(deviceToPair?.address != null) {
                        //Note: 18/Mar/22 this has been disabled - should be done via the retriever interface itself
//                        retriever.addNewNode(
//                            NetworkNode(
//                                deviceToPair.name + ":" + deviceToPair.address,
//                                deviceToPair.address,
//                                System.currentTimeMillis()
//                        ))

                    }
                    Napier.d("P2P selected")
                }
            }else -> super.onActivityResult(requestCode, resultCode, data)

        }
    }

    override var nodeList: DoorDataSourceFactory<Int, NetworkNode>? = null
        set(value) {
            nodeListLiveData?.removeObserver(nodeListObserver)
            nodeListLiveData = value?.asRepositoryLiveData((retriever as RetrieverAndroidImpl).database.networkNodeDao)
            field = value
            nodeListLiveData?.observe(this, nodeListObserver)
        }

    override fun onClickNode(node: NetworkNode) {
        val clipboard: ClipboardManager? = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("", node.networkNodeEndpointUrl)
        clipboard?.setPrimaryClip(clip)

        Toast.makeText(context, "Endpoint copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.fragmentNodeListRv?.adapter = null
        nodeListRecyclerAdapter = null
        binding = null
    }

    override fun onDeleteNode(node: NetworkNode) {
//        val endpointUrl = node.networkNodeEndpointUrl
//        if(!endpointUrl.isNullOrEmpty()){
//            retriever.updateNetworkNodeLost(endpointUrl)
//        }
    }

    override fun clickAddNote() {
        checkBluetooth()
    }


}