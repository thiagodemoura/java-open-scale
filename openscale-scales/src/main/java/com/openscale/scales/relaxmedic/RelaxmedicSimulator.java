package com.openscale.scales.relaxmedic;

import com.openscale.core.ble.SimulatedBleTransport;
import com.openscale.core.util.Bytes;

import java.util.ArrayList;
import java.util.List;

/**
 * Simula o lado da balança: quando recebe o perfil 0xBA, envia algumas leituras
 * ao vivo (0xA2) e depois o resultado final (0xA3), repetido como a balança real faz.
 */
public final class RelaxmedicSimulator {

    private final SimulatedBleTransport transport;
    private final int finalGrams;
    private final double[] impedancesOhm;
    private int seq = 0x40;
    private boolean started = false;

    public RelaxmedicSimulator(SimulatedBleTransport transport, double weightKg, double[] impedancesOhm) {
        this.transport = transport;
        this.finalGrams = (int) Math.round(weightKg * 1000);
        this.impedancesOhm = impedancesOhm.clone();
    }

    /** Impedâncias plausíveis: 10 valores em dois grupos de 5, cada um começando baixo. */
    public static double[] defaultImpedances() {
        return new double[]{21.3, 512.4, 498.7, 455.2, 470.9, 20.8, 540.1, 520.6, 480.3, 495.5};
    }

    /** Liga a simulação ao transporte: responde ao perfil automaticamente. */
    public RelaxmedicSimulator attach() {
        transport.onWrite(w -> {
            if (started || !w.characteristic().equals(RelaxmedicHandler.CHAR_WRITE)) return;
            if ((w.value()[3] & 0xFF) != RelaxmedicProtocol.TYPE_USER_PROFILE) return;
            started = true;
            run();
        });
        return this;
    }

    private void run() {
        // leituras ao vivo subindo até o valor final
        // passos de 150 g ou mais: o handler ignora variações ao vivo menores que 50 g
        int[] steps = {finalGrams / 3, finalGrams * 2 / 3, finalGrams - 400, finalGrams - 150, finalGrams};
        for (int g : steps) {
            for (byte[] f : liveFrames(g)) transport.inject(RelaxmedicHandler.CHAR_LIVE, f);
        }
        for (int i = 0; i < 3; i++) {
            for (byte[] f : finalFrames(finalGrams, impedancesOhm)) transport.inject(RelaxmedicHandler.CHAR_RESULT, f);
        }
    }

    public List<byte[]> liveFrames(int grams) {
        byte[] msg = new byte[8];
        msg[0] = (byte) RelaxmedicProtocol.TYPE_LIVE_WEIGHT;
        msg[1] = 0x01; // state
        Bytes.putU32be(msg, 2, grams & RelaxmedicProtocol.WEIGHT_MASK);
        return fragment(msg);
    }

    public List<byte[]> finalFrames(int grams, double[] imps) {
        byte[] msg = new byte[1 + 5 + imps.length * 2];
        msg[0] = (byte) RelaxmedicProtocol.TYPE_FINAL_RESULT;
        Bytes.putU32be(msg, 1, grams & RelaxmedicProtocol.WEIGHT_MASK);
        for (int i = 0; i < imps.length; i++) {
            Bytes.putU16be(msg, 6 + 2 * i, (int) Math.round(imps[i] * 10));
        }
        return fragment(msg);
    }

    private List<byte[]> fragment(byte[] message) {
        List<byte[]> frames = new ArrayList<>();
        int len = message.length - 1;
        int fragment = 0;
        for (int off = 0; off < message.length; off += RelaxmedicProtocol.CHUNK_SIZE) {
            frames.add(RelaxmedicProtocol.frameFragment(seq, len, fragment++, message, off));
            seq = (seq + 1) & 0xFF;
        }
        return frames;
    }
}
