package com.fortrx.services

expect object ArchiveZip {
    fun zip(entries: Map<String, ByteArray>): ByteArray
    fun unzip(bytes: ByteArray): Map<String, ByteArray>
}
