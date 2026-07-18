package io.bedrockbridge.registry.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** Loads one externally supplied X.509 P-384 public key without accepting private key material. */
public final class ExternalPublicKeyLoader {
  private static final int MAX_KEY_BYTES = 16 * 1024;

  private ExternalPublicKeyLoader() {}

  /** Loads a DER SubjectPublicKeyInfo or PEM PUBLIC KEY from outside the repository. */
  public static PublicKey load(Path path) throws IOException {
    RepositoryBoundary.requireOutsideGitWorkTree(path);
    if (path == null || !Files.isRegularFile(path)) {
      throw new IOException("Trusted public-key file is missing: " + path);
    }
    byte[] source = Files.readAllBytes(path);
    if (source.length == 0 || source.length > MAX_KEY_BYTES) {
      throw new IOException("Trusted public-key file exceeds the bounded size");
    }
    byte[] der = decodePemOrDer(source);
    try {
      PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
      if (!(key instanceof ECPublicKey ec)
          || !Arrays.equals(key.getEncoded(), der)
          || ec.getParams().getCurve().getField().getFieldSize() != 384) {
        throw new IOException("Trusted public key is not an EC SubjectPublicKeyInfo");
      }
      return key;
    } catch (java.security.GeneralSecurityException | IllegalArgumentException failure) {
      throw new IOException("Trusted public key is not a valid EC SubjectPublicKeyInfo", failure);
    }
  }

  private static byte[] decodePemOrDer(byte[] source) throws IOException {
    String text = new String(source, StandardCharsets.US_ASCII).trim();
    if (!text.startsWith("-----BEGIN PUBLIC KEY-----")) {
      return source.clone();
    }
    if (!text.endsWith("-----END PUBLIC KEY-----")) {
      throw new IOException("Trusted public-key PEM has an invalid boundary");
    }
    String body =
        text.substring(
                "-----BEGIN PUBLIC KEY-----".length(),
                text.length() - "-----END PUBLIC KEY-----".length())
            .replaceAll("\\s", "");
    try {
      return Base64.getDecoder().decode(body);
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Trusted public-key PEM is not valid Base64", invalid);
    }
  }
}
