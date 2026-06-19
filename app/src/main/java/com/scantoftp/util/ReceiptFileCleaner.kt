package com.scantoftp.util

import java.io.File

object ReceiptFileCleaner {
    fun deletePaths(vararg paths: String?) {
        paths
            .filterNotNull()
            .filter { it.isNotBlank() }
            .forEach { path ->
                runCatching { File(path).delete() }
            }
    }
}
