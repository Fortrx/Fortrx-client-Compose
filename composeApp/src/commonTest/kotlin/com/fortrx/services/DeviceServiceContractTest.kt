package com.fortrx.services

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceServiceContractTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun pairingCompleteRequestSerializesOnlyExpectedFields() {
        val payload = PairingCompleteRequest(
            pairing_token = "pair-token",
            code = "123456",
            identity_pub = "identity-public-key",
            device_name = "Desktop",
        )

        val encoded = json.encodeToString(payload)

        assertTrue(encoded.contains("\"pairing_token\":\"pair-token\""))
        assertTrue(encoded.contains("\"code\":\"123456\""))
        assertTrue(encoded.contains("\"identity_pub\":\"identity-public-key\""))
        assertTrue(encoded.contains("\"device_name\":\"Desktop\""))
        assertFalse(encoded.contains("identityBundle"))
        assertFalse(encoded.contains("private"))
    }

    @Test
    fun pairingResponseKeepsServerExpiryFields() {
        val response = PairingCompleteResponse(
            access_token = "access",
            refresh_token = "refresh",
            device_id = "device-1",
            access_expires_at = 123L,
            refresh_expires_at = 456L,
        )

        assertEquals("bearer", response.token_type)
        assertEquals(123L, response.access_expires_at)
        assertEquals(456L, response.refresh_expires_at)
    }
}
