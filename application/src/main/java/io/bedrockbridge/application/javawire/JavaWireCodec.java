package io.bedrockbridge.application.javawire;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Clean-room Java packet framing and the packet subset used by the bridge. */
public final class JavaWireCodec {
  public static final int PROTOCOL_1_21_1 = 767;
  private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
  private static final int MAX_STRING_BYTES = 1_048_576;

  private JavaWireCodec() {}

  public static void writePacket(
      OutputStream output, int packetId, byte[] fields, int compressionThreshold)
      throws IOException {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    writeVarInt(body, packetId);
    body.write(fields);
    byte[] raw = body.toByteArray();
    ByteArrayOutputStream framed = new ByteArrayOutputStream();
    if (compressionThreshold >= 0) {
      if (raw.length >= compressionThreshold) {
        byte[] compressed = compress(raw);
        writeVarInt(framed, raw.length);
        framed.write(compressed);
      } else {
        writeVarInt(framed, 0);
        framed.write(raw);
      }
    } else {
      framed.write(raw);
    }
    byte[] frame = framed.toByteArray();
    writeVarInt(output, frame.length);
    output.write(frame);
    output.flush();
  }

  public static Frame readFrame(InputStream input, int compressionThreshold)
      throws IOException, JavaWireException {
    int length = readVarInt(input);
    if (length < 1 || length > MAX_PACKET_BYTES) {
      throw new JavaWireException("invalid Java packet length: " + length);
    }
    byte[] frame = input.readNBytes(length);
    if (frame.length != length) {
      throw new EOFException("truncated Java packet frame");
    }
    byte[] body = frame;
    if (compressionThreshold >= 0) {
      ByteArrayInputStream compressedFrame = new ByteArrayInputStream(frame);
      int uncompressedLength = readVarInt(compressedFrame);
      byte[] compressed = compressedFrame.readAllBytes();
      if (uncompressedLength != 0) {
        if (uncompressedLength > MAX_PACKET_BYTES) {
          throw new JavaWireException("invalid uncompressed Java packet length");
        }
        body = decompress(compressed, uncompressedLength);
      } else {
        body = compressed;
      }
    }
    ByteArrayInputStream bodyInput = new ByteArrayInputStream(body);
    int packetId = readVarInt(bodyInput);
    return new Frame(packetId, bodyInput.readAllBytes());
  }

  public static byte[] encode(JavaWirePacket packet, JavaWireState state) throws JavaWireException {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (packet instanceof JavaWirePacket.Handshake p) {
        writeVarInt(out, p.protocolVersion());
        writeString(out, p.host(), 255);
        writeUnsignedShort(out, p.port());
        writeVarInt(out, p.nextState());
      } else if (packet instanceof JavaWirePacket.StatusRequest) {
        // no fields
      } else if (packet instanceof JavaWirePacket.Ping p) {
        writeLong(out, p.payload());
      } else if (packet instanceof JavaWirePacket.LoginStart p) {
        writeString(out, p.username(), 16);
        writeUuid(out, p.uuid());
      } else if (packet instanceof JavaWirePacket.LoginAcknowledged) {
        // no fields
      } else if (packet instanceof JavaWirePacket.AcknowledgeFinishConfiguration) {
        // no fields
      } else if (packet instanceof JavaWirePacket.Pong p) {
        writeLong(out, p.payload());
      } else if (packet instanceof JavaWirePacket.KeepAlive p) {
        writeLong(out, p.payload());
      } else if (packet instanceof JavaWirePacket.PlayKeepAlive p) {
        writeLong(out, p.payload());
      } else if (packet instanceof JavaWirePacket.ConfirmTeleportation p) {
        writeVarInt(out, p.teleportId());
      } else if (packet instanceof JavaWirePacket.ChunkBatchReceived p) {
        writeFloat(out, p.chunksPerTick());
      } else if (packet instanceof JavaWirePacket.PlayPlayerAbilities p) {
        out.write(p.flags());
      } else if (packet instanceof JavaWirePacket.SetPlayerPosition p) {
        writeDouble(out, p.x());
        writeDouble(out, p.feetY());
        writeDouble(out, p.z());
        out.write(p.onGround() ? 1 : 0);
      } else if (packet instanceof JavaWirePacket.SetPlayerPositionRotation p) {
        writeDouble(out, p.x());
        writeDouble(out, p.feetY());
        writeDouble(out, p.z());
        writeFloat(out, p.yaw());
        writeFloat(out, p.pitch());
        out.write(p.onGround() ? 1 : 0);
      } else if (packet instanceof JavaWirePacket.SetPlayerRotation p) {
        writeFloat(out, p.yaw());
        writeFloat(out, p.pitch());
        out.write(p.onGround() ? 1 : 0);
      } else if (packet instanceof JavaWirePacket.SetPlayerOnGround p) {
        out.write(p.onGround() ? 1 : 0);
      } else if (packet instanceof JavaWirePacket.KnownPacks p) {
        writeVarInt(out, p.packs().size());
        for (JavaWirePacket.KnownPack pack : p.packs()) {
          writeString(out, pack.namespace(), 32767);
          writeString(out, pack.id(), 32767);
          writeString(out, pack.version(), 32767);
        }
      } else if (packet instanceof JavaWirePacket.ClientInformation p) {
        writeString(out, p.locale(), 16);
        out.write(p.viewDistance());
        writeVarInt(out, p.chatMode());
        out.write(p.chatColors() ? 1 : 0);
        out.write(p.displayedSkinParts());
        writeVarInt(out, p.mainHand());
        out.write(p.textFiltering() ? 1 : 0);
        out.write(p.serverListings() ? 1 : 0);
      } else {
        throw new JavaWireException("packet is not valid serverbound in " + state + ": " + packet);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new JavaWireException("failed to encode Java packet", e);
    }
  }

