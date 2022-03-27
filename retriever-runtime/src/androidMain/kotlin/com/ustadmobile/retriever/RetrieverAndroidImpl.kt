package com.ustadmobile.retriever

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.soywiz.klock.DateTime
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import com.ustadmobile.retriever.responder.AvailabilityResponder
import com.ustadmobile.retriever.responder.ZippedItemsResponder
import java.net.InetAddress
import fi.iki.elonen.router.RouterNanoHTTPD
import io.github.aakira.napier.Napier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.NetworkInterface
import java.util.*

class RetrieverAndroidImpl internal constructor(
    db: RetrieverDatabase,
    nsdServiceName: String,
    private val applicationContext: Context,
    availabilityChecker: AvailabilityChecker,
    originServerFetcher: OriginServerFetcher,
    localPeerFetcher: LocalPeerFetcher,
    json: Json,
): RetrieverCommonJvm(db, nsdServiceName, availabilityChecker, originServerFetcher, localPeerFetcher, json) {

    val database = db

    private var mServiceName = ""

    private lateinit var nsdManager: NsdManager

    private var SERVICE_TYPE = "_${nsdServiceName.lowercase()}._tcp"

    //Service registered listener
    private val registrationListener = object: NsdManager.RegistrationListener{
        override fun onRegistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            //Failed.
            Napier.w("P2PManager: onRegistration Failed! ")
        }

        override fun onUnregistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            //Unreg failed.
            Napier.w("P2PManager: onUnregistration Failed! ")
        }

        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            mServiceName = nsdServiceInfo.serviceName
            Napier.d("P2PManagerAndroid: Registered ok: " + mServiceName)
        }

        override fun onServiceUnregistered(p0: NsdServiceInfo?) {
            //Un registered ok
            Napier.d("P2PManagerAndroid: Unregistered ok.")
        }
    }

    private val discoveryListener = object: NsdManager.DiscoveryListener{
        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
            //failed to start discovery
            Napier.w("P2PManagerAndroid: onStartDiscoveryFailed !")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
            //failed on stop discovery
            Napier.w("P2PManagerAndroid: onStopDiscoveryFailed!")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onDiscoveryStarted(p0: String?) {
            //Discovery started
            Napier.d("P2PManagerAndroid: Discovery Started..")
        }

        override fun onDiscoveryStopped(p0: String?) {
            Napier.d("P2PManagerAndroid: Discovery Stopped.")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            if(!service.serviceName.equals(mServiceName) && service.serviceName.startsWith(nsdServiceName)) {
                nsdManager.resolveService(
                    service,
                    ServiceFoundResolveListener(mServiceName, this@RetrieverAndroidImpl)
                )
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            Napier.d("P2PManagerAndroid: Lost peer.")
            nsdManager.resolveService(service, LostListener(this@RetrieverAndroidImpl))
        }
    }

    //Node lost listener
    private class LostListener(val retriever: RetrieverCommon) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Napier.d( "P2PManagerAndroid: Lost Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {

            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host

            Napier.d("P2PManagerAndroid: Lost Peer: $host:$port")

            GlobalScope.launch {
                retriever.updateNetworkNodeLost("$host:$port")
            }
        }
    }

    //Node found listener
    class ServiceFoundResolveListener(
        val mServiceName: String,
        val retriever: RetrieverCommon
    ) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Napier.d( "P2PManagerAndroid: Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Napier.d( "P2PManagerAndroid: Resolve Succeeded. $serviceInfo")


            val allDeviceAddresses = Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap {
                Collections.list(it.inetAddresses)
            }

            if(serviceInfo.host in allDeviceAddresses){
                return
            }

            val port: Int = serviceInfo.port
            val host: InetAddress = serviceInfo.host

            Napier.d("P2PManagerAndroid: Found Peer: $host:$port")

            val resolvedEndpoint =
                if(!host.toString().contains("http:/")) {
                    if (host.toString().startsWith("/")) {
                        "http:/$host:$port/"
                    } else {
                        "http://$host:$port/"
                    }
                }else{
                    "$host:$port/"
                }
            val networkNode = NetworkNode()
            networkNode.networkNodeEndpointUrl = resolvedEndpoint
            networkNode.networkNodeDiscovered = DateTime.nowUnixLong()

            GlobalScope.launch {
                retriever.addNewNode(networkNode)
            }
        }
    }

    init {
        startNSD()
    }





    fun startNSD() {
        val serviceInfo = NsdServiceInfo().apply{
            serviceName = nsdServiceName
            serviceType = "_${nsdServiceName.lowercase()}._tcp"
            port = server.listeningPort
        }

        nsdManager = (applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    companion object {
    }
}