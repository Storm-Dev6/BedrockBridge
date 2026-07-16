package io.bedrockbridge.bedrock.auth;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.math.BigInteger;
import java.util.Arrays;

/** Converts ECDSA signatures between JWT P1363 and JCA DER encodings. */
public final class EcdsaSignatureCodec {
    private EcdsaSignatureCodec() {}

    /** Converts a 96-byte P-384 r||s signature to strict DER. */
    public static byte[] p1363ToDer(byte[] signature) {
        if (signature.length != 96) throw new BedrockValidationException("Invalid P-384 signature length");
        byte[] r = unsignedInteger(Arrays.copyOfRange(signature, 0, 48));
        byte[] s = unsignedInteger(Arrays.copyOfRange(signature, 48, 96));
        int length = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + length];
        int offset = 0;
        der[offset++] = 0x30;
        der[offset++] = (byte) length;
        der[offset++] = 0x02;
        der[offset++] = (byte) r.length;
        System.arraycopy(r, 0, der, offset, r.length);
        offset += r.length;
        der[offset++] = 0x02;
        der[offset++] = (byte) s.length;
        System.arraycopy(s, 0, der, offset, s.length);
        return der;
    }

    /** Converts strict DER into the 96-byte JWT P-384 r||s form. */
    public static byte[] derToP1363(byte[] der) {
        try {
            int offset = 0;
            if (der[offset++] != 0x30 || Byte.toUnsignedInt(der[offset++]) != der.length - 2) throw invalid();
            if (der[offset++] != 0x02) throw invalid();
            int rLength = Byte.toUnsignedInt(der[offset++]);
            byte[] r = Arrays.copyOfRange(der, offset, offset + rLength);
            offset += rLength;
            if (der[offset++] != 0x02) throw invalid();
            int sLength = Byte.toUnsignedInt(der[offset++]);
            byte[] s = Arrays.copyOfRange(der, offset, offset + sLength);
            if (offset + sLength != der.length) throw invalid();
            byte[] result = new byte[96];
            copyInteger(r, result, 0);
            copyInteger(s, result, 48);
            return result;
        } catch (IndexOutOfBoundsException failure) {
            throw invalid();
        }
    }

    private static byte[] unsignedInteger(byte[] value) {
        byte[] stripped = new BigInteger(1, value).toByteArray();
        return stripped;
    }

    private static void copyInteger(byte[] source, byte[] target, int offset) {
        int start = source.length > 48 && source[0] == 0 ? 1 : 0;
        int length = source.length - start;
        if (length > 48) throw invalid();
        System.arraycopy(source, start, target, offset + 48 - length, length);
    }

    private static BedrockValidationException invalid() {
        return new BedrockValidationException("Invalid DER ECDSA signature");
    }
}
