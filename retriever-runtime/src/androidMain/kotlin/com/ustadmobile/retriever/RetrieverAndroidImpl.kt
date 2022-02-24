package com.ustadmobile.retriever

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.ParcelUuid
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.controller.RetrieverController
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.responder.EmbeddedHTTPD
import com.ustadmobile.retriever.responder.RequestResponder
import com.ustadmobile.retriever.view.RetrieverViewCallback
import java.net.InetAddress
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern

class RetrieverAndroidImpl(
    private val applicationContext: Context,
    val view: RetrieverViewCallback): Retriever {

    var database: RetrieverDatabase

    private var retrieverController: RetrieverController? = null

    init {
        database = DatabaseBuilder.databaseBuilder(
                applicationContext,
                RetrieverDatabase::class,
                DBNAME
            ).build()
        retrieverController = RetrieverController(applicationContext, database)

        //TODO: Can't access some variables here
        //startNSD()

    }

    private var mServiceName = ""

    private lateinit var mService: NsdServiceInfo

    private lateinit var nsdManager: NsdManager

    private var SERVICE_TYPE = "_ustadretriever._tcp"
    var SERVICE_NAME = "UstadRetriever"
    private var BLUETOOTHSERVICEUUID = 0x424abcL

    var listeningPort: Int = 42424

    private lateinit var server: NanoHTTPD

    fun startNSD() {

        //TODO: Check if already running
        GlobalScope.launch {
            database.networkNodeDao.clearAllNodes()
            database.availabilityResponseDao.clearAllResponses()
        }

        //Start nanohttpd server
        server = object : EmbeddedHTTPD(listeningPort, database){}
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)


        val serviceInfo = NsdServiceInfo().apply{
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = listeningPort
        }

        nsdManager = (applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)


    }

    /**
     * Called when bluetooth is available
     */
    fun startCompanionDevicePairing(){



    }

    override fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall {

        val activeNodes: List<NetworkNode> = database.networkNodeDao.findAllActiveNodes()
        val allRequestResponses : List<AvailabilityResponse> = retrieverRequests.flatMap{
            val thisRequest: RetrieverRequest = it

            val requestResponses : List<AvailabilityResponse> = activeNodes.map{
                    var nodeEndpoint = it.networkNodeEndpointUrl?:""
                    nodeEndpoint = if(nodeEndpoint.startsWith("/")){
                        nodeEndpoint.substring(1, nodeEndpoint.length)
                    }else{
                        nodeEndpoint
                    }
                    nodeEndpoint = if(nodeEndpoint.startsWith("http")){
                        nodeEndpoint
                    }else{
                        "http://$nodeEndpoint"
                    }

                    nodeEndpoint = nodeEndpoint + "/" +
                        RequestResponder.PARAM_FILE_REQUEST_URL + "?" +
                        RequestResponder.PARAM_FILE_REQUEST_URL + "=" +
                        thisRequest.originUrl


                    var connection: HttpURLConnection? = null
                    val fileAvailable: Boolean = try {
                        val url = URL(nodeEndpoint)
                        connection = url.openConnection() as HttpURLConnection
                        val responseStr = connection.inputStream.bufferedReader().readText()
                        val responseEntryList = Gson().fromJson<List<AvailableFile>>(
                            responseStr,
                            object: TypeToken<List<AvailableFile>>(){
                            }.type
                        )
                        responseEntryList.isNotEmpty()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        false
                    } catch (e: Throwable){
                        e.printStackTrace()
                        false
                    }finally {
                        connection?.disconnect()
                    }

                    AvailabilityResponse(
                        it.networkNodeId,
                        thisRequest.originUrl,
                        fileAvailable,
                        DateTime.nowUnixLong())
            }

            requestResponses
        }

        //Add these AvailabilityResponse responses to the database
        GlobalScope.launch {
            database.availabilityResponseDao.insertList(allRequestResponses)
        }

        return RetrieverCall()
    }

    private val registrationListener = object: NsdManager.RegistrationListener{
        override fun onRegistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            //Failed.
            println("P2PManager: onRegistration Failed! ")
        }

        override fun onUnregistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            //Unreg failed.
            println("P2PManager: onUnregistration Failed! ")
        }

        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            mServiceName = nsdServiceInfo.serviceName
            println("P2PManagerAndroid: Registered ok: " + mServiceName)
        }

        override fun onServiceUnregistered(p0: NsdServiceInfo?) {
            //Un registered ok
            println("P2PManagerAndroid: Unregistered ok.")
        }
    }

    private val discoveryListener = object: NsdManager.DiscoveryListener{
        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
            //failed to start discovery
            println("P2PManagerAndroid: onStartDiscoveryFailed !")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
            //failed on stop discovery
            println("P2PManagerAndroid: onStopDiscoveryFailed!")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onDiscoveryStarted(p0: String?) {
            //Discovery started
            //println("P2PManagerAndroid: Discovery Started..")
        }

        override fun onDiscoveryStopped(p0: String?) {
            println("P2PManagerAndroid: Discovery Stopped.")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            when {
                !service.serviceType.startsWith(SERVICE_TYPE) -> {
                    println("P2PManagerAndroid: Unknown Service Type: " + service.serviceType)
                }
                service.serviceName == mServiceName -> {
//                    println("P2PManagerAndroid: Same machine: $mServiceName")
                }

                service.serviceName.contains(SERVICE_NAME) -> {
                    nsdManager.resolveService(service, resolveListener)
                }
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            println("P2PManagerAndroid: Lost peer.")
            nsdManager.resolveService(service, ResolveLostListener(retrieverController))
        }
    }

    private class ResolveLostListener(val retrieverController: RetrieverController?) :
        NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            println( "P2PManagerAndroid: Lost Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {

            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host

            println("P2PManagerAndroid: Lost Peer: $host:$port")

            GlobalScope.launch {
                retrieverController?.updateNetworkNodeLost("$host:$port")
            }
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            println( "P2PManagerAndroid: Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            //println( "P2PManagerAndroid: Resolve Succeeded. $serviceInfo")

            if (serviceInfo.serviceName == mServiceName) {
                //println("P2PManagerAndroid: Same IP ")
                return
            }
            mService = serviceInfo
            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host

            println("P2PManagerAndroid: Found Peer: $host:$port")

            val networkNode = NetworkNode()
            networkNode.networkNodeIPAddress = host.toString()
            networkNode.networkNodeEndpointUrl = "$host:$port"
            networkNode.networkNodeDiscovered = DateTime.nowUnixLong()

            GlobalScope.launch {
                retrieverController?.addNewNode(networkNode)
            }
        }
    }

    private fun addWatchList(fileOriginUrls: List<String>){

    }

    companion object {
        private const val DBNAME: String = "retreiverdb"
    }
}