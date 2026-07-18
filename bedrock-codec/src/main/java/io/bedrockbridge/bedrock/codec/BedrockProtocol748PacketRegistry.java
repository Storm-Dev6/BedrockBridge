package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.play.BedrockExperiment;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkCompressionAlgorithm;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.PlayStatus;
import io.bedrockbridge.bedrock.packet.play.PlayStatusPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackInfo;
import io.bedrockbridge.bedrock.packet.play.ResourcePackResponse;
import io.bedrockbridge.bedrock.packet.play.ResourcePackStackEntry;
import io.bedrockbridge.bedrock.packet.play.ResourcePackStackPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePacksInfoPacket;
import io.bedrockbridge.bedrock.packet.play.ServerToClientHandshakePacket;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Creates the independently implemented packet catalog for Bedrock network protocol 748. */
public final class BedrockProtocol748PacketRegistry {
  private BedrockProtocol748PacketRegistry() {}

  /** Builds an immutable, collision-checked catalog under the supplied decode limits. */
  public static BedrockPlayPacketRegistry create(BedrockProtocolLimits limits) {
    BedrockProtocolLimits checkedLimits = Objects.requireNonNull(limits, "limits");
    var builder = BedrockPlayPacketRegistry.builder(checkedLimits);
    builder.register(
        registration(
            BedrockPacketIds.REQUEST_NETWORK_SETTINGS,
            PacketDirection.SERVERBOUND,
            Set.of(BedrockPlayState.NETWORK_SETTINGS),
            RequestNetworkSettingsPacket.class,
            requestNetworkSettingsCodec()));
    builder.register(
        registration(
            BedrockPacketIds.NETWORK_SETTINGS,
            PacketDirection.CLIENTBOUND,
            Set.of(BedrockPlayState.NETWORK_SETTINGS),
            NetworkSettingsPacket.class,
            networkSettingsCodec()));
    builder.register(
        registration(
            BedrockPacketIds.LOGIN,
            PacketDirection.SERVERBOUND,
            Set.of(BedrockPlayState.LOGIN),
            LoginPacket.class,
            loginCodec(checkedLimits)));
    builder.register(
        registration(
            BedrockPacketIds.PLAY_STATUS,
            PacketDirection.CLIENTBOUND,
            EnumSet.of(
                BedrockPlayState.LOGIN,
                BedrockPlayState.AUTHENTICATING,
                BedrockPlayState.STARTING_PLAY,
                BedrockPlayState.PLAY_READY),
            PlayStatusPacket.class,
            playStatusCodec()));
    builder.register(
        registration(
            BedrockPacketIds.SERVER_TO_CLIENT_HANDSHAKE,
            PacketDirection.CLIENTBOUND,
            Set.of(BedrockPlayState.AUTHENTICATING),
            ServerToClientHandshakePacket.class,
            serverToClientHandshakeCodec(checkedLimits)));
    builder.register(
        registration(
            BedrockPacketIds.CLIENT_TO_SERVER_HANDSHAKE,
            PacketDirection.SERVERBOUND,
            Set.of(BedrockPlayState.AUTHENTICATING),
            ClientToServerHandshakePacket.class,
            clientToServerHandshakeCodec()));
    builder.register(
        registration(
            BedrockPacketIds.DISCONNECT,
            PacketDirection.CLIENTBOUND,
            connectedStates(),
            DisconnectPacket.class,
            disconnectCodec(checkedLimits)));
    builder.register(
        registration(
            BedrockPacketIds.RESOURCE_PACKS_INFO,
            PacketDirection.CLIENTBOUND,
            Set.of(BedrockPlayState.RESOURCE_PACKS),
            ResourcePacksInfoPacket.class,
            resourcePacksInfoCodec(checkedLimits)));
    builder.register(
        registration(
            BedrockPacketIds.RESOURCE_PACK_STACK,
            PacketDirection.CLIENTBOUND,
            Set.of(BedrockPlayState.RESOURCE_PACKS),
            ResourcePackStackPacket.class,
            resourcePackStackCodec(checkedLimits)));
    builder.register(
        registration(
            BedrockPacketIds.RESOURCE_PACK_CLIENT_RESPONSE,
            PacketDirection.SERVERBOUND,
            Set.of(BedrockPlayState.RESOURCE_PACKS),
            ResourcePackClientResponsePacket.class,
            resourcePackClientResponseCodec(checkedLimits)));
    return builder.build();
  }

