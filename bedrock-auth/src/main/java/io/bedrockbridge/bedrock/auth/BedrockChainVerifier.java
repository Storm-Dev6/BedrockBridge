package io.bedrockbridge.bedrock.auth;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verifies a pinned-root ES384 identity chain, client-data JWT, claims, and replay. */
public final class BedrockChainVerifier {
    private final Set<PublicKey> trustedRoots;
    private final ReplayGuard replayGuard;
    private final Clock clock;
    private final Duration clockSkew;

    /** Creates a verifier from explicit trusted P-384 roots and time policy. */
    public BedrockChainVerifier(
            Collection<PublicKey> trustedRoots,
            ReplayGuard replayGuard,
            Clock clock,
            Duration clockSkew) {
        this.trustedRoots = Set.copyOf(trustedRoots);
        if (this.trustedRoots.isEmpty()) throw new IllegalArgumentException("At least one trust root is required");
        this.replayGuard = java.util.Objects.requireNonNull(replayGuard, "replayGuard");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.clockSkew = java.util.Objects.requireNonNull(clockSkew, "clockSkew");
        if (clockSkew.isNegative()) throw new IllegalArgumentException("clockSkew must be nonnegative");
    }

    /** Authenticates the entire chain and returns only verified claims. */
    public AuthenticatedLogin verify(BedrockLoginPayload payload) {
        Instant now = Instant.now(clock);
        PublicKey signingKey = null;
        Map<String, Object> finalClaims = null;
        Instant earliestExpiry = Instant.MAX;
        Set<String> tokens = new HashSet<>();
        for (String compact : payload.chain()) {
            if (!tokens.add(compact)) throw new BedrockValidationException("Duplicate JWT in login chain");
            JwtToken token = JwtToken.parse(compact);
            if (signingKey == null) {
                signingKey = publicKey(requiredString(token.header(), "x5u"));
                if (!trustedRoots.contains(signingKey)) {
                    throw new BedrockValidationException("Login chain root is not trusted");
                }
            }
            token.verify(signingKey);
            Instant expiry = validateTime(token.claims(), now);
            if (expiry.isBefore(earliestExpiry)) earliestExpiry = expiry;
            signingKey = publicKey(requiredString(token.claims(), "identityPublicKey"));
            finalClaims = token.claims();
        }
        JwtToken clientData = JwtToken.parse(payload.clientDataJwt());
        clientData.verify(signingKey);
        String fingerprint = fingerprint(payload);
        if (!replayGuard.admit(fingerprint, earliestExpiry, now)) {
            throw new BedrockValidationException("Login proof was already used or replay cache is full");
        }
        Map<String, Object> extra = requiredObject(finalClaims, "extraData");
        BedrockIdentity identity = new BedrockIdentity(
                UUID.fromString(requiredString(extra, "identity")),
                requiredString(extra, "displayName"),
                optionalString(extra, "XUID"),
                optionalString(extra, "titleId"),
                signingKey);
        return new AuthenticatedLogin(identity, clientData.claims());
    }

    private Instant validateTime(Map<String, Object> claims, Instant now) {
        long expirySeconds = requiredLong(claims, "exp");
        long notBeforeSeconds = requiredLong(claims, "nbf");
        Instant expiry = Instant.ofEpochSecond(expirySeconds);
        Instant notBefore = Instant.ofEpochSecond(notBeforeSeconds);
        if (!expiry.plus(clockSkew).isAfter(now) || notBefore.minus(clockSkew).isAfter(now)) {
            throw new BedrockValidationException("JWT is expired or not yet valid");
        }
        return expiry;
    }

    private static PublicKey publicKey(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException failure) {
            throw new BedrockValidationException("Invalid P-384 identity public key");
        }
    }

    private static String fingerprint(BedrockLoginPayload payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            payload.chain().forEach(token -> digest.update(token.getBytes(StandardCharsets.US_ASCII)));
            digest.update(payload.clientDataJwt().getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new BedrockValidationException("Missing string claim: " + key);
        return text;
    }

    private static String optionalString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof String text ? text : "";
    }

    private static long requiredLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Long number)) throw new BedrockValidationException("Missing integer claim: " + key);
        return number;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredObject(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Map<?, ?>)) throw new BedrockValidationException("Missing object claim: " + key);
        return (Map<String, Object>) value;
    }
}
