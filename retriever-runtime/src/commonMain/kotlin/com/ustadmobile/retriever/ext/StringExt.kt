package com.ustadmobile.retriever.ext

fun String.requirePrefix(prefix: String) = if(this.startsWith(prefix)) this else "$prefix$this"

fun String.requirePostfix(postfix: String) = if(this.endsWith(postfix)) this else "$this$postfix"

