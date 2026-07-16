package io.bedrockbridge.bedrock.crypto;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.KeyAgreement;

/** P-384 ephemeral key generation and ECDH shared-secret derivation. */
public final class BedrockKeyAgreement {
    private BedrockKeyAgreement() {}

    /** Generates a fresh P-384 server key pair using the supplied CSPRNG. */
    public static KeyPair generate(SecureRandom random) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp384r1"), random);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("P-384 is unavailable", failure);
        }
    }

    /** Derives the raw P-384 ECDH secret. */
    public static byte[] derive(PrivateKey privateKey, PublicKey peerKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(privateKey);
            agreement.doPhase(peerKey, true);
            return agreement.generateSecret();
        } catch (java.security.GeneralSecurityException failure) {
            throw new BedrockValidationException("Unable to derive Bedrock session secret");
        }
    }
}
