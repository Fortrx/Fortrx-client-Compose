package com.fortrx.crypto

fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
    }
    return diff == 0
}

fun constantTimeStringEquals(a: String, b: String): Boolean =
    constantTimeEquals(a.encodeToByteArray(), b.encodeToByteArray()) // FIXED: Constant-Time Password Comparison
