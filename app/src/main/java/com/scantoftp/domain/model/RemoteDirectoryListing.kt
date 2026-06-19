package com.scantoftp.domain.model

data class RemoteDirectoryEntry(
    val name: String,
    val path: String,
)

data class RemoteDirectoryListing(
    val currentPath: String,
    val directories: List<RemoteDirectoryEntry>,
)
