package com.shoshiluna.executive.core.business

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Creates user-authentication-bound ECDSA keys. The Signature returned by newSigningOperation()
 * must be passed through BiometricPrompt.CryptoObject before sign() is allowed by Android.
 */
class ApprovalKeyManager {
    fun ensureKey(alias: String) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    fun newSigningOperation(alias: String): Signature {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = ks.getKey(alias, null) as? PrivateKey ?: error("Keystore private key missing")
        return Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
    }
}
