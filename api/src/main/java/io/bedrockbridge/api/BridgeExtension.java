package io.bedrockbridge.api;

/** Stable lifecycle contract implemented by trusted bridge extensions. */
public interface BridgeExtension {
    /** Returns a unique, stable namespaced extension identifier. */
    String id();

    /** Activates the extension using only stable API services. */
    void start(ExtensionContext context);

    /** Releases extension-owned resources; implementations must be idempotent. */
    void stop();
}
