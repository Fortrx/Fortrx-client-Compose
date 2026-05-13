package com.fortrx.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Port of `client/crypto/ratchet.py` — Double Ratchet state machine.
 *
 * The on-wire byte format MUST stay compatible with the Python reference:
 *  - Header is JSON: { "dh": <b64>, "pn": Int, "n": Int, ...extra }
 *  - Message keys are derived per Signal Double Ratchet spec via HKDF/HMAC chains.
 *  - Skipped-key map keys are "<base64-dh-public>:<n>".
 */
@Serializable
data class RatchetState(
    val rootKey: ByteArray,
    var sendingChainKey: ByteArray? = null,
    var recvChainKey: ByteArray? = null,
    val dhSendingPrivate: ByteArray,
    val dhSendingPublic: ByteArray,
    var dhRemotePublic: ByteArray? = null,
    var recipientIkPublic: ByteArray? = null,
    var sendCount: Int = 0,
    var recvCount: Int = 0,
    var previousSendCount: Int = 0,
    /** Map key: "remoteDhB64:n", value: 32-byte message key. */
    val skippedMessageKeys: Map<String, ByteArray> = emptyMap(),

    // Handshake metadata for bootstrapping
    var x3dhEkPublic: ByteArray? = null,
    var x3dhIkPublic: ByteArray? = null,
    var x3dhOtpkUsed: Boolean = false,
    var x3dhOtpkPublic: ByteArray? = null,
    var x3dhPrekeyId: Int = 0,
    var x3dhIsPqxdh: Boolean = false,
    var x3dhKyberCt: ByteArray? = null,
)

const val MAX_SKIP = 1000
const val NONCE_SIZE = 12

@OptIn(ExperimentalEncodingApi::class)
object Ratchet {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun initSender(sharedSecret: ByteArray, recipientRatchetPublic: ByteArray): RatchetState {
        val kp = CryptoPrimitives.x25519Generate()
        val dhOut = CryptoPrimitives.x25519Diffie(kp.privateKey, recipientRatchetPublic)
        val (rk, ck) = kdfRk(sharedSecret, dhOut)
        return RatchetState(
            rootKey = rk, sendingChainKey = ck, recvChainKey = null,
            dhSendingPrivate = kp.privateKey, dhSendingPublic = kp.publicKey,
            dhRemotePublic = recipientRatchetPublic,
        )
    }

    fun initReceiver(sharedSecret: ByteArray, ourRatchetPrivate: ByteArray, ourRatchetPublic: ByteArray): RatchetState =
        RatchetState(
            rootKey = sharedSecret, sendingChainKey = null, recvChainKey = null,
            dhSendingPrivate = ourRatchetPrivate, dhSendingPublic = ourRatchetPublic,
            dhRemotePublic = null,
        )

    /** KDF_RK: derive new (rootKey, chainKey) from current rootKey and a DH output. */
    private fun kdfRk(rootKey: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val out = hkdfDerive(ikm = dhOut, salt = rootKey,
            info = "Fortrx Ratchet".encodeToByteArray(), length = 64)
        return out.copyOfRange(0, 32) to out.copyOfRange(32, 64)
    }

    /** KDF_CK: advance chain key, output (newChainKey, messageKey). */
    private fun kdfCk(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val mk = CryptoPrimitives.hmacSha256(chainKey, byteArrayOf(0x01))
        val nextCk = CryptoPrimitives.hmacSha256(chainKey, byteArrayOf(0x02))
        return nextCk to mk
    }

    private fun parseHeader(bytes: ByteArray): JsonObject =
        json.parseToJsonElement(bytes.decodeToString()).jsonObject

