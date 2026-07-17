package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.packet.ConnectedPing;
import io.bedrockbridge.bedrock.packet.ConnectedPong;
import io.bedrockbridge.bedrock.packet.ConnectionRequest;
import io.bedrockbridge.bedrock.packet.ConnectionRequestAccepted;
import io.bedrockbridge.bedrock.packet.DisconnectNotification;
import io.bedrockbridge.bedrock.packet.NewIncomingConnection;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply1;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply2;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest2;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.codec.DefaultPacketCodec;
import io.bedrockbridge.protocol.registry.DynamicPacketRegistry;
import io.bedrockbridge.protocol.registry.PacketRegistration;
import io.bedrockbridge.protocol.registry.PacketRegistry;
import java.util.function.Supplier;

/** Creates the complete immutable-by-convention Phase 3 handshake packet catalog. */
public final class BedrockPacketRegistry {
  private BedrockPacketRegistry() {}

  /** Registers every supported handshake packet ID, codec, and factory. */
  public static PacketRegistry create() {
    DynamicPacketRegistry registry = new DynamicPacketRegistry();
    register(registry, ConnectedPing.class, ConnectedPing::new);
    register(registry, ConnectedPong.class, ConnectedPong::new);
    register(registry, OpenConnectionRequest1.class, OpenConnectionRequest1::new);
    register(registry, OpenConnectionReply1.class, OpenConnectionReply1::new);
    register(registry, OpenConnectionRequest2.class, OpenConnectionRequest2::new);
    register(registry, OpenConnectionReply2.class, OpenConnectionReply2::new);
    register(registry, ConnectionRequest.class, ConnectionRequest::new);
    register(registry, ConnectionRequestAccepted.class, ConnectionRequestAccepted::new);
    register(registry, NewIncomingConnection.class, NewIncomingConnection::new);
    register(registry, DisconnectNotification.class, DisconnectNotification::new);
    return registry;
  }

  private static <T extends Packet> void register(
      DynamicPacketRegistry registry, Class<T> type, Supplier<T> supplier) {
    T sample = supplier.get();
    registry.register(
        new PacketRegistration<>(
            new PacketKey(
                sample.protocolVersion(), sample.state(), sample.direction(), sample.packetId()),
            type,
            new DefaultPacketCodec<>(),
            supplier::get));
  }
}
