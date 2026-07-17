package io.bedrockbridge.bedrock.auth;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

/** Parsed compact ES384 JWT with strict JSON and signature verification. */
public final class JwtToken {
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private final String encodedHeader;
  private final String encodedPayload;
  private final byte[] signature;
  private final Map<String, Object> header;
  private final Map<String, Object> claims;

  private JwtToken(
      String encodedHeader,
      String encodedPayload,
      byte[] signature,
      Map<String, Object> header,
      Map<String, Object> claims) {
    this.encodedHeader = encodedHeader;
    this.encodedPayload = encodedPayload;
    this.signature = signature.clone();
    this.header = Map.copyOf(header);
    this.claims = Map.copyOf(claims);
  }

  /** Parses exactly three URL-safe, unpadded JWT components. */
  public static JwtToken parse(String compact) {
    if (compact == null || compact.length() > 256 * 1024) {
      throw new BedrockValidationException("JWT exceeds size limit");
    }
    String[] parts = compact.split("\\.", -1);
    if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
      throw new BedrockValidationException("JWT must contain three nonempty components");
    }
    try {
      Map<String, Object> header =
          StrictJson.parseObject(new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8));
      Map<String, Object> claims =
          StrictJson.parseObject(new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8));
      if (!"ES384".equals(header.get("alg"))) {
        throw new BedrockValidationException("Only ES384 JWT is accepted");
      }
      byte[] signature = URL_DECODER.decode(parts[2]);
      if (signature.length != 96) {
        throw new BedrockValidationException("ES384 JWT signature must be 96 bytes");
      }
      return new JwtToken(parts[0], parts[1], signature, header, claims);
    } catch (IllegalArgumentException invalid) {
      throw new BedrockValidationException("Invalid JWT Base64URL encoding");
    }
  }

  /** Verifies the JWT signing input with a P-384 public key. */
  public void verify(PublicKey publicKey) {
    try {
      Signature verifier = Signature.getInstance("SHA384withECDSA");
      verifier.initVerify(publicKey);
      verifier.update((encodedHeader + '.' + encodedPayload).getBytes(StandardCharsets.US_ASCII));
      if (!verifier.verify(EcdsaSignatureCodec.p1363ToDer(signature))) {
        throw new BedrockValidationException("JWT signature verification failed");
      }
    } catch (BedrockValidationException failure) {
      throw failure;
    } catch (java.security.GeneralSecurityException failure) {
      throw new BedrockValidationException("Unable to verify ES384 JWT");
    }
  }

  /** Returns the encoded protected header component. */
  public String encodedHeader() {
    return encodedHeader;
  }

  /** Returns the encoded claims component. */
  public String encodedPayload() {
    return encodedPayload;
  }

  /** Returns a defensive copy of the P1363 signature. */
  public byte[] signature() {
    return signature.clone();
  }

  /** Returns the immutable protected header. */
  public Map<String, Object> header() {
    return header;
  }

  /** Returns the immutable claims. */
  public Map<String, Object> claims() {
    return claims;
  }
}