    private fun concatAd(associatedData: ByteArray, header: JsonObject): ByteArray {
        val headerBytes = json.encodeToString(header).encodeToByteArray()
        val len = associatedData.size
        val prefix = byteArrayOf(
            ((len ushr 24) and 0xFF).toByte(),
            ((len ushr 16) and 0xFF).toByte(),
            ((len ushr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte()
        )
        return prefix + associatedData + headerBytes
    }

    /** Encrypt: returns (newState, headerBytes, nonce||ciphertext||tag). */
    fun encrypt(state: RatchetState, plaintext: ByteArray, associatedData: ByteArray,
                headerExtra: JsonObject? = null):
            Triple<RatchetState, ByteArray, ByteArray> {
        val ck = state.sendingChainKey
            ?: error("Ratchet has no sending chain key — cannot encrypt")
        val (nextCk, mk) = kdfCk(ck)
        
        val headerObj = buildJsonObject {
            put("dh_public", Base64.encode(state.dhSendingPublic))
            put("pn", state.previousSendCount)
            val n = state.sendCount
            put("n", n)
            put("send_count", n + 1)
            put("recv_count", state.recvCount)
            headerExtra?.forEach { (k, v) -> put(k, v) }
        }
        val headerBytes = json.encodeToString(headerObj).encodeToByteArray()
        
        val nonce = CryptoPrimitives.randomBytes(NONCE_SIZE)
        val aad = concatAd(associatedData, headerObj)
        val ct = CryptoPrimitives.aesGcmEncrypt(mk, nonce, plaintext, aad)
        
        val newState = state.copy(sendingChainKey = nextCk, sendCount = state.sendCount + 1)
        return Triple(newState, headerBytes, nonce + ct)
    }

    /** Decrypt: returns (newState, plaintext). Performs DH ratchet step on remote DH change. */
    fun decrypt(state: RatchetState, headerBytes: ByteArray, ciphertext: ByteArray,
        associatedData: ByteArray): Pair<RatchetState, ByteArray> {
        if (ciphertext.size < NONCE_SIZE + 16) error("ciphertext too short")
        
        val header = parseHeader(headerBytes)
        val remoteDhB64 = header["dh_public"]?.jsonPrimitive?.content ?: error("missing dh_public")
        val remoteDh = Base64.decode(remoteDhB64)
        val n = header["n"]?.jsonPrimitive?.int ?: 0
        val pn = header["pn"]?.jsonPrimitive?.int ?: 0

        // 1. Try skipped keys first.
        val skipKey = "$remoteDhB64:$n"
        state.skippedMessageKeys[skipKey]?.let { mk ->
            val pt = decryptWithMk(mk, ciphertext, associatedData, header)
            val pruned = state.skippedMessageKeys.toMutableMap().also { it.remove(skipKey) }
            return state.copy(skippedMessageKeys = pruned) to pt
        }

        var working = state
        // 2. DH ratchet step if remote sent a new DH public.
        if (working.dhRemotePublic == null || !working.dhRemotePublic!!.contentEquals(remoteDh)) {
            working = skipMessageKeys(working, pn)
            working = dhRatchetStep(working, remoteDh)
        }
        // 3. Skip in current receiving chain up to header.n.
        working = skipMessageKeys(working, n)
        // 4. Advance receiving chain to derive this message's key.
        val (nextRck, mk) = kdfCk(working.recvChainKey ?: error("no recv chain"))
        working = working.copy(recvChainKey = nextRck, recvCount = working.recvCount + 1)
        val pt = decryptWithMk(mk, ciphertext, associatedData, header)
        return working to pt
    }

    private fun decryptWithMk(mk: ByteArray, ciphertext: ByteArray,
        associatedData: ByteArray, header: JsonObject): ByteArray {
        val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
        val data = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)
        val aad = concatAd(associatedData, header)
        return CryptoPrimitives.aesGcmDecrypt(mk, nonce, data, aad)
    }

    private fun skipMessageKeys(state: RatchetState, until: Int): RatchetState {
        if (state.recvChainKey == null || state.dhRemotePublic == null) return state
        if (state.recvCount + MAX_SKIP < until) error("Too many skipped messages (>$MAX_SKIP)")
        var ck = state.recvChainKey!!
        var n = state.recvCount
        val skipped = state.skippedMessageKeys.toMutableMap()
        val remoteB64 = Base64.encode(state.dhRemotePublic!!)
        while (n < until) {
            val (next, mk) = kdfCk(ck)
            skipped["$remoteB64:$n"] = mk
            ck = next
            n += 1
        }
        return state.copy(recvChainKey = ck, recvCount = n, skippedMessageKeys = skipped)
    }

    private fun dhRatchetStep(state: RatchetState, newRemoteDh: ByteArray): RatchetState {
        // Receiving chain from new DH(remote, ourCurrentPriv).
        val dh1 = CryptoPrimitives.x25519Diffie(state.dhSendingPrivate, newRemoteDh)
        val (rk1, rck) = kdfRk(state.rootKey, dh1)
        // Generate fresh DH pair, then sending chain.
        val newKp = CryptoPrimitives.x25519Generate()
        val dh2 = CryptoPrimitives.x25519Diffie(newKp.privateKey, newRemoteDh)
        val (rk2, sck) = kdfRk(rk1, dh2)
        return state.copy(
            rootKey = rk2,
            recvChainKey = rck,
            sendingChainKey = sck,
            dhSendingPrivate = newKp.privateKey,
            dhSendingPublic = newKp.publicKey,
            dhRemotePublic = newRemoteDh,
            previousSendCount = state.sendCount,
            sendCount = 0,
            recvCount = 0,
        )
    }
}
