package com.fortrx.storage

expect object PlatformFileStorage {
    /**
     * Saves the bytes to internal storage and returns the unique local filename.
     */
    fun saveFile(bytes: ByteArray): String
    
    /**
     * Reads the bytes for the given local filename.
     */
    fun readFile(localFileName: String): ByteArray?
    
    /**
     * Deletes the local file.
     */
    fun deleteFile(localFileName: String)
}
