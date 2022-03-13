package com.ustadmobile.retriever.ext

fun String.requirePrefix(prefix: String) = if(this.startsWith(prefix)) this else "$prefix$this"
