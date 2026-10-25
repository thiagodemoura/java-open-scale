package com.openscale.scales.relaxmedic;

import com.openscale.core.ble.SimulatedBleTransport;
import com.openscale.core.model.Gender;
import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.scale.Capability;
import com.openscale.core.scale.ScaleHandler;
import com.openscale.core.scale.ScaleHandlerProvider;
import com.openscale.core.scale.ScaleHandlerRegistry;
import com.openscale.core.scale.ScaleSession;
import com.openscale.core.util.Bytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class RelaxmedicHandlerTest {

    private static final long NOW = 1_793_000_000_000L;
    private static final TimeZone FORTALEZA = TimeZone.getTimeZone("America/Fortaleza");
    private final ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);

    @Test
    void registryFindsRelaxmedicByAdvertisedName() {
        ScaleHandlerRegistry reg = ScaleHandlerRegistry.fromClasspath();
        assertTrue(reg.find("Relaxmedic-1234").isPresent());
        assertTrue(reg.find("SWAN").isEmpty());
        assertTrue(reg.find("Mi Scale").isEmpty());
    }

    @Test
    void fullSessionPublishesOneMeasurementAndAcks() {
        SimulatedBleTransport transport = new SimulatedBleTransport("Relaxmedic");
        new RelaxmedicSimulator(transport, 78.45, RelaxmedicSimulator.defaultImpedances()).attach();

        List<ScaleMeasurement> results = new ArrayList<>();
        List<Double> live = new ArrayList<>();
        new ScaleSession(transport, fixedClockProvider(), user, new ScaleSession.Listener() {
            @Override
            public void onMeasurement(ScaleMeasurement m) {
                results.add(m);
            }

            @Override
            public void onLiveWeight(double kg) {
                live.add(kg);
            }
        }).start();

        assertTrue(transport.isSubscribed(RelaxmedicHandler.CHAR_LIVE));
        assertTrue(transport.isSubscribed(RelaxmedicHandler.CHAR_RESULT));

        assertEquals(1, results.size(), "resultado repetido deve ser publicado uma vez");
        ScaleMeasurement m = results.get(0);
        assertEquals(78.45, m.weightKg(), 1e-9);
        assertEquals(10, m.impedancesOhm().length);
        assertEquals(21.3, m.get(MeasurementType.IMPEDANCE).getAsDouble(), 1e-9);
        assertEquals(25.6, m.get(MeasurementType.BMI).getAsDouble(), 1e-9);
        assertEquals("Relaxmedic", m.source());

        assertFalse(live.isEmpty());
        assertEquals(78.45, live.get(live.size() - 1), 1e-9);

        // 1 perfil + 3 ACKs (um por repetição do 0xA3)
        long acks = transport.writes().stream().filter(w -> (w.value()[3] & 0xFF) == 0xB0).count();
        assertEquals(3, acks);
    }

    @Test
    void profileFrameMatchesOpenScaleLayout() {
        RelaxmedicProtocol p = new RelaxmedicProtocol();
        byte[] f = p.buildUserProfileFrame(175, 40, true, 80, NOW, FORTALEZA);

        assertEquals(20, f.length);
        assertEquals(0x00, f[0] & 0xFF);                 // seq
        assertEquals(0x10, f[1] & 0xFF);                 // len = 16
        assertEquals(0xBA, f[3] & 0xFF);
        assertEquals(NOW / 1000, Integer.toUnsignedLong(Bytes.u32be(f, 4)));
        assertEquals(0x8000 | 180, Bytes.u16be(f, 8));   // UTC-3
        assertEquals(175, f[14] & 0xFF);
        assertEquals(8000, Bytes.u16be(f, 15));
        assertEquals(0x80 | 40, f[17] & 0xFF);           // masculino, 40 anos
        assertEquals(0x2F, f[18] & 0xFF);                // flags2; trailing descartado
        assertTrue(RelaxmedicProtocol.isChecksumValid(f));
    }

    @Test
    void corruptedFrameIsIgnored() {
        RelaxmedicSimulator sim = new RelaxmedicSimulator(new SimulatedBleTransport("x"), 70, new double[0]);
        byte[] frame = sim.liveFrames(70_000).get(0);
        frame[10] ^= 0x01;
        assertNull(new RelaxmedicProtocol().onFrame(frame));
    }

    @Test
    void femaleFlagHasSexBitCleared() {
        byte[] f = new RelaxmedicProtocol().buildUserProfileFrame(160, 35, false, 60, NOW, FORTALEZA);
        assertEquals(35, f[17] & 0xFF);
    }

    private ScaleHandlerProvider fixedClockProvider() {
        return new ScaleHandlerProvider() {
            public String displayName() {
                return "Relaxmedic";
            }

            public boolean supports(String n) {
                return true;
            }

            public Set<Capability> capabilities() {
                return Set.of();
            }

            public ScaleHandler create() {
                return new RelaxmedicHandler(() -> NOW, FORTALEZA);
            }
        };
    }
}
