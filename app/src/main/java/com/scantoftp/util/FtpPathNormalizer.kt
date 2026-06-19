package com.scantoftp.util

object FtpPathNormalizer {
    fun normalize(baseDirectory: String, tripSubfolder: String): String {
        val base = normalizePath(baseDirectory, allowAbsolute = true)
        val trip = normalizePath(tripSubfolder, allowAbsolute = false)
        return when {
            base.isBlank() -> trip
            trip.isBlank() -> base
            base == "/" -> "/$trip"
            else -> "${base.trimEnd('/')}/$trip"
        }
    }

    fun segments(path: String): List<String> {
        val normalized = normalizePath(path, allowAbsolute = path.trim().startsWith("/"))
        return normalized.trim('/').split('/').filter { it.isNotBlank() }
    }

    private fun normalizePath(raw: String, allowAbsolute: Boolean): String {
        val trimmed = raw.trim().replace('\\', '/')
        if (trimmed.isBlank()) return ""

        val absolute = allowAbsolute && trimmed.startsWith("/")
        val segments = trimmed
            .split('/')
            .filter { it.isNotBlank() }
            .onEach { segment ->
                require(segment != "." && segment != "..") {
                    "Remote directories cannot contain '.' or '..' segments."
                }
            }

        val joined = segments.joinToString("/")
        return when {
            absolute && joined.isBlank() -> "/"
            absolute -> "/$joined"
            else -> joined
        }
    }
}