  public static JavaWirePacket decode(JavaWireState state, int packetId, byte[] fields)
      throws JavaWireException {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(fields));
      return switch (state) {
        case LOGIN -> decodeLogin(packetId, in);
        case STATUS -> decodeStatus(packetId, in);
        case CONFIGURATION -> decodeConfiguration(packetId, in);
        case HANDSHAKING, CLOSED ->
            throw new JavaWireException(
                "unsupported inbound packet state=" + state + " id=" + packetId);
        case PLAY -> decodePlay(packetId, in);
      };
    } catch (IOException e) {
      throw new JavaWireException("malformed Java packet state=" + state + " id=" + packetId, e);
    }
  }

  private static JavaWirePacket decodeLogin(int id, DataInputStream in)
      throws IOException, JavaWireException {
    return switch (id) {
      case 0x00 -> new JavaWirePacket.Disconnect(readString(in, 262144));
      case 0x02 -> {
        UUID uuid = readUuid(in);
        String username = readString(in, 16);
        int properties = readVarInt(in);
        if (properties < 0 || properties > 1024) {
          throw new JavaWireException("invalid property count");
        }
        for (int i = 0; i < properties; i++) {
          readString(in, 32767);
          readString(in, 32767);
          if (in.readBoolean()) {
            readString(in, 32767);
          }
        }
        yield new JavaWirePacket.LoginSuccess(uuid, username, in.readBoolean());
      }
      case 0x03 -> new JavaWirePacket.SetCompression(readVarInt(in));
      default -> throw new JavaWireException("unsupported login packet id=" + id);
    };
  }

  private static JavaWirePacket decodeStatus(int id, DataInputStream in)
      throws IOException, JavaWireException {
    return switch (id) {
      case 0x00 -> new JavaWirePacket.StatusResponse(readString(in, 262144));
      case 0x01 -> new JavaWirePacket.Pong(in.readLong());
      default -> throw new JavaWireException("unsupported status packet id=" + id);
    };
  }

  private static JavaWirePacket decodeConfiguration(int id, DataInputStream in)
      throws IOException, JavaWireException {
    return switch (id) {
      case 0x03 -> new JavaWirePacket.FinishConfiguration();
      case 0x04 -> new JavaWirePacket.KeepAlive(in.readLong());
      case 0x0E -> {
        int count = readVarInt(in);
        if (count < 0 || count > 1024) {
          throw new JavaWireException("invalid known pack count");
        }
        List<JavaWirePacket.KnownPack> packs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
          packs.add(
              new JavaWirePacket.KnownPack(
                  readString(in, 32767), readString(in, 32767), readString(in, 32767)));
        }
        yield new JavaWirePacket.KnownPacks(packs);
      }
      case 0x07 -> {
        String registryId = readString(in, 32767);
        int count = boundedCount(readVarInt(in), "registry entry");
        List<JavaWirePacket.RegistryEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
          String entryId = readString(in, 32767);
          JavaNbt data = in.readBoolean() ? JavaNbtCodec.read(in) : null;
          entries.add(new JavaWirePacket.RegistryEntry(entryId, data));
        }
        yield new JavaWirePacket.RegistryData(registryId, entries);
      }
      case 0x01 -> {
        String channel = readString(in, 32767);
        int payloadBytes = in.available();
        if (payloadBytes > 32_767) {
          throw new JavaWireException("configuration plugin payload exceeds 32767 bytes");
        }
        in.skipBytes(payloadBytes);
        yield new JavaWirePacket.ConfigurationPluginMessage(channel, payloadBytes);
      }
      case 0x0C -> {
        int count = boundedCount(readVarInt(in), "feature flag");
        List<String> flags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
          flags.add(readString(in, 32767));
        }
        yield new JavaWirePacket.FeatureFlags(flags);
      }
      case 0x0D -> {
        int registryCount = boundedCount(readVarInt(in), "tag registry");
        List<JavaWirePacket.RegistryTags> registries = new ArrayList<>();
        for (int i = 0; i < registryCount; i++) {
          String registryId = readString(in, 32767);
          int tagCount = boundedCount(readVarInt(in), "tag");
          List<JavaWirePacket.Tag> tags = new ArrayList<>();
          for (int tagIndex = 0; tagIndex < tagCount; tagIndex++) {
            String name = readString(in, 32767);
            int entryCount = boundedCount(readVarInt(in), "tag entry");
            List<Integer> entries = new ArrayList<>();
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
              entries.add(readVarInt(in));
            }
            tags.add(new JavaWirePacket.Tag(name, entries));
          }
          registries.add(new JavaWirePacket.RegistryTags(registryId, tags));
        }
        yield new JavaWirePacket.UpdateTags(registries);
      }
      case 0x02 -> new JavaWirePacket.Disconnect(readString(in, 262144));
      default ->
          throw new JavaWireException(
              "unsupported configuration packet id=0x" + Integer.toHexString(id));
    };
  }

  private static JavaWirePacket decodePlay(int id, DataInputStream in)
      throws IOException, JavaWireException {
    JavaWirePacket packet =
        switch (id) {
          case 0x2B -> decodePlayLogin(in);
          case 0x0B -> {
            int difficulty = in.readUnsignedByte();
            if (difficulty > 3) {
              throw new JavaWireException("invalid difficulty=" + difficulty);
            }
            yield new JavaWirePacket.ChangeDifficulty(
                difficulty, readBoolean(in, "difficulty lock"));
          }
          case 0x0C -> new JavaWirePacket.ChunkBatchFinished(readVarInt(in));
          case 0x0D -> new JavaWirePacket.ChunkBatchStart();
          case 0x1D -> new JavaWirePacket.PlayDisconnect(JavaNbtCodec.read(in));
          case 0x1F -> new JavaWirePacket.EntityEvent(in.readInt(), in.readByte());
          case 0x22 -> new JavaWirePacket.GameEvent(in.readUnsignedByte(), in.readFloat());
          case 0x26 -> new JavaWirePacket.PlayKeepAlive(in.readLong());
          case 0x38 ->
              new JavaWirePacket.PlayPlayerAbilities(in.readByte(), in.readFloat(), in.readFloat());
          case 0x40 ->
              new JavaWirePacket.SynchronizePlayerPosition(
                  in.readDouble(),
                  in.readDouble(),
                  in.readDouble(),
                  in.readFloat(),
                  in.readFloat(),
                  in.readUnsignedByte(),
                  readVarInt(in));
          case 0x6C ->
              new JavaWirePacket.SystemChat(
                  JavaNbtCodec.read(in), readBoolean(in, "system chat overlay"));
          case 0x54 -> new JavaWirePacket.SetChunkCacheCenter(readVarInt(in), readVarInt(in));
          case 0x56 -> new JavaWirePacket.SetDefaultSpawnPosition(readPosition(in), in.readFloat());
          case 0x53 -> {
            int slot = in.readUnsignedByte();
            if (slot > 8) {
              throw new JavaWireException("invalid carried item slot=" + slot);
            }
            yield new JavaWirePacket.SetCarriedItem(slot);
          }
          case 0x4B -> {
            JavaNbt motd = JavaNbtCodec.read(in);
            int iconBytes = 0;
            if (readBoolean(in, "server icon present")) {
              iconBytes = readVarInt(in);
              if (iconBytes < 0 || iconBytes > 1_048_576) {
                throw new JavaWireException("invalid server icon length=" + iconBytes);
              }
              if (in.skipBytes(iconBytes) != iconBytes) {
                throw new IOException("truncated server icon");
              }
            }
            yield new JavaWirePacket.ServerData(motd, iconBytes);
          }
          default ->
              throw new JavaWireException(
                  "unsupported play packet id=0x" + Integer.toHexString(id));
        };
    if (in.available() != 0) {
      throw new JavaWireException(
          "malformed play packet id=0x"
              + Integer.toHexString(id)
              + ": trailing bytes="
              + in.available());
    }
    return packet;
  }

  private static JavaWirePacket.PlayLogin decodePlayLogin(DataInputStream in)
      throws IOException, JavaWireException {
    int entityId = in.readInt();
    boolean hardcore = readBoolean(in, "hardcore");
    int dimensionCount = boundedCount(readVarInt(in), "dimension name");
    if (dimensionCount > 1024) {
      throw new JavaWireException("invalid dimension name count=" + dimensionCount);
    }
    List<String> dimensions = new ArrayList<>();
    for (int index = 0; index < dimensionCount; index++) {
      dimensions.add(readIdentifier(in, 0x2B, "dimension name"));
    }
    int maxPlayers = readVarInt(in);
    if (maxPlayers < 0 || maxPlayers > 1_000_000) {
      throw new JavaWireException("invalid max players=" + maxPlayers);
    }
    int viewDistance = readVarInt(in);
    int simulationDistance = readVarInt(in);
    if (viewDistance < 2
        || viewDistance > 32
        || simulationDistance < 2
        || simulationDistance > 32) {
      throw new JavaWireException("invalid PLAY login distance values");
    }
    boolean reducedDebugInfo = readBoolean(in, "reduced debug info");
    boolean enableRespawnScreen = readBoolean(in, "respawn screen");
    boolean doLimitedCrafting = readBoolean(in, "limited crafting");
    int dimensionType = readVarInt(in);
    if (dimensionType < 0) {
      throw new JavaWireException("invalid dimension type=" + dimensionType);
    }
    String dimensionName = readIdentifier(in, 0x2B, "current dimension");
    long hashedSeed = in.readLong();
    int gameMode = in.readUnsignedByte();
    if (gameMode > 3) {
      throw new JavaWireException("invalid game mode=" + gameMode);
    }
    int previousGameMode = in.readByte();
    if (previousGameMode < -1 || previousGameMode > 3) {
      throw new JavaWireException("invalid previous game mode=" + previousGameMode);
    }
    boolean debug = readBoolean(in, "debug world");
    boolean flat = readBoolean(in, "flat world");
    String deathDimensionName = null;
    JavaWirePacket.BlockPosition deathLocation = null;
    if (readBoolean(in, "death location present")) {
      deathDimensionName = readIdentifier(in, 0x2B, "death dimension");
      deathLocation = readPosition(in);
    }
    int portalCooldown = readVarInt(in);
    if (portalCooldown < 0) {
      throw new JavaWireException("invalid portal cooldown=" + portalCooldown);
    }
    boolean secureChat = readBoolean(in, "secure chat enforcement");
    return new JavaWirePacket.PlayLogin(
        entityId,
        hardcore,
        dimensions,
        maxPlayers,
        viewDistance,
        simulationDistance,
        reducedDebugInfo,
        enableRespawnScreen,
        doLimitedCrafting,
        dimensionType,
        dimensionName,
        hashedSeed,
        gameMode,
        previousGameMode,
        debug,
        flat,
        deathDimensionName,
        deathLocation,
        portalCooldown,
        secureChat);
  }

  private static JavaWirePacket.BlockPosition readPosition(DataInputStream in) throws IOException {
    long value = in.readLong();
    int x = (int) (value >> 38);
    int y = (int) (value << 52 >> 52);
    int z = (int) (value << 26 >> 38);
    return new JavaWirePacket.BlockPosition(x, y, z);
  }

  private static boolean readBoolean(DataInputStream in, String label)
      throws IOException, JavaWireException {
    int value = in.readUnsignedByte();
    if (value > 1) {
      throw new JavaWireException("invalid boolean " + label + "=" + value);
    }
    return value == 1;
  }

  private static String readIdentifier(DataInputStream in, int packetId, String label)
      throws IOException, JavaWireException {
    String identifier = readString(in, 32767);
    if (!identifier.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
      throw new JavaWireException(
          "unsupported play packet id=0x"
              + Integer.toHexString(packetId)
              + ": invalid "
              + label
              + " identifier");
    }
    return identifier;
  }

  private static int boundedCount(int count, String label) throws JavaWireException {
    if (count < 0 || count > 65_536) {
      throw new JavaWireException("invalid " + label + " count=" + count);
    }
    return count;
  }

  public static void writeVarInt(OutputStream out, int value) throws IOException {
    while ((value & ~0x7F) != 0) {
      out.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    out.write(value);
  }

  public static int readVarInt(InputStream in) throws IOException, JavaWireException {
    int value = 0;
    int position = 0;
    while (true) {
      int next = in.read();
      if (next < 0) {
        throw new EOFException("truncated VarInt");
      }
      value |= (next & 0x7F) << position;
      if ((next & 0x80) == 0) {
        return value;
      }
      position += 7;
      if (position >= 35) {
        throw new JavaWireException("VarInt exceeds five bytes");
      }
    }
  }

  private static void writeString(OutputStream out, String value, int maxChars)
      throws IOException, JavaWireException {
    if (value == null || value.length() > maxChars) {
      throw new JavaWireException("string exceeds limit");
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_STRING_BYTES) {
      throw new JavaWireException("string bytes exceed limit");
    }
    writeVarInt(out, bytes.length);
    out.write(bytes);
  }

  private static String readString(InputStream in, int maxChars)
      throws IOException, JavaWireException {
    int length = readVarInt(in);
    if (length < 0 || length > MAX_STRING_BYTES) {
      throw new JavaWireException("invalid string length");
    }
    byte[] bytes = in.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException("truncated string");
    }
    String value = new String(bytes, StandardCharsets.UTF_8);
    if (value.length() > maxChars) {
      throw new JavaWireException("string exceeds limit");
    }
    return value;
  }

  private static void writeUuid(OutputStream out, UUID uuid) throws IOException {
    writeLong(out, uuid.getMostSignificantBits());
    writeLong(out, uuid.getLeastSignificantBits());
  }

  private static UUID readUuid(InputStream in) throws IOException {
    return new UUID(readLong(in), readLong(in));
  }

  private static void writeUnsignedShort(OutputStream out, int value) throws IOException {
    if (value < 0 || value > 65535) {
      throw new IOException("port out of range");
    }
    out.write(value >>> 8);
    out.write(value);
  }

  private static void writeLong(OutputStream out, long value) throws IOException {
    DataOutputStream data = new DataOutputStream(out);
    data.writeLong(value);
  }

  private static void writeDouble(OutputStream out, double value) throws IOException {
    new DataOutputStream(out).writeDouble(value);
  }

  private static void writeFloat(OutputStream out, float value) throws IOException {
    new DataOutputStream(out).writeFloat(value);
  }

  private static long readLong(InputStream in) throws IOException {
    return new DataInputStream(in).readLong();
  }

  private static byte[] compress(byte[] raw) throws IOException {
    Deflater d = new Deflater();
    d.setInput(raw);
    d.finish();
    byte[] buffer = new byte[raw.length + 128];
    int length = d.deflate(buffer);
    d.end();
    return java.util.Arrays.copyOf(buffer, length);
  }

  private static byte[] decompress(byte[] compressed, int expected)
      throws IOException, JavaWireException {
    Inflater i = new Inflater();
    i.setInput(compressed);
    byte[] result = new byte[expected];
    try {
      int length = i.inflate(result);
      if (length != expected || !i.finished()) {
        throw new JavaWireException("invalid compressed Java packet");
      }
      return result;
    } catch (DataFormatException e) {
      throw new JavaWireException("invalid compressed Java packet", e);
    } finally {
      i.end();
    }
  }

  public static final class Frame {
    private final int packetId;
    private final byte[] fields;

    public Frame(int packetId, byte[] fields) {
      this.packetId = packetId;
      this.fields = fields.clone();
    }

    public int packetId() {
      return packetId;
    }

    public byte[] fields() {
      return fields.clone();
    }
  }
}
