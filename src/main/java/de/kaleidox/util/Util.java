package de.kaleidox.util;

import static java.lang.System.arraycopy;

public class Util {
    public static byte[] stringToBytesASCII(String str) {
        if (str == null) return new byte[0];
        byte[] b = new byte[str.length()];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) str.charAt(i);
        }
        return b;
    }

    public static String bytesASCIIToString(byte[] bytes) {
        return new String(bytes);
    }

    public static byte[] appendByteArray(byte[] ba1, byte... ba2) {
        byte[] bytes = new byte[ba1.length + ba2.length];

        arraycopy(ba1, 0, bytes, 0, ba1.length);
        arraycopy(ba2, 0, bytes, ba1.length, ba2.length);

        return bytes;
    }

    public static byte[] minSizeArray(byte[] base, int size) {
        byte[] bytes = new byte[size];
        arraycopy(base, 0, bytes, 0, base.length);
        return bytes;
    }

    public static byte[] createByteArray(Object data) {
        if (data instanceof String) {
            return stringToBytesASCII((String) data); // todo Implement more data Types
        } else throw new IllegalArgumentException("Unknown Data Type! Please contact the developer.");
    }
}
