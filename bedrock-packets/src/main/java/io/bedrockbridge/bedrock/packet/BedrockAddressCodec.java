package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/** RakNet IPv4/IPv6 socket-address serializer used by handshake packets. */
public final class BedrockAddressCodec {
    private BedrockAddressCodec() {}

    /** Writes an IPv4 or IPv6 endpoint using RakNet address encoding. */
    public static void write(PacketWriter writer, InetSocketAddress address) {
        byte[] bytes = address.getAddress().getAddress();
        if (address.getAddress() instanceof Inet4Address) {
            writer.writeByte((byte) 4);
            byte[] inverted = bytes.clone();
            for (int index = 0; index < inverted.length; index++) {
                inverted[index] = (byte) ~inverted[index];
            }
            writer.writeBytes(ByteBuffer.wrap(inverted));
            writer.writeUnsignedShort(address.getPort());
        } else if (address.getAddress() instanceof Inet6Address ipv6) {
            writer.writeByte((byte) 6);
            writer.writeUnsignedShort(23);
            writer.writeUnsignedShort(address.getPort());
            writer.writeInt(0);
            writer.writeBytes(ByteBuffer.wrap(bytes));
            writer.writeInt(ipv6.getScopeId());
        } else {
            throw new IllegalArgumentException("Unsupported address family");
        }
    }

    /** Reads a validated IPv4 or IPv6 endpoint. */
    public static InetSocketAddress read(PacketReader reader) {
        int version = Byte.toUnsignedInt(reader.readByte());
        try {
            if (version == 4) {
                byte[] bytes = copy(reader.readSlice(4));
                for (int index = 0; index < bytes.length; index++) {
                    bytes[index] = (byte) ~bytes[index];
                }
                return new InetSocketAddress(InetAddress.getByAddress(bytes), reader.readUnsignedShort());
            }
            if (version == 6) {
                reader.readUnsignedShort();
                int port = reader.readUnsignedShort();
                reader.readInt();
                byte[] bytes = copy(reader.readSlice(16));
                int scope = reader.readInt();
                return new InetSocketAddress(Inet6Address.getByAddress(null, bytes, scope), port);
            }
            throw new IllegalArgumentException("Unsupported address version: " + version);
        } catch (UnknownHostException impossible) {
            throw new IllegalArgumentException("Invalid encoded address", impossible);
        }
    }

    private static byte[] copy(ByteBuffer input) {
        byte[] bytes = new byte[input.remaining()];
        input.get(bytes);
        return bytes;
    }
}
