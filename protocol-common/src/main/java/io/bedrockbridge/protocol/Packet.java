package io.bedrockbridge.protocol;

/** Generic mutable-decoding packet contract without edition-specific semantics. */
public interface Packet {
    /** Returns the nonnegative wire packet identifier. */
    int packetId();

    /** Returns the exact protocol version owning this packet layout. */
    ProtocolVersion protocolVersion();

    /** Returns the connection state in which this packet is legal. */
    ProtocolState state();

    /** Returns the packet direction relative to the server. */
    PacketDirection direction();

    /** Encodes packet fields into a bounded writer. */
    void encode(PacketWriter writer);

    /** Decodes packet fields from a bounded reader into this factory-created instance. */
    void decode(PacketReader reader);
}
