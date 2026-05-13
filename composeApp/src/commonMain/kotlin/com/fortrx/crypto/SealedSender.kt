package com.fortrx.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Port of `client/crypto/sealed_sender.py`.
 *
 * Wire format (FSS2):
 *   FSS2 || ephemeral_public (32) || e_ciphertext (32) || e_mac (32) || s_ciphertext (N) || s_mac (32)
 */
@OptIn(ExperimentalEncodingApi::class)
object SealedSender {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val FSS2 = "FSS2".encodeToByteArray()
    private const val IDENTITY_KEY_SIZE = 32
    private const val MAC_SIZE = 32
    private val CTR_IV = ByteArray(16)
    private val UNIDENTIFIED_DELIVERY_LABEL = "UnidentifiedDelivery".encodeToByteArray()

    @Serializable
    private data class InnerWire(
        val sender_id: Long,
        val ciphertext: String, // b64
        val header: String // JSON b64
    )

    data class SealedEnvelope(val blob: ByteArray)

    fun seal(
        senderId: Long,
        senderIkPrivate: ByteArray,
        senderIkPublic: ByteArray,
        recipientIkPublic: ByteArray,
        ciphertext: ByteArray,
        headerJson: String
    ): SealedEnvelope {
        // 1. Ephemeral layer
        val ephemeralKp = CryptoPrimitives.x25519Generate()
        val ephemeralSharedSecret = CryptoPrimitives.x25519Diffie(ephemeralKp.privateKey, recipientIkPublic)
        val ephemeralMaterial = hkdfDerive(
            ikm = ephemeralSharedSecret,
            salt = UNIDENTIFIED_DELIVERY_LABEL + recipientIkPublic + ephemeralKp.publicKey,
            info = byteArrayOf(),
            length = 96
        )
        val eChain = ephemeralMaterial.copyOfRange(0, 32)
        val eCipherKey = ephemeralMaterial.copyOfRange(32, 64)
        val eMacKey = ephemeralMaterial.copyOfRange(64, 96)
        
        val eCiphertext = CryptoPrimitives.aesCtr(eCipherKey, CTR_IV, senderIkPublic)
        val eMac = CryptoPrimitives.hmacSha256(eMacKey, eCiphertext)

        // 2. Sender layer
        val senderSharedSecret = CryptoPrimitives.x25519Diffie(senderIkPrivate, recipientIkPublic)
        val senderMaterial = hkdfDerive(
            ikm = senderSharedSecret,
            salt = eChain + eCiphertext + eMac,
            info = byteArrayOf(),
            length = 64
        )
        val sCipherKey = senderMaterial.copyOfRange(0, 32)
        val sMacKey = senderMaterial.copyOfRange(32, 64)

        val inner = InnerWire(
            sender_id = senderId,
            ciphertext = Base64.encode(ciphertext),
            header = Base64.encode(headerJson.encodeToByteArray())
        )
        val innerJson = json.encodeToString(InnerWire.serializer(), inner).encodeToByteArray()
        val sCiphertext = CryptoPrimitives.aesCtr(sCipherKey, CTR_IV, innerJson)
        val sMac = CryptoPrimitives.hmacSha256(sMacKey, sCiphertext)

        val blob = FSS2 + ephemeralKp.publicKey + eCiphertext + eMac + sCiphertext + sMac
        return SealedEnvelope(blob)
    }

    data class OpenedEnvelope(val senderId: Long, val ciphertext: ByteArray, val headerJson: String, val senderIkPublic: ByteArray)

    fun open(recipientIkPrivate: ByteArray, recipientIkPublic: ByteArray, envelope: ByteArray): OpenedEnvelope {
        if (!envelope.take(4).toByteArray().contentEquals(FSS2)) {
            error("Unsupported envelope version (expected FSS2)")
        }

        var offset = FSS2.size
        val ephemeralPublic = envelope.copyOfRange(offset, offset + IDENTITY_KEY_SIZE)
        offset += IDENTITY_KEY_SIZE
        val eCiphertext = envelope.copyOfRange(offset, offset + IDENTITY_KEY_SIZE)
        offset += IDENTITY_KEY_SIZE
        val eMac = envelope.copyOfRange(offset, offset + MAC_SIZE)
        offset += MAC_SIZE
        val sCiphertext = envelope.copyOfRange(offset, envelope.size - MAC_SIZE)
        val sMac = envelope.copyOfRange(envelope.size - MAC_SIZE, envelope.size)

        // 1. Unseal ephemeral
        val ephemeralSharedSecret = CryptoPrimitives.x25519Diffie(recipientIkPrivate, ephemeralPublic)
        val ephemeralMaterial = hkdfDerive(
            ikm = ephemeralSharedSecret,
            salt = UNIDENTIFIED_DELIVERY_LABEL + recipientIkPublic + ephemeralPublic,
            info = byteArrayOf(),
            length = 96
        )
        val eChain = ephemeralMaterial.copyOfRange(0, 32)
        val eCipherKey = ephemeralMaterial.copyOfRange(32, 64)
        val eMacKey = ephemeralMaterial.copyOfRange(64, 96)

        val expectedEMac = CryptoPrimitives.hmacSha256(eMacKey, eCiphertext)
        if (!expectedEMac.contentEquals(eMac)) error("sealed sender identity MAC verification failed")

        val senderIkPublic = CryptoPrimitives.aesCtr(eCipherKey, CTR_IV, eCiphertext)

        // 2. Unseal sender
        val senderSharedSecret = CryptoPrimitives.x25519Diffie(recipientIkPrivate, senderIkPublic)
        val senderMaterial = hkdfDerive(
            ikm = senderSharedSecret,
            salt = eChain + eCiphertext + eMac,
            info = byteArrayOf(),
            length = 64
        )
        val sCipherKey = senderMaterial.copyOfRange(0, 32)
        val sMacKey = senderMaterial.copyOfRange(32, 64)

        val expectedSMac = CryptoPrimitives.hmacSha256(sMacKey, sCiphertext)
        if (!expectedSMac.contentEquals(sMac)) error("sealed sender message MAC verification failed")

        val innerJson = CryptoPrimitives.aesCtr(sCipherKey, CTR_IV, sCiphertext).decodeToString()
        val inner = json.decodeFromString(InnerWire.serializer(), innerJson)

        return OpenedEnvelope(
            senderId = inner.sender_id,
            ciphertext = Base64.decode(inner.ciphertext),
            headerJson = Base64.decode(inner.header).decodeToString(),
            senderIkPublic = senderIkPublic
        )
    }
}
