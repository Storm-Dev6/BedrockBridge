package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.Packet;

/** Combined encoder and decoder for one packet class. */
public interface PacketCodec<T extends Packet> extends PacketEncoder<T>, PacketDecoder<T> {}