  private static <T extends io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket>
      BedrockPlayPacketRegistration<T> registration(
          int packetId,
          PacketDirection direction,
          Set<BedrockPlayState> states,
          Class<T> type,
          BedrockPlayPacketCodec<T> codec) {
    return new BedrockPlayPacketRegistration<>(
        BedrockProtocol.PLAY_VERSION_748, packetId, direction, states, type, codec);
  }

  private static Set<BedrockPlayState> connectedStates() {
    EnumSet<BedrockPlayState> states = EnumSet.allOf(BedrockPlayState.class);
    states.remove(BedrockPlayState.DISCONNECTED);
    return states;
  }

  private static BedrockPlayPacketCodec<RequestNetworkSettingsPacket>
      requestNetworkSettingsCodec() {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(RequestNetworkSettingsPacket packet, BedrockBinaryWriter writer) {
        writer.writeIntBE(packet.clientNetworkVersion());
      }

      @Override
      public RequestNetworkSettingsPacket decode(BedrockBinaryReader reader) {
        return new RequestNetworkSettingsPacket(reader.readIntBE());
      }
    };
  }

  private static BedrockPlayPacketCodec<NetworkSettingsPacket> networkSettingsCodec() {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(NetworkSettingsPacket packet, BedrockBinaryWriter writer) {
        writer.writeUnsignedShortLE(packet.compressionThreshold());
        writer.writeUnsignedShortLE(packet.compressionAlgorithm().wireValue());
        writer.writeBoolean(packet.clientThrottleEnabled());
        writer.writeByte(packet.clientThrottleThreshold());
        writer.writeFloatLE(packet.clientThrottleScalar());
      }

      @Override
      public NetworkSettingsPacket decode(BedrockBinaryReader reader) {
        int threshold = reader.readUnsignedShortLE();
        NetworkCompressionAlgorithm algorithm;
        try {
          algorithm = NetworkCompressionAlgorithm.fromWireValue(reader.readUnsignedShortLE());
        } catch (IllegalArgumentException invalid) {
          throw new BedrockValidationException("Unknown network compression algorithm");
        }
        return new NetworkSettingsPacket(
            threshold,
            algorithm,
            reader.readBoolean(),
            reader.readUnsignedByte(),
            reader.readFloatLE());
      }
    };
  }

  private static BedrockPlayPacketCodec<LoginPacket> loginCodec(BedrockProtocolLimits limits) {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(LoginPacket packet, BedrockBinaryWriter writer) {
        writer.writeIntBE(packet.clientNetworkVersion());
        writer.writeUnsignedVarInt(packet.connectionRequestLength());
        writer.writeBytes(packet.connectionRequest());
      }

      @Override
      public LoginPacket decode(BedrockBinaryReader reader) {
        int networkVersion = reader.readIntBE();
        int length = reader.readUnsignedVarInt(limits.maximumLoginBytes());
        return new LoginPacket(networkVersion, reader.readBytes(length));
      }
    };
  }

  private static BedrockPlayPacketCodec<PlayStatusPacket> playStatusCodec() {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(PlayStatusPacket packet, BedrockBinaryWriter writer) {
        writer.writeIntBE(packet.status().wireValue());
      }

      @Override
      public PlayStatusPacket decode(BedrockBinaryReader reader) {
        try {
          return new PlayStatusPacket(PlayStatus.fromWireValue(reader.readIntBE()));
        } catch (IllegalArgumentException invalid) {
          throw new BedrockValidationException("Unknown play status");
        }
      }
    };
  }

  private static BedrockPlayPacketCodec<ServerToClientHandshakePacket> serverToClientHandshakeCodec(
      BedrockProtocolLimits limits) {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(ServerToClientHandshakePacket packet, BedrockBinaryWriter writer) {
        writer.writeString(packet.handshakeJwt(), limits.maximumLoginBytes());
      }

      @Override
      public ServerToClientHandshakePacket decode(BedrockBinaryReader reader) {
        return new ServerToClientHandshakePacket(reader.readString(limits.maximumLoginBytes()));
      }
    };
  }

  private static BedrockPlayPacketCodec<ClientToServerHandshakePacket>
      clientToServerHandshakeCodec() {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(ClientToServerHandshakePacket packet, BedrockBinaryWriter writer) {}

      @Override
      public ClientToServerHandshakePacket decode(BedrockBinaryReader reader) {
        return new ClientToServerHandshakePacket();
      }
    };
  }

  private static BedrockPlayPacketCodec<DisconnectPacket> disconnectCodec(
      BedrockProtocolLimits limits) {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(DisconnectPacket packet, BedrockBinaryWriter writer) {
        writer.writeVarInt(packet.reason());
        writer.writeBoolean(packet.skipMessage());
        if (!packet.skipMessage()) {
          writer.writeString(packet.message(), limits.maximumStringBytes());
          writer.writeString(packet.filteredMessage(), limits.maximumStringBytes());
        }
      }

      @Override
      public DisconnectPacket decode(BedrockBinaryReader reader) {
        int reason = reader.readVarInt();
        boolean skipMessage = reader.readBoolean();
        if (skipMessage) {
          return DisconnectPacket.silent(reason);
        }
        return new DisconnectPacket(
            reason,
            false,
            reader.readString(limits.maximumStringBytes()),
            reader.readString(limits.maximumStringBytes()));
      }
    };
  }

  private static BedrockPlayPacketCodec<ResourcePacksInfoPacket> resourcePacksInfoCodec(
      BedrockProtocolLimits limits) {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(ResourcePacksInfoPacket packet, BedrockBinaryWriter writer) {
        writer.writeBoolean(packet.resourcePackRequired());
        writer.writeBoolean(packet.hasAddonPacks());
        writer.writeBoolean(packet.hasScripts());
        writeUnsignedShortCount(writer, packet.resourcePacks().size(), limits);
        for (ResourcePackInfo pack : packet.resourcePacks()) {
          writePackInfo(writer, pack, limits);
        }
      }

      @Override
      public ResourcePacksInfoPacket decode(BedrockBinaryReader reader) {
        boolean required = reader.readBoolean();
        boolean addon = reader.readBoolean();
        boolean scripts = reader.readBoolean();
        int count = readUnsignedShortCount(reader, limits);
        List<ResourcePackInfo> packs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          packs.add(readPackInfo(reader, limits));
        }
        return new ResourcePacksInfoPacket(required, addon, scripts, packs);
      }
    };
  }

  private static BedrockPlayPacketCodec<ResourcePackStackPacket> resourcePackStackCodec(
      BedrockProtocolLimits limits) {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(ResourcePackStackPacket packet, BedrockBinaryWriter writer) {
        writer.writeBoolean(packet.texturePackRequired());
        writeStackEntries(writer, packet.addonPacks(), limits);
        writeStackEntries(writer, packet.texturePacks(), limits);
        writer.writeString(packet.baseGameVersion(), limits.maximumStringBytes());
        writer.writeUnsignedIntLE(packet.experiments().size());
        for (BedrockExperiment experiment : packet.experiments()) {
          writer.writeString(experiment.toggleName(), limits.maximumStringBytes());
          writer.writeBoolean(experiment.enabled());
          writer.writeString(experiment.alwaysOnName(), limits.maximumStringBytes());
          writer.writeBoolean(experiment.alwaysOnEnabled());
        }
        writer.writeBoolean(packet.experimentsEverToggled());
        writer.writeBoolean(packet.includeEditorPacks());
      }

      @Override
      public ResourcePackStackPacket decode(BedrockBinaryReader reader) {
        boolean required = reader.readBoolean();
        List<ResourcePackStackEntry> addons = readStackEntries(reader, limits);
        List<ResourcePackStackEntry> textures = readStackEntries(reader, limits);
        String baseGameVersion = reader.readString(limits.maximumStringBytes());
        int count = checkedCount(reader.readUnsignedIntLE(), limits.maximumRegistryEntries());
        List<BedrockExperiment> experiments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          experiments.add(
              new BedrockExperiment(
                  reader.readString(limits.maximumStringBytes()),
                  reader.readBoolean(),
                  reader.readString(limits.maximumStringBytes()),
                  reader.readBoolean()));
        }
        return new ResourcePackStackPacket(
            required,
            addons,
            textures,
            baseGameVersion,
            experiments,
            reader.readBoolean(),
            reader.readBoolean());
      }
    };
  }

  private static BedrockPlayPacketCodec<ResourcePackClientResponsePacket>
      resourcePackClientResponseCodec(BedrockProtocolLimits limits) {
    return new BedrockPlayPacketCodec<>() {
      @Override
      public void encode(ResourcePackClientResponsePacket packet, BedrockBinaryWriter writer) {
        writer.writeByte(packet.response().wireValue());
        writeUnsignedShortCount(writer, packet.downloadingPacks().size(), limits);
        for (String pack : packet.downloadingPacks()) {
          writer.writeString(pack, limits.maximumResourcePackIdBytes());
        }
      }

      @Override
      public ResourcePackClientResponsePacket decode(BedrockBinaryReader reader) {
        ResourcePackResponse response;
        try {
          response = ResourcePackResponse.fromWireValue(reader.readUnsignedByte());
        } catch (IllegalArgumentException invalid) {
          throw new BedrockValidationException("Unknown resource pack response");
        }
        int count = readUnsignedShortCount(reader, limits);
        List<String> packs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          packs.add(reader.readString(limits.maximumResourcePackIdBytes()));
        }
        return new ResourcePackClientResponsePacket(response, packs);
      }
    };
  }

  private static void writePackInfo(
      BedrockBinaryWriter writer, ResourcePackInfo pack, BedrockProtocolLimits limits) {
    writer.writeString(pack.id(), limits.maximumResourcePackIdBytes());
    writer.writeString(pack.version(), limits.maximumStringBytes());
    writer.writeLongLE(pack.size());
    writer.writeString(pack.contentKey(), limits.maximumStringBytes());
    writer.writeString(pack.subPackName(), limits.maximumStringBytes());
    writer.writeString(pack.contentIdentity(), limits.maximumStringBytes());
    writer.writeBoolean(pack.hasScripts());
    writer.writeBoolean(pack.addonPack());
    writer.writeBoolean(pack.rayTracingCapable());
    writer.writeString(pack.cdnUrl(), limits.maximumStringBytes());
  }

  private static ResourcePackInfo readPackInfo(
      BedrockBinaryReader reader, BedrockProtocolLimits limits) {
    return new ResourcePackInfo(
        reader.readString(limits.maximumResourcePackIdBytes()),
        reader.readString(limits.maximumStringBytes()),
        reader.readLongLE(),
        reader.readString(limits.maximumStringBytes()),
        reader.readString(limits.maximumStringBytes()),
        reader.readString(limits.maximumStringBytes()),
        reader.readBoolean(),
        reader.readBoolean(),
        reader.readBoolean(),
        reader.readString(limits.maximumStringBytes()));
  }

  private static void writeStackEntries(
      BedrockBinaryWriter writer,
      List<ResourcePackStackEntry> entries,
      BedrockProtocolLimits limits) {
    if (entries.size() > limits.maximumResourcePacks()) {
      throw new BedrockValidationException("Resource pack count exceeds configured limit");
    }
    writer.writeUnsignedVarInt(entries.size());
    for (ResourcePackStackEntry entry : entries) {
      writer.writeString(entry.id(), limits.maximumResourcePackIdBytes());
      writer.writeString(entry.version(), limits.maximumStringBytes());
      writer.writeString(entry.subPackName(), limits.maximumStringBytes());
    }
  }

  private static List<ResourcePackStackEntry> readStackEntries(
      BedrockBinaryReader reader, BedrockProtocolLimits limits) {
    int count = reader.readUnsignedVarInt(limits.maximumResourcePacks());
    List<ResourcePackStackEntry> entries = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      entries.add(
          new ResourcePackStackEntry(
              reader.readString(limits.maximumResourcePackIdBytes()),
              reader.readString(limits.maximumStringBytes()),
              reader.readString(limits.maximumStringBytes())));
    }
    return List.copyOf(entries);
  }

  private static void writeUnsignedShortCount(
      BedrockBinaryWriter writer, int count, BedrockProtocolLimits limits) {
    if (count > limits.maximumResourcePacks() || count > 0xFFFF) {
      throw new BedrockValidationException("Resource pack count exceeds configured limit");
    }
    writer.writeUnsignedShortLE(count);
  }

  private static int readUnsignedShortCount(
      BedrockBinaryReader reader, BedrockProtocolLimits limits) {
    int count = reader.readUnsignedShortLE();
    if (count > limits.maximumResourcePacks()) {
      throw new BedrockValidationException("Resource pack count exceeds configured limit");
    }
    return count;
  }

  private static int checkedCount(long count, int maximum) {
    if (count > maximum) {
      throw new BedrockValidationException("Collection count exceeds configured limit");
    }
    return (int) count;
  }
}
