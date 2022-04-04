package com.ustadmobile.retriever

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ustadmobile.retriever.util.findAvailableRandomPort
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RetrieverAndroidImpl internal constructor(
    db: RetrieverDatabase,
    nsdServiceName: String,
    private val applicationContext: Context,
    availabilityChecker: AvailabilityChecker,
    originServerFetcher: OriginServerFetcher,
    localPeerFetcher: LocalPeerFetcher,
    json: Json,
    port: Int,
    retrieverCoroutineScope: CoroutineScope,
): RetrieverCommonJvm(
    db, nsdServiceName, availabilityChecker, originServerFetcher, localPeerFetcher, port, json, retrieverCoroutineScope,
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    private val dataStore: DataStore<Preferences>
        get() = applicationContext.dataStore

    val database = db

    //The remembered service name as per onServiceRegistered
    private var mServiceName = ""

    private lateinit var nsdManager: NsdManager

    private val serviceType = "_${nsdServiceName.lowercase()}._tcp"

    //as per https://developer.android.com/training/connect-devices-wirelessly/nsd
    // If the service name is exactly equal to the value provided onServiceRegistered (mServiceName), then this is the
    // local device itself.
    private fun NsdServiceInfo.isMatchingServiceOnOtherDevice(): Boolean {
        return !serviceName.equals(mServiceName) && serviceName.contains(nsdServiceName)
    }

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
            // Save the service name. Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            // As per https://developer.android.com/training/connect-devices-wirelessly/nsd
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
            Napier.d("RetrieverAndroidImpl: service found: ${service.serviceName}")
            if(service.isMatchingServiceOnOtherDevice()) {
                Napier.d("RetrieverAndroidImpl: resolving service: ${service.serviceName}")
                nsdManager.resolveService(
                    service,
                    ServiceFoundResolveListener(this@RetrieverAndroidImpl)
                )
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            Napier.d("RetrieverAndroidImpl: Lost peer.")
            if(service?.isMatchingServiceOnOtherDevice() == true) {
                nsdManager.resolveService(service, LostListener(this@RetrieverAndroidImpl))
            }
        }
    }

    //Node lost listener
    private inner class LostListener(val retriever: RetrieverCommon) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Napier.d( "RetrieverAndroidImpl: Lost Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Napier.d("RetrieverAndroidImpl: Lost Peer resolved: $serviceInfo.")

            retrieverCoroutineScope.launch {
                retriever.updateNetworkNodeLost(serviceInfo.httpEndpointUrl())
            }
        }
    }

    //Node found listener
    inner class ServiceFoundResolveListener(
        val retriever: RetrieverCommon
    ) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Napier.d( "RetrieverAndroidImpl ServiceFoundResolveListener: Resolve failed for $serviceInfo: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Napier.d("RetrieverAndroidImpl: Found Peer: ${serviceInfo.httpEndpointUrl()}")

            val networkNode = NetworkNode().apply {
                networkNodeEndpointUrl = serviceInfo.httpEndpointUrl()
                networkNodeDiscovered = systemTimeInMillis()
            }


            retrieverCoroutineScope.launch {
                retriever.handleNodeDiscovered(networkNode)
            }
        }
    }


    internal override fun start() {
        super.start()

        retrieverCoroutineScope.launch {
            val serverReady: RouterNanoHTTPD = awaitServer()

            withContext(Dispatchers.Main){
                startNSD(serverReady)
            }
        }
    }


    override suspend fun choosePort(): Int {
        val lastPortKey = stringPreferencesKey(PREFERENCES_KEY_PORT)
        val lastPort: Int = dataStore.data.map { preferences ->
            preferences[lastPortKey]?.toInt() ?: 0
        }.first()

        val portToUse = findAvailableRandomPort(preferred = lastPort)
        if(portToUse != lastPort) {
            //Save it to the preferences
            dataStore.edit { prefs ->
                prefs[lastPortKey] = portToUse.toString()
            }
        }

        return portToUse
    }

    private fun startNSD(server: RouterNanoHTTPD) {
        val serviceInfo = NsdServiceInfo().apply{
            serviceName = nsdServiceName
            serviceType = "_${nsdServiceName.lowercase()}._tcp"
            port = server.listeningPort
        }

        nsdManager = (applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun close() {
        Napier.d("RetrieverAndroidImpl: close!")
        super.close()

        nsdManager.unregisterService(registrationListener)
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

    companion object {

        /**
         * The key that is used for the DataStore. This will be used to save the port, so we can use the same port next
         * time.
         */
        const val DEFAULT_SETTINGS_FILENAME = "retriever_settings"

    }

}