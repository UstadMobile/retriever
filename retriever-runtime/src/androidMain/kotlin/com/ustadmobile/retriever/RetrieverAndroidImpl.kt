package com.ustadmobile.retriever

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.controller.NetworkNodeController
import com.ustadmobile.retriever.db.RetrieverDatabase
import java.net.InetAddress
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class RetrieverAndroidImpl(
    private val applicationContext: Context
): Retriever {

    var database: RetrieverDatabase? = null

    private var retrieverController: NetworkNodeController? = null

    init {
        database = DatabaseBuilder.databaseBuilder(
                applicationContext,
                RetrieverDatabase::class,
                DBNAME
            ).build()
        println("Database created $database")
        retrieverController = NetworkNodeController(applicationContext, database)

        startNSD()

    }

    private var mServiceName = ""

    private lateinit var mService: NsdServiceInfo

    private lateinit var nsdManager: NsdManager

    private val SERVICE_TYPE = "_ustadretriever._tcp"
    private val SERVICE_NAME = "UstadRetriever"

    private var listeningPort: Int = 4242

    private lateinit var server: NanoHTTPD

    fun startNSD() {

//        database = DatabaseBuilder.databaseBuilder(
//                applicationContext,
//                RetrieverDatabase::class,
//                DBNAME
//            ).build()

        //Start nanohttpd server
        server = object : NanoHTTPD(listeningPort){}
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

    override fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall {
        //TODO("Not yet implemented")
        // 1. Make requests to every node with a list of request urls
        // 2. Build RetrieverCall return it

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
            println("P2PManagerAndroid: Discovery Started..")
        }

        override fun onDiscoveryStopped(p0: String?) {
            println("P2PManagerAndroid: Discovery Stopped.")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            println("P2PManagerAndroid: onServiceFound: " + service.serviceName)
            println("P2PManagerAndroid: onServiceFound: "
                    + service.serviceName)

            when {
                !service.serviceType.startsWith(SERVICE_TYPE) -> {
                    println("Unknown Service Type: ${service.serviceType}")
                    println("P2PManagerAndroid: Unknown Service Type: " + service.serviceType)
                }
                service.serviceName == mServiceName -> {
                    println("P2PManagerAndroid: Same machine: $mServiceName")
                    println( "P2PManagerAndroid: onServiceFound: "
                            + service.serviceName)
                }

                service.serviceName.contains(SERVICE_NAME) -> {
                    nsdManager.resolveService(service, resolveListener)
                    println("P2PManagerAndroid: contains service name :" + service.serviceName
                            + " .. resolving..")
                }
            }
        }

        override fun onServiceLost(p0: NsdServiceInfo?) {
            println("P2PManagerAndroid: onServiceLost.")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            println( "P2PManagerAndroid: Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            println( "P2PManagerAndroid: Resolve Succeeded. $serviceInfo")

            if (serviceInfo.serviceName == mServiceName) {
                println( "P2PManagerAndroid: Same IP.")
                println("P2PManagerAndroid: Same IP ")
                return
            }
            mService = serviceInfo
            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host

            println("P2PManagerAndroid: host:port  "
                    + host + ":" + port)

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