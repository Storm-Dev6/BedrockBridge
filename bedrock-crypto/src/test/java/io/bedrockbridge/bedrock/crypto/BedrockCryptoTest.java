package io.bedrockbridge.bedrock.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class BedrockCryptoTest {
    @Test
    void peersDeriveSameSecretAndExchangeAuthenticatedPayload() {
        SecureRandom random = new SecureRandom();
        var firstKey = BedrockKeyAgreement.generate(random);
        var secondKey = BedrockKeyAgreement.generate(random);
        byte[] firstSecret = BedrockKeyAgreement.derive(firstKey.getPrivate(), secondKey.getPublic());
        byte[] secondSecret = BedrockKeyAgreement.derive(secondKey.getPrivate(), firstKey.getPublic());
        assertArrayEquals(firstSecret, secondSecret);
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        var sender = new BedrockSessionCipher(salt, firstSecret);
        var receiver = new BedrockSessionCipher(salt, secondSecret);
        assertArrayEquals(new byte[] {1, 2, 3}, receiver.decrypt(sender.encrypt(new byte[] {1, 2, 3})));
    }

    @Test
    void handshakeJwtUsesEs384SignatureShape() {
        String token = new HandshakeJwtSigner().sign(
                BedrockKeyAgreement.generate(new SecureRandom()), new byte[16]);
        assertEquals(3, token.split("\\.").length);
    }
}
