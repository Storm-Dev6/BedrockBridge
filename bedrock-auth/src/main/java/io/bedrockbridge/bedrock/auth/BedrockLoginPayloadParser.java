package io.bedrockbridge.bedrock.auth;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses the size-bounded Bedrock chain JSON and client-data compact JWT. */
public final class BedrockLoginPayloadParser {
    /** Parses a chain object of the form {@code {"chain":[...]}}. */
    public BedrockLoginPayload parse(String chainJson, String clientDataJwt) {
        Map<String, Object> object = StrictJson.parseObject(chainJson);
        Object chainValue = object.get("chain");
        if (!(chainValue instanceof List<?> values)) {
            throw new BedrockValidationException("Login chain array is missing");
        }
        List<String> chain = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String token)) {
                throw new BedrockValidationException("Login chain contains a non-string token");
            }
            chain.add(token);
        }
        return new BedrockLoginPayload(chain, clientDataJwt);
    }
}
