package io.bedrockbridge.network.raknet;

/** Classifies the flag byte that prefixes a connected RakNet datagram. */
public final class RakNetDatagramFlags {
  private static final int CONNECTED = 0x80;
  private static final int ACKNOWLEDGEMENT = 0x40;
  private static final int NEGATIVE_ACKNOWLEDGEMENT = 0x20;
  private static final int TYPE_MASK = CONNECTED | ACKNOWLEDGEMENT | NEGATIVE_ACKNOWLEDGEMENT;

  private RakNetDatagramFlags() {}

  /** Returns whether the flags describe a DATA datagram, including optional low-bit flags. */
  public static boolean isData(int flags) {
    return flags >= 0 && flags <= 0xFF && (flags & TYPE_MASK) == CONNECTED;
  }

  /** Returns whether the value is one of the connected DATA, ACK, or NACK envelopes. */
  public static boolean isConnected(int flags) {
    return isData(flags) || flags == 0xC0 || flags == 0xA0;
  }
}
