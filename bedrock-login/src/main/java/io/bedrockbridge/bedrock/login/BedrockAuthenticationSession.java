package io.bedrockbridge.bedrock.login;

import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.auth.AuthenticatedLogin;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.BedrockIdentity;
import io.bedrockbridge.bedrock.auth.BedrockLoginPayload;
import io.bedrockbridge.bedrock.crypto.BedrockKeyAgreement;
import io.bedrockbridge.bedrock.crypto.BedrockSessionCipher;
import io.bedrockbridge.bedrock.crypto.HandshakeJwtSigner;
import java.security.KeyPair;
import java.security.SecureRandom;

/**
 * Session-confined coordinator for chain verification, ECDH, JWT challenge, and cipher activation.
 */
public final class BedrockAuthenticationSession implements AutoCloseable {
  private final BedrockChainVerifier verifier;
  private final SecureRandom random;
  private final HandshakeJwtSigner handshakeSigner;
  private final BedrockAuthMode mode;
  private AuthenticationState state = AuthenticationState.AWAITING_LOGIN;
  private BedrockIdentity identity;
  private BedrockSessionCipher cipher;

  /** Creates an authentication session from explicit security dependencies. */
  public BedrockAuthenticationSession(
      BedrockChainVerifier verifier, SecureRandom random, HandshakeJwtSigner handshakeSigner) {
    this(verifier, random, handshakeSigner, BedrockAuthMode.ONLINE);
  }

  /** Creates a session with an explicit online/offline authentication policy. */
  public BedrockAuthenticationSession(
      BedrockChainVerifier verifier,
      SecureRandom random,
      HandshakeJwtSigner handshakeSigner,
      BedrockAuthMode mode) {
    this.verifier = java.util.Objects.requireNonNull(verifier, "verifier");
    this.random = java.util.Objects.requireNonNull(random, "random");
    this.handshakeSigner = java.util.Objects.requireNonNull(handshakeSigner, "handshakeSigner");
    this.mode = java.util.Objects.requireNonNull(mode, "mode");
  }

  /** Verifies login proof, derives a unique cipher, and creates the signed server challenge. */
  public synchronized AuthenticationChallenge authenticate(BedrockLoginPayload payload) {
    require(AuthenticationState.AWAITING_LOGIN);
    try {
      AuthenticatedLogin authenticated =
          switch (mode) {
            case ONLINE -> verifier.verify(payload);
            case OFFLINE_ALLOW_SELF_SIGNED -> verifier.verifyOfflineSelfSigned(payload);
            case OFFLINE_DENY ->
                throw new BedrockValidationException("Offline Bedrock authentication is disabled");
          };
      identity = authenticated.identity();
      KeyPair serverKey = BedrockKeyAgreement.generate(random);
      byte[] salt = new byte[16];
      random.nextBytes(salt);
      byte[] secret = BedrockKeyAgreement.derive(serverKey.getPrivate(), identity.identityKey());
      cipher = new BedrockSessionCipher(salt, secret);
      String handshakeJwt = handshakeSigner.sign(serverKey, salt);
      state = AuthenticationState.AWAITING_CLIENT_HANDSHAKE;
      return new AuthenticationChallenge(identity, handshakeJwt);
    } catch (RuntimeException failure) {
      state = AuthenticationState.FAILED;
      throw failure;
    }
  }

  /** Activates encrypted traffic after the client handshake acknowledgement. */
  public synchronized void confirmClientHandshake() {
    require(AuthenticationState.AWAITING_CLIENT_HANDSHAKE);
    state = AuthenticationState.AUTHENTICATED;
  }

  /** Encrypts a packet only after successful client confirmation. */
  public synchronized byte[] encrypt(byte[] payload) {
    require(AuthenticationState.AUTHENTICATED);
    return cipher.encrypt(payload);
  }

  /** Decrypts and authenticates a packet only after successful client confirmation. */
  public synchronized byte[] decrypt(byte[] payload) {
    require(AuthenticationState.AUTHENTICATED);
    return cipher.decrypt(payload);
  }

  /** Returns the verified identity after login proof is accepted. */
  public synchronized BedrockIdentity identity() {
    if (identity == null) {
      throw new BedrockValidationException("Identity is not authenticated");
    }
    return identity;
  }

  /** Returns the current authentication state. */
  public synchronized AuthenticationState state() {
    return state;
  }

  @Override
  public synchronized void close() {
    cipher = null;
    identity = null;
    state = AuthenticationState.CLOSED;
  }

  private void require(AuthenticationState expected) {
    if (state != expected) {
      throw new BedrockValidationException(
          "Expected authentication state " + expected + " but was " + state);
    }
  }
}
