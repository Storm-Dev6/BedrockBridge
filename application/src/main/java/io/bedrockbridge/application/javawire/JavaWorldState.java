package io.bedrockbridge.application.javawire;

import java.util.HashSet;
import java.util.Set;

/** Minimal bounded world-state model populated by decoded Java PLAY packets. */
public final class JavaWorldState {
  private JavaWirePacket.PlayLogin login;
  private JavaWirePacket.BlockPosition defaultSpawn;
  private int centerChunkX;
  private int centerChunkZ;
  private final Set<ChunkKey> knownChunks = new HashSet<>();

  public void apply(JavaWirePacket packet) throws JavaWireException {
    if (packet instanceof JavaWirePacket.PlayLogin value) {
      login = value;
    } else if (packet instanceof JavaWirePacket.SetChunkCacheCenter value) {
      centerChunkX = value.chunkX();
      centerChunkZ = value.chunkZ();
    } else if (packet instanceof JavaWirePacket.SetDefaultSpawnPosition value) {
      defaultSpawn = value.location();
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

  public Set<ChunkKey> knownChunks() {
    return Set.copyOf(knownChunks);
  }

  /** Records a chunk only after a future, fully decoded chunk payload is available. */
  public void recordChunk(int chunkX, int chunkZ) {
    knownChunks.add(new ChunkKey(chunkX, chunkZ));
  }

  public record ChunkKey(int x, int z) {}
}
