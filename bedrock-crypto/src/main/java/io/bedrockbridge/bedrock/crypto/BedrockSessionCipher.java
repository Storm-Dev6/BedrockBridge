package io.bedrockbridge.bedrock.crypto;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Stateful AES-256/CFB8 cipher with Bedrock counter-bound eight-byte integrity checks. */
public final class BedrockSessionCipher {
  private final byte[] key;
  private final Cipher encryptor;
  private final Cipher decryptor;
  private long sendCounter;
  private long receiveCounter;

  /** Derives and initializes independent stream directions from salt and ECDH secret. */
  public BedrockSessionCipher(byte[] salt, byte[] sharedSecret) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(salt.clone());
      key = digest.digest(sharedSecret.clone());
      SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
      IvParameterSpec iv = new IvParameterSpec(Arrays.copyOf(key, 16));
      encryptor = Cipher.getInstance("AES/CFB8/NoPadding");
      decryptor = Cipher.getInstance("AES/CFB8/NoPadding");
      encryptor.init(Cipher.ENCRYPT_MODE, secretKey, iv);
      decryptor.init(Cipher.DECRYPT_MODE, secretKey, iv);
    } catch (java.security.GeneralSecurityException failure) {
      throw new IllegalStateException("Required Bedrock cipher is unavailable", failure);
    }
  }

  /** Appends integrity bytes and encrypts one payload in stream order. */
  public synchronized byte[] encrypt(byte[] payload) {
    ensureCounter(sendCounter);
    byte[] checksum = checksum(sendCounter, payload);
    byte[] clear = Arrays.copyOf(payload, payload.length + checksum.length);
    System.arraycopy(checksum, 0, clear, payload.length, checksum.length);
    sendCounter++;
    return encryptor.update(clear);
  }

  /** Decrypts and verifies one payload in stream order. */
  public synchronized byte[] decrypt(byte[] encrypted) {
    ensureCounter(receiveCounter);
    byte[] clear = decryptor.update(encrypted);
    if (clear == null || clear.length < 8) {
      throw new BedrockValidationException("Encrypted packet is truncated");
    }
    byte[] payload = Arrays.copyOf(clear, clear.length - 8);
    byte[] actual = Arrays.copyOfRange(clear, clear.length - 8, clear.length);
    byte[] expected = checksum(receiveCounter, payload);
    if (!MessageDigest.isEqual(actual, expected)) {
      throw new BedrockValidationException("Encrypted packet integrity check failed");
    }
    receiveCounter++;
    return payload;
  }

  private byte[] checksum(long counter, byte[] payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(
          ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array());
      digest.update(payload);
      digest.update(key);
      return Arrays.copyOf(digest.digest(), 8);
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void ensureCounter(long counter) {
    if (counter == Long.MAX_VALUE) {
      throw new BedrockValidationException("Encryption counter exhausted");
    }
  }
}
