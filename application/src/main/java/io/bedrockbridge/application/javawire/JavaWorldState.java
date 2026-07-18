package io.bedrockbridge.application.javawire;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Minimal bounded world-state model populated by decoded Java PLAY packets. */
public final class JavaWorldState {
  private JavaWirePacket.PlayLogin login;
  private JavaWirePacket.BlockPosition defaultSpawn;
  private int centerChunkX;
  private int centerChunkZ;
  private int viewDistance;
  private int simulationDistance;
  private long worldAge;
  private long timeOfDay;
  private final Set<ChunkKey> knownChunks = new HashSet<>();
  private final Map<ChunkKey, JavaWirePacket.ChunkData> chunks = new LinkedHashMap<>();
  private final Map<ChunkKey, JavaWirePacket.LightData> lightUpdates = new LinkedHashMap<>();

  public void apply(JavaWirePacket packet) throws JavaWireException {
    if (packet instanceof JavaWirePacket.PlayLogin value) {
      login = value;
    } else if (packet instanceof JavaWirePacket.SetChunkCacheCenter value) {
      centerChunkX = value.chunkX();
      centerChunkZ = value.chunkZ();
    } else if (packet instanceof JavaWirePacket.SetChunkCacheRadius value) {
      viewDistance = value.viewDistance();
    } else if (packet instanceof JavaWirePacket.SetSimulationDistance value) {
      simulationDistance = value.simulationDistance();
    } else if (packet instanceof JavaWirePacket.SetTime value) {
      worldAge = value.worldAge();
      timeOfDay = value.timeOfDay();
    } else if (packet instanceof JavaWirePacket.SetDefaultSpawnPosition value) {
      defaultSpawn = value.location();
    } else if (packet instanceof JavaWirePacket.ChunkData value) {
      ChunkKey key = new ChunkKey(value.chunkX(), value.chunkZ());
      chunks.put(key, value);
      knownChunks.add(key);
    } else if (packet instanceof JavaWirePacket.LightUpdate value) {
      lightUpdates.put(new ChunkKey(value.chunkX(), value.chunkZ()), value.light());
    } else if (packet instanceof JavaWirePacket.PlayDisconnect) {
      // Lifecycle packet; the session owns disconnect propagation.
    } else if (packet instanceof JavaWirePacket.ChunkBatchStart
        || packet instanceof JavaWirePacket.ChunkBatchFinished) {
      // Chunk batching is tracked by the transport; no chunk payload is invented here.
    }
  }

  public JavaWirePacket.PlayLogin login() {
    return login;
  }

  public JavaWirePacket.BlockPosition defaultSpawn() {
    return defaultSpawn;
  }

  public int centerChunkX() {
    return centerChunkX;
  }

  public int centerChunkZ() {
    return centerChunkZ;
  }

  public int viewDistance() {
    return viewDistance;
  }

  public int simulationDistance() {
    return simulationDistance;
  }

  public long worldAge() {
    return worldAge;
  }

  public long timeOfDay() {
    return timeOfDay;
  }

  public Set<ChunkKey> knownChunks() {
    return Set.copyOf(knownChunks);
  }

  public Map<ChunkKey, JavaWirePacket.ChunkData> chunks() {
    return Map.copyOf(chunks);
  }

  public Map<ChunkKey, JavaWirePacket.LightData> lightUpdates() {
    return Map.copyOf(lightUpdates);
  }

  /** Records a chunk only after a future, fully decoded chunk payload is available. */
  public void recordChunk(int chunkX, int chunkZ) {
    knownChunks.add(new ChunkKey(chunkX, chunkZ));
  }

  public record ChunkKey(int x, int z) {}
}
