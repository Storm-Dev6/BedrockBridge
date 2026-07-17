package io.bedrockbridge.bedrock.crypto;

import io.bedrockbridge.bedrock.auth.EcdsaSignatureCodec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

/** Produces the compact ES384 server handshake JWT containing the session salt. */
public final class HandshakeJwtSigner {
  /** Signs a handshake token with the ephemeral P-384 server key. */
  public String sign(KeyPair serverKey, byte[] salt) {
    Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
    String publicKey = Base64.getEncoder().encodeToString(serverKey.getPublic().getEncoded());
    String header = "{\"alg\":\"ES384\",\"x5u\":\"" + publicKey + "\"}";
    String payload = "{\"salt\":\"" + Base64.getEncoder().encodeToString(salt) + "\"}";
    String encodedHeader = url.encodeToString(header.getBytes(StandardCharsets.UTF_8));
    String encodedPayload = url.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signingInput = encodedHeader + '.' + encodedPayload;
    try {
      Signature signer = Signature.getInstance("SHA384withECDSA");
      signer.initSign(serverKey.getPrivate());
      signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      byte[] signature = EcdsaSignatureCodec.derToP1363(signer.sign());
      return signingInput + '.' + url.encodeToString(signature);
    } catch (java.security.GeneralSecurityException failure) {
      throw new IllegalStateException("Unable to sign Bedrock handshake JWT", failure);
    }
  }
}
