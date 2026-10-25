package com.openscale.core.util;

/** Utilitários de bytes compartilhados pelos handlers. */
public final class Bytes {

    private Bytes() {
    }

    public static int u16be(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    public static int u16le(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
    }

    public static int u32be(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16)
                | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    public static void putU16be(byte[] d, int o, int v) {
        d[o] = (byte) ((v >> 8) & 0xFF);
        d[o + 1] = (byte) (v & 0xFF);
    }

    public static void putU32be(byte[] d, int o, long v) {
        d[o] = (byte) ((v >> 24) & 0xFF);
        d[o + 1] = (byte) ((v >> 16) & 0xFF);
        d[o + 2] = (byte) ((v >> 8) & 0xFF);
        d[o + 3] = (byte) (v & 0xFF);
    }

    public static byte[] slice(byte[] src, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(src, from, out, 0, out.length);
        return out;
    }

    public static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02x", data[i] & 0xFF));
        }
        return sb.toString();
    }

    /** Aceita "a3 00 01" ou "a30001". */
    public static byte[] fromHex(String hex) {
        String s = hex.replaceAll("[^0-9a-fA-F]", "");
        if (s.length() % 2 != 0) throw new IllegalArgumentException("hex com número ímpar de dígitos");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
