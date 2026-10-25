package com.openscale.scales.relaxmedic;

import com.openscale.core.ble.BleUuids;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.scale.ScaleHandler;

import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Handler da balança Relaxmedic.
 *
 * <ol>
 *   <li>Liga notify em FFB2 (peso ao vivo) e indicate em FFB3 (resultado);</li>
 *   <li>envia o perfil 0xBA em FFB1;</li>
 *   <li>recebe 0xA2 (provisório) e 0xA3 (definitivo), respondendo 0xB0.</li>
 * </ol>
 *
 * A composição corporal não é calculada aqui: o openScale usa a biblioteca
 * Wla25BodyComposition para isso, e ela não foi portada. A medição publicada traz
 * o peso e as impedâncias brutas.
 */
public final class RelaxmedicHandler extends ScaleHandler {

    public static final UUID SERVICE = BleUuids.uuid16(0xFFB0);
    public static final UUID CHAR_WRITE = BleUuids.uuid16(0xFFB1);
    public static final UUID CHAR_LIVE = BleUuids.uuid16(0xFFB2);
    public static final UUID CHAR_RESULT = BleUuids.uuid16(0xFFB3);

    private final RelaxmedicProtocol protocol = new RelaxmedicProtocol();
    private final LongSupplier clock;
    private final TimeZone zone;

    private double lastLiveKg = -1;
    private int lastPublishedGrams = -1;

    public RelaxmedicHandler() {
        this(System::currentTimeMillis, TimeZone.getDefault());
    }

    RelaxmedicHandler(LongSupplier clock, TimeZone zone) {
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public void onConnected(ScaleUser user) {
        protocol.reset();
        lastLiveKg = -1;
        lastPublishedGrams = -1;

        ctx().info("Suba na balança e fique parado até o resultado.");
        ctx().enableNotifications(SERVICE, CHAR_LIVE);
        ctx().enableNotifications(SERVICE, CHAR_RESULT);
        ctx().write(SERVICE, CHAR_WRITE, protocol.buildUserProfileFrame(
                user.heightCm(), user.age(), user.gender().isMale(), user.initialWeightKg(),
                clock.getAsLong(), zone), true);
    }

    @Override
    public void onNotification(UUID characteristic, byte[] data, ScaleUser user) {
        RelaxmedicProtocol.Message msg = protocol.onFrame(data);
        if (msg == null) return;

        switch (msg.command()) {
            case RelaxmedicProtocol.TYPE_LIVE_WEIGHT -> {
                double kg = RelaxmedicProtocol.parseLiveWeightKg(msg.payload());
                if (kg >= 0 && Math.abs(kg - lastLiveKg) >= 0.05) {
                    lastLiveKg = kg;
                    ctx().liveWeight(kg);
                }
            }
            case RelaxmedicProtocol.TYPE_FINAL_RESULT -> {
                RelaxmedicProtocol.FinalResult r = RelaxmedicProtocol.parseFinalResult(msg.payload());
                // A balança repete o resultado; só o primeiro é publicado.
                if (r != null && r.grams() != lastPublishedGrams && r.grams() > 0) {
                    lastPublishedGrams = r.grams();
                    ctx().publish(ScaleMeasurement.builder(r.weightKg())
                            .timestamp(Instant.ofEpochMilli(clock.getAsLong()))
                            .source("Relaxmedic")
                            .impedances(r.impedancesOhm())
                            .build());
                }
                ack(msg.seq());
            }
            case RelaxmedicProtocol.TYPE_ACK_IN -> ack(msg.seq());
            case RelaxmedicProtocol.TYPE_DEVICE_INFO ->
                    ctx().debug("Relaxmedic device info, " + msg.payload().length + " bytes");
            default -> ctx().debug(String.format("Relaxmedic comando não tratado 0x%02X", msg.command()));
        }
    }

    private void ack(int seq) {
        ctx().write(SERVICE, CHAR_WRITE, protocol.buildAckFrame(seq), true);
    }
}
