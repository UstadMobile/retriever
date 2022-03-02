package com.ustadmobile.retriever

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.soywiz.klock.DateTime
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.responder.RequestResponder
import java.net.InetAddress
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RetrieverAndroidImpl internal constructor(
        db: RetrieverDatabase,
        nsdServiceName: String,
        private val applicationContext: Context,
        availabilityChecker: AvailabilityChecker
    ): RetrieverCommon(db, nsdServiceName, availabilityChecker
) {

    init {
        //startNSD()
    }

    val database = db

    private var mServiceName = ""

    private lateinit var mService: NsdServiceInfo

    private lateinit var nsdManager: NsdManager

    private var SERVICE_TYPE = "_$nsdServiceName._tcp"

    private val server = RouterNanoHTTPD(0)

    //Node lost listener
    private val lostListener = object : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            println( "P2PManagerAndroid: Lost Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {

            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host

            println("P2PManagerAndroid: Lost Peer: $host:$port")

            GlobalScope.launch {
                updateNetworkNodeLost("$host:$port")
            }
        }
    }

    //Node found listener
    private val serviceFoundResolveListener = object : NsdManager.ResolveListener {

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
                addNewNode(networkNode)
            }
        }
    }

    //Service registered listener
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
            if(service.serviceName.startsWith(nsdServiceName)) {
                nsdManager.resolveService(service, serviceFoundResolveListener)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            println("P2PManagerAndroid: Lost peer.")
            nsdManager.resolveService(service, lostListener)
        }
    }

    fun startNSD() {

        //TODO: Check if already running
        GlobalScope.launch {
            db.networkNodeDao.clearAllNodes()
            db.availabilityResponseDao.clearAllResponses()
        }

        //Start nanohttpd server
        server.addRoute(
            "/:${RequestResponder.PARAM_FILE_REQUEST_URL}/",
            RequestResponder::class.java,
            db
        )
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

        val serviceInfo = NsdServiceInfo().apply{
            serviceName = nsdServiceName
            serviceType = "$nsdServiceName._tcp"
            port = server.listeningPort
        }

        nsdManager = (applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)


    }

    override fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall {


        //TODO: How to handle onAvailabilityChanged event?
        val onAvailabilityChanged : OnAvailabilityChanged = OnAvailabilityChanged {

        }
        val allUrls : List<String> = retrieverRequests.map { it.originUrl }
        val availabilityObserver = AvailabilityObserver(allUrls, onAvailabilityChanged)


        val listenerUid = availabilityManager.addAvailabilityObserver(availabilityObserver)


        //TODO: How to handle RetrieverCall ?
        return RetrieverCall(listenerUid)
    }



    companion object {
    }
}