package com.openscale.sync.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.openscale.core.model.Gender;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MqttSyncTargetTest {

    record Sent(String topic, byte[] payload, int qos, boolean retain) {
    }

    private final ScaleUser user = new ScaleUser("Thiago M", "Thiago", 175, 40, Gender.MALE, 80);

    @Test
    void publishesDiscoveryOnceAndStateEveryTime() throws Exception {
        List<Sent> sent = new ArrayList<>();
        MqttPublisher fake = new MqttPublisher() {
            public void publish(String t, byte[] p, int q, boolean r) {
                sent.add(new Sent(t, p, q, r));
            }

            public void close() {
            }
        };
        MqttSyncTarget target = new MqttSyncTarget(fake, "openscale", true, "homeassistant", 1);
        ScaleMeasurement m = ScaleMeasurement.builder(78.456)
                .timestamp(Instant.parse("2026-10-25T20:00:00Z"))
                .source("Relaxmedic")
                .impedances(new double[]{21.3, 512.4})
                .build().withBmi(user);

        target.sync(m, user);
        target.sync(m, user);

        long discovery = sent.stream().filter(s -> s.topic().startsWith("homeassistant/sensor/")).count();
        assertEquals(MqttPayloads.HA_SENSORS.size(), discovery);

        List<Sent> states = sent.stream().filter(s -> s.topic().equals("openscale/thiago_m/measurement")).toList();
        assertEquals(2, states.size());
        assertTrue(states.get(0).retain());

        JsonNode json = MqttPayloads.JSON.readTree(states.get(0).payload());
        assertEquals(78.46, json.get("weight").asDouble(), 1e-9);
        assertEquals(25.6, json.get("bmi").asDouble(), 1e-9);
        assertEquals("2026-10-25T20:00:00Z", json.get("timestamp").asText());
        assertEquals(2, json.get("impedances").size());

        JsonNode weightCfg = MqttPayloads.JSON.readTree(sent.get(0).payload());
        assertEquals("weight", weightCfg.get("device_class").asText());
        assertEquals("kg", weightCfg.get("unit_of_measurement").asText());
        assertEquals("openscale/thiago_m/measurement", weightCfg.get("state_topic").asText());
    }
}
