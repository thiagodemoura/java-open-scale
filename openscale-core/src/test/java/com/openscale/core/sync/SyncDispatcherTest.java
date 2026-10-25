package com.openscale.core.sync;

import com.openscale.core.model.Gender;
import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.csv.CsvSyncTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SyncDispatcherTest {

    private final ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);

    @Test
    void failingTargetDoesNotStopOthers(@TempDir Path dir) throws Exception {
        SyncTarget broken = new SyncTarget() {
            public String id() {
                return "broken";
            }

            public void sync(ScaleMeasurement m, ScaleUser u) throws SyncException {
                throw new SyncException("offline");
            }
        };
        CsvSyncTarget csv = new CsvSyncTarget(dir, ZoneId.of("America/Fortaleza"));
        SyncDispatcher d = new SyncDispatcher().add(broken).add(csv);

        ScaleMeasurement m = ScaleMeasurement.builder(78.45)
                .timestamp(Instant.parse("2026-10-25T23:30:00Z"))
                .source("Relaxmedic")
                .build().withBmi(user);
        List<SyncResult> results = d.dispatch(m, user);

        assertFalse(results.get(0).success());
        assertEquals("offline", results.get(0).message());
        assertTrue(results.get(1).success());

        d.dispatch(m, user);
        List<String> lines = Files.readAllLines(csv.fileFor(user));
        assertEquals(3, lines.size(), "cabeçalho + 2 linhas");
        assertTrue(lines.get(0).startsWith("\"dateTime\",\"weight\",\"bmi\""));
        assertTrue(lines.get(1).startsWith("\"2026-10-25 20:30:00\",78.45,25.60,"), lines.get(1));
        assertTrue(lines.get(1).endsWith(",\"Relaxmedic\""));
    }

    @Test
    void csvFactoryIsDiscoveredViaServiceLoader(@TempDir Path dir) {
        Properties p = new Properties();
        p.setProperty("sync.csv.enabled", "true");
        p.setProperty("sync.csv.directory", dir.toString());
        List<SyncTarget> targets = SyncTargetFactory.createEnabled(new SyncConfig(p));
        assertEquals(1, targets.size());
        assertEquals("csv", targets.get(0).id());
        assertTrue(SyncTargetFactory.available().contains("csv"));
    }

    @Test
    void measurementRequiresWeightAndComputesBmi() {
        ScaleMeasurement m = ScaleMeasurement.builder(70).build().withBmi(user);
        assertEquals(22.9, m.get(MeasurementType.BMI).getAsDouble(), 1e-9);
    }
}
