package com.openscale.scales.relaxmedic;

import com.openscale.core.util.Bytes;

import java.util.TimeZone;

/**
 * Protocolo da balança Relaxmedic (app Fitdays, protocolo icomon v107).
 * Portado de openScale {@code RelaxmedicHandler.kt} (GPL-3.0).
 *
 * <pre>
 * Frame (20 bytes, nos dois sentidos):
 *   [seq][len][fragment][16 bytes de payload][checksum]
 *   len      = tamanho da mensagem remontada sem o byte de comando
 *   checksum = sum(bytes[3..18]) &amp; 0x1F
 * </pre>
 */
public final class RelaxmedicProtocol {

    public static final int FRAME_SIZE = 20;
    public static final int CHUNK_SIZE = 16;

    public static final int TYPE_ACK_IN = 0xA0;
    public static final int TYPE_DEVICE_INFO = 0xA1;
    public static final int TYPE_LIVE_WEIGHT = 0xA2;
    public static final int TYPE_FINAL_RESULT = 0xA3;
    public static final int TYPE_ACK_OUT = 0xB0;
    public static final int TYPE_USER_PROFILE = 0xBA;

    /** Gramas ficam nos 18 bits baixos do u32. */
    static final int WEIGHT_MASK = 0x3FFFF;

    /** Bytes literais de uma captura aceita pela balança (significado só parcialmente conhecido). */
    static final int FLAGS2 = 0x2F;
    static final int TRAILING = 0x0F;

    /** Mensagem remontada. */
    public record Message(int command, byte[] payload, int seq) {
    }

    /** Resultado final 0xA3. */
    public record FinalResult(int grams, double[] impedancesOhm) {
        public double weightKg() {
            return grams / 1000.0;
        }
    }

    private int outgoingSeq = 0;
    private byte[] stream = new byte[0];
    private int expectedLength = 0;

    public void reset() {
        outgoingSeq = 0;
        stream = new byte[0];
        expectedLength = 0;
    }

    // ------------------------------------------------------------ recepção

    /** Recebe um frame; devolve a mensagem quando todos os fragmentos chegaram. */
    public Message onFrame(byte[] frame) {
        if (frame == null || frame.length != FRAME_SIZE || !isChecksumValid(frame)) return null;

        int length = frame[1] & 0xFF;
        int fragment = frame[2] & 0xFF;
        byte[] chunk = Bytes.slice(frame, 3, 19);

        if (fragment == 0) {
            stream = chunk;
            expectedLength = length;
        } else {
            if (stream.length == 0) return null;
            stream = Bytes.concat(stream, chunk);
        }
        if (stream.length < expectedLength + 1) return null;

        byte[] message = Bytes.slice(stream, 0, expectedLength + 1);
        stream = new byte[0];
        expectedLength = 0;
        return new Message(message[0] & 0xFF, Bytes.slice(message, 1, message.length), frame[0] & 0xFF);
    }

    /** 0xA2: {@code [state][u32 BE][heart rate][mode]}. */
    public static double parseLiveWeightKg(byte[] payload) {
        if (payload.length < 5) return -1;
        return (Bytes.u32be(payload, 1) & WEIGHT_MASK) / 1000.0;
    }

    /** 0xA3: u32 BE (gramas), 2 bytes livres, impedâncias u16 BE em décimos de ohm. */
    public static FinalResult parseFinalResult(byte[] payload) {
        if (payload.length < 5) return null;
        int grams = Bytes.u32be(payload, 0) & WEIGHT_MASK;
        double[] imps = new double[(payload.length - 5) / 2];
        for (int i = 0; i < imps.length; i++) {
            imps[i] = Bytes.u16be(payload, 5 + 2 * i) / 10.0;
        }
        return new FinalResult(grams, imps);
    }

    // ------------------------------------------------------------ envio

    /**
     * 0xBA — perfil do usuário. Sexo e idade só vão no byte flags1; se estiverem
     * errados, a balança aceita em silêncio e calcula para a pessoa errada.
     */
    public byte[] buildUserProfileFrame(int heightCm, int age, boolean male, double profileKg, long nowMillis,
                                        TimeZone zone) {
        int offsetMinutes = zone.getOffset(nowMillis) / 60000;
        int encodedOffset = Math.abs(offsetMinutes) & 0x7FFF;
        if (offsetMinutes < 0) encodedOffset |= 0x8000;

        byte[] msg = new byte[17];
        msg[0] = (byte) TYPE_USER_PROFILE;
        Bytes.putU32be(msg, 1, nowMillis / 1000L);
        Bytes.putU16be(msg, 5, encodedOffset);
        Bytes.putU32be(msg, 7, 0L); // user id
        msg[11] = (byte) (heightCm & 0xFF);
        Bytes.putU16be(msg, 12, (int) ((profileKg > 0 ? profileKg : 60.0) * 100.0));
        msg[14] = (byte) (((male ? 1 : 0) << 7) | (age & 0x7F));
        msg[15] = (byte) FLAGS2;
        msg[16] = (byte) TRAILING; // não cabe no frame; o app oficial também o descarta
        return buildFrame(msg);
    }

    /** 0xB0 — confirma um frame recebido. */
    public byte[] buildAckFrame(int seq) {
        return buildFrame(new byte[]{(byte) TYPE_ACK_OUT, (byte) (seq & 0xFF), 0x00});
    }

    private byte[] buildFrame(byte[] message) {
        byte[] frame = frameFragment(outgoingSeq, message.length - 1, 0, message, 0);
        outgoingSeq = (outgoingSeq + 1) & 0xFF;
        return frame;
    }

    /** Monta um frame com até 16 bytes de {@code message} a partir de {@code offset}. */
    static byte[] frameFragment(int seq, int len, int fragment, byte[] message, int offset) {
        byte[] frame = new byte[FRAME_SIZE];
        frame[0] = (byte) (seq & 0xFF);
        frame[1] = (byte) (len & 0xFF);
        frame[2] = (byte) (fragment & 0xFF);
        int carried = Math.max(0, Math.min(CHUNK_SIZE, message.length - offset));
        System.arraycopy(message, offset, frame, 3, carried);
        frame[19] = (byte) checksum(frame);
        return frame;
    }

    static int checksum(byte[] frame) {
        int sum = 0;
        for (int i = 3; i <= 18; i++) sum += frame[i] & 0xFF;
        return sum & 0x1F;
    }

    static boolean isChecksumValid(byte[] frame) {
        return (frame[19] & 0xFF) == checksum(frame);
    }
}
