package com.ustadmobile.retriever

import android.net.nsd.NsdServiceInfo

/**
 * For the given NSD service info return a string for the http endpoint url
 */
fun NsdServiceInfo.httpEndpointUrl() = "http://${host.hostAddress}:${port}/"
