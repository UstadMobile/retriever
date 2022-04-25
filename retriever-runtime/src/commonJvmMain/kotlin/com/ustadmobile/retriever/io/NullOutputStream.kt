package com.ustadmobile.retriever.io

import java.io.OutputStream

class NullOutputStream: OutputStream() {

    override fun write(p0: Int) {
        //do nothing
    }

    override fun write(b: ByteArray) {
        //do nothing
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        //do nothing
    }
}