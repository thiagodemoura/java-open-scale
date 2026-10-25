package com.openscale.sync.healthconnect;

import com.openscale.core.model.Gender;
import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HealthConnectMapperTest {

    private final ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);
    private final Instant t = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void weightOnlyProducesSingleWeightRecord() {
        List<HealthConnectRecord> r = HealthConnectMapper.map(
                ScaleMeasurement.builder(80).timestamp(t).build(), user, ZoneId.of("America/Fortaleza"));
        assertEquals(1, r.size());
        HealthConnectRecord.Weight w = (HealthConnectRecord.Weight) r.get(0);
        assertEquals(80.0, w.kilograms());
        assertEquals(ZoneOffset.ofHours(-3), w.zoneOffset());
        assertEquals("openscale-thiago-" + t.toEpochMilli() + "-weight", w.clientRecordId());
    }

    @Test
    void waterPercentBecomesMass() {
        ScaleMeasurement m = ScaleMeasurement.builder(80).timestamp(t)
                .set(MeasurementType.BODY_FAT, 22.5)
                .set(MeasurementType.WATER, 55.0)
                .set(MeasurementType.BMR, 1750)
                .build();
        List<HealthConnectRecord> r = HealthConnectMapper.map(m, user, ZoneId.of("UTC"));
        assertEquals(4, r.size());
        HealthConnectRecord.BodyWaterMass water = r.stream()
                .filter(HealthConnectRecord.BodyWaterMass.class::isInstance)
                .map(HealthConnectRecord.BodyWaterMass.class::cast).findFirst().orElseThrow();
        assertEquals(44.0, water.kilograms(), 1e-9);
    }

    @Test
    void syncTargetWrapsWriterErrors() {
        HealthConnectSyncTarget target = new HealthConnectSyncTarget(recs -> {
            throw new SecurityException("permissão negada");
        }, ZoneId.of("UTC"));
        Exception e = assertThrows(Exception.class,
                () -> target.sync(ScaleMeasurement.builder(80).build(), user));
        assertTrue(e.getMessage().contains("permissão negada"));

        List<HealthConnectRecord> written = new ArrayList<>();
        assertDoesNotThrow(() -> new HealthConnectSyncTarget(written::addAll, ZoneId.of("UTC"))
                .sync(ScaleMeasurement.builder(80).build(), user));
        assertEquals(1, written.size());
    }
}
