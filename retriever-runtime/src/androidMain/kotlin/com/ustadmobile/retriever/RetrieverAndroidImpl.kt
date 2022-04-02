package com.ustadmobile.retriever

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import java.net.InetAddress
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
            Napier.w("RetrieverAndroidImpl onRegistration Failed! ")
        }

        override fun onUnregistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            //Unreg failed.
            Napier.w("RetrieverAndroidImpl onUnregistration Failed! ")
        }

        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            mServiceName = nsdServiceInfo.serviceName
            Napier.d("RetrieverAndroidImpl: Registered ok: $mServiceName")
        }

        override fun onServiceUnregistered(p0: NsdServiceInfo?) {
            //Un registered ok
            Napier.d("RetrieverAndroidImpl: Unregistered ok.")
        }
    }

    private val discoveryListener = object: NsdManager.DiscoveryListener{
        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
            //failed to start discovery
            Napier.w("RetrieverAndroidImpl: onStartDiscoveryFailed !")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
            //failed on stop discovery
            Napier.w("RetrieverAndroidImpl: onStopDiscoveryFailed!")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onDiscoveryStarted(p0: String?) {
            //Discovery started
            Napier.d("RetrieverAndroidImpl: Discovery Started..")
        }

        override fun onDiscoveryStopped(p0: String?) {
            Napier.d("RetrieverAndroidImpl: Discovery Stopped.")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            //as per https://developer.android.com/training/connect-devices-wirelessly/nsd
            // if the service name is exactly equal, then this is the local device itself.
            Napier.d("RetrieverAndroidImpl: service found: ${service.serviceName}")
            if(!service.serviceName.equals(mServiceName) && service.serviceName.contains(nsdServiceName)) {
                Napier.d("RetrieverAndroidImpl: resolving service: ${service.serviceName}")
                nsdManager.resolveService(
                    service,
                    ServiceFoundResolveListener(this@RetrieverAndroidImpl)
                )
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            Napier.d("RetrieverAndroidImpl: Lost peer.")
            nsdManager.resolveService(service, LostListener(this@RetrieverAndroidImpl))
        }
    }

    //Node lost listener
    private class LostListener(val retriever: RetrieverCommon) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Napier.d( "RetrieverAndroidImpl: Lost Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Napier.d("RetrieverAndroidImpl: Lost Peer: $serviceInfo.")

            GlobalScope.launch {
                retriever.updateNetworkNodeLost(serviceInfo.httpEndpointUrl())
            }
        }
    }

    //Node found listener
    class ServiceFoundResolveListener(
        val retriever: RetrieverCommon
    ) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Napier.d( "RetrieverAndroidImpl: Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Napier.d( "RetrieverAndroidImpl: Resolve Succeeded. $serviceInfo")
            val allDeviceAddresses = Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap {
                Collections.list(it.inetAddresses)
            }

            //Avoid "discovering" our own local device
            if(serviceInfo.host in allDeviceAddresses){
                return
            }

            Napier.d("RetrieverAndroidImpl: Found Peer: ${serviceInfo.httpEndpointUrl()}")

            val networkNode = NetworkNode().apply {
                networkNodeEndpointUrl = serviceInfo.httpEndpointUrl()
                networkNodeDiscovered = systemTimeInMillis()
            }


            GlobalScope.launch {
                retriever.addNewNode(networkNode)
            }
        }
    }

    init {
        startNSD()
    }


    private fun startNSD() {

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

    override fun close() {
        Napier.d("RetrieverAndroidImpl: close!")
        super.close()

        nsdManager.unregisterService(registrationListener)
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

}