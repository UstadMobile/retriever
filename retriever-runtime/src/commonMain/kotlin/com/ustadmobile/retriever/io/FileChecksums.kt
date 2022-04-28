package com.ustadmobile.retriever.io

import com.ustadmobile.retriever.IntegrityChecksum

data class FileChecksums(val sha256: ByteArray?, val sha384: ByteArray?, val sha512: ByteArray?, val crc32: Long) {

    /**
     * Given a list of required IntegrityChecksum types, find those that have not yet been calculated
     */
    fun findUnsetChecksumTypes(checksumTypes: Array<IntegrityChecksum>) : Array<IntegrityChecksum> {
        val missingTypes = mutableListOf<IntegrityChecksum>()
        checksumTypes.forEach {
            when {
                it == IntegrityChecksum.SHA256 && sha256 == null -> missingTypes += IntegrityChecksum.SHA256
                it == IntegrityChecksum.SHA384 && sha384 == null -> missingTypes += IntegrityChecksum.SHA384
                it == IntegrityChecksum.SHA512 && sha512 == null -> missingTypes += IntegrityChecksum.SHA512
            }
        }

        return missingTypes.toTypedArray()
    }

    companion object {

        fun fromMap(map: Map<IntegrityChecksum, ByteArray>, crc32: Long): FileChecksums {
            return FileChecksums(map[IntegrityChecksum.SHA256], map[IntegrityChecksum.SHA384],
                map[IntegrityChecksum.SHA512], crc32)
        }

    }


}
