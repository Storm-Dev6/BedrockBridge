package io.bedrockbridge.registry.generator;

import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.BedrockConnectionRequestDecoder;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.auth.JwtToken;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates and audits a transient, self-signed offline Login request. */
public final class OfflineLoginMaterial {
  private OfflineLoginMaterial() {}

  /** Generates a new P-384 identity and a minimal unauthenticated Login request. */
  public static Generated generate(int serverPort, Instant now) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    KeyPair identity = generator.generateKeyPair();
    String publicKey = Base64.getEncoder().encodeToString(identity.getPublic().getEncoded());
    String uuid = UUID.randomUUID().toString();
    long issuedAt = now.getEpochSecond();
    String chainPayload =
        "{\"certificateAuthority\":true,\"exp\":"
            + (issuedAt + 3_600)
            + ",\"extraData\":{\"displayName\":\"BedrockBridgeProbe\",\"identity\":\""
            + uuid
            + "\",\"XUID\":\"\"},\"iat\":"
            + issuedAt
            + ",\"identityPublicKey\":\""
            + publicKey
            + "\",\"nbf\":"
            + (issuedAt - 60)
            + "}";
    String chainToken = sign(identity, publicKey, chainPayload);
    String chainJson = "{\"chain\":[\"" + chainToken + "\"]}";
    String clientToken = sign(identity, publicKey, clientPayload(serverPort, uuid));
    byte[] chain = chainJson.getBytes(StandardCharsets.UTF_8);
    byte[] client = clientToken.getBytes(StandardCharsets.UTF_8);
    ByteBuffer request =
        ByteBuffer.allocate(Integer.BYTES * 2 + chain.length + client.length)
            .order(ByteOrder.LITTLE_ENDIAN);
    request.putInt(chain.length).put(chain).putInt(client.length).put(client);
    return new Generated(identity.getPublic(), chainToken, clientToken, request.array());
  }

  /** Audits encoding, claims, signature format, key linkage, and verifies the generated request. */
  public static void verify(Generated generated, Instant now) {
    if (generated == null) {
      throw new NullPointerException("generated");
    }
    byte[] request = generated.connectionRequest();
    ByteBuffer input = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN);
    int chainLength = input.getInt();
    if (chainLength <= 0 || chainLength > input.remaining()) {
      throw new BedrockValidationException("Generated chain length is invalid");
    }
    byte[] chainBytes = new byte[chainLength];
    input.get(chainBytes);
    int clientLength = input.getInt();
    if (clientLength <= 0 || clientLength != input.remaining()) {
      throw new BedrockValidationException("Generated client-data length is invalid");
    }
    String chainJson = new String(chainBytes, StandardCharsets.UTF_8);
    String clientToken =
        new String(input.array(), input.position(), clientLength, StandardCharsets.UTF_8);
    JwtToken chain = JwtToken.parse(generated.chainToken());
    JwtToken client = JwtToken.parse(clientToken);
    String encodedKey = Base64.getEncoder().encodeToString(generated.identityKey().getEncoded());
    if (!encodedKey.equals(chain.header().get("x5u"))
        || !encodedKey.equals(client.header().get("x5u"))
        || !encodedKey.equals(chain.claims().get("identityPublicKey"))) {
      throw new BedrockValidationException("Generated JWT public-key linkage is invalid");
    }
    if (!Boolean.TRUE.equals(chain.claims().get("certificateAuthority"))) {
      throw new BedrockValidationException("Generated chain must be certificate-authority marked");
    }
    requireTimeClaim(chain.claims(), "iat");
    requireTimeClaim(chain.claims(), "nbf");
    requireTimeClaim(chain.claims(), "exp");
    if (chain.signature().length != 96 || client.signature().length != 96) {
      throw new BedrockValidationException("Generated ES384 signature is not P1363 96-byte form");
    }
    new BedrockConnectionRequestDecoder(1_048_576).decode(ByteBuffer.wrap(request));
    new BedrockChainVerifier(
            List.of(generated.identityKey()),
            new InMemoryReplayGuard(2),
            Clock.fixed(now, java.time.ZoneOffset.UTC),
            Duration.ofSeconds(5))
        .verify(new BedrockConnectionRequestDecoder(1_048_576).decode(ByteBuffer.wrap(request)));
    if (!chainJson.contains(generated.chainToken())) {
      throw new BedrockValidationException("Generated chain JSON does not contain its token");
    }
  }

  private static void requireTimeClaim(Map<String, Object> claims, String name) {
    if (!(claims.get(name) instanceof Long)) {
      throw new BedrockValidationException("Generated JWT is missing integer " + name);
    }
  }

  private static String clientPayload(int serverPort, String uuid) {
    byte[] skin = new byte[64 * 64 * 4];
    for (int index = 3; index < skin.length; index += 4) {
      skin[index] = (byte) 0xFF;
    }
    String skinData = Base64.getEncoder().encodeToString(skin);
    String resourcePatch =
        Base64.getEncoder()
            .encodeToString(
                "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}"
                    .getBytes(StandardCharsets.UTF_8));
    String emptyObject = Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8));
    return "{\"AnimatedImageData\":[],\"ArmSize\":\"wide\",\"CapeData\":\"\","
        + "\"CapeId\":\"\",\"CapeImageHeight\":0,\"CapeImageWidth\":0,"
        + "\"CapeOnClassicSkin\":false,\"ClientRandomId\":748,"
        + "\"CompatibleWithClientSideChunkGen\":false,\"CurrentInputMode\":1,"
        + "\"DefaultInputMode\":1,\"DeviceId\":\""
        + uuid
        + "\",\"DeviceModel\":\"BedrockBridge\",\"DeviceOS\":8,"
        + "\"GameVersion\":\"1.21.40\",\"GuiScale\":0,\"IsEditorMode\":false,"
        + "\"LanguageCode\":\"en_US\",\"OverrideSkin\":false,"
        + "\"PersonaPieces\":[],\"PersonaSkin\":false,\"PieceTintColors\":[],"
        + "\"PlatformOfflineId\":\"\",\"PlatformOnlineId\":\"\","
        + "\"PlatformUserId\":\"\",\"PlayFabId\":\"\",\"PremiumSkin\":false,"
        + "\"SelfSignedId\":\""
        + uuid
        + "\",\"ServerAddress\":\"127.0.0.1:"
        + serverPort
        + "\",\"SkinAnimationData\":\"\",\"SkinColor\":\"#0\","
        + "\"SkinData\":\""
        + skinData
        + "\",\"SkinGeometryData\":\""
        + emptyObject
        + "\",\"SkinGeometryDataEngineVersion\":\"1.21.40\","
        + "\"SkinId\":\"BedrockBridgeSynthetic\",\"SkinImageHeight\":64,"
        + "\"SkinImageWidth\":64,\"SkinResourcePatch\":\""
        + resourcePatch
        + "\",\"ThirdPartyName\":\"BedrockBridgeProbe\","
        + "\"ThirdPartyNameOnly\":false,\"TrustedSkin\":true,\"UIProfile\":0}";
  }

  private static String sign(KeyPair keyPair, String publicKey, String payload) throws Exception {
    Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
    String header = "{\"alg\":\"ES384\",\"x5u\":\"" + publicKey + "\"}";
    String signingInput =
        url.encodeToString(header.getBytes(StandardCharsets.UTF_8))
            + "."
            + url.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA384withECDSAinP1363Format");
    signer.initSign(keyPair.getPrivate());
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
    return signingInput + "." + url.encodeToString(signer.sign());
  }

  /** Generated request and public-only audit material; private key is deliberately not retained. */
  public static final class Generated {
    private final java.security.PublicKey identityKey;
    private final String chainToken;
    private final String clientToken;
    private final byte[] connectionRequest;

    private Generated(
        java.security.PublicKey identityKey,
        String chainToken,
        String clientToken,
        byte[] connectionRequest) {
      if (identityKey == null
          || chainToken == null
          || clientToken == null
          || connectionRequest == null) {
        throw new NullPointerException("generated fields");
      }
      this.identityKey = identityKey;
      this.chainToken = chainToken;
      this.clientToken = clientToken;
      this.connectionRequest = connectionRequest.clone();
    }

    public java.security.PublicKey identityKey() {
      return identityKey;
    }

    public String chainToken() {
      return chainToken;
    }

    public String clientToken() {
      return clientToken;
    }

    public byte[] connectionRequest() {
      return connectionRequest.clone();
    }
  }
}
