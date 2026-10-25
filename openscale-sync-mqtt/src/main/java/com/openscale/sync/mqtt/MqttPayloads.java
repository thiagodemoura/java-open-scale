package com.openscale.sync.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Monta os payloads JSON (estado e descoberta do Home Assistant). */
public final class MqttPayloads {

    static final ObjectMapper JSON = new ObjectMapper();

    /** Sensores anunciados ao Home Assistant. */
    static final List<MeasurementType> HA_SENSORS = List.of(
            MeasurementType.WEIGHT, MeasurementType.BMI, MeasurementType.BODY_FAT, MeasurementType.WATER,
            MeasurementType.MUSCLE, MeasurementType.BONE, MeasurementType.LBM, MeasurementType.VISCERAL_FAT,
            MeasurementType.BMR, MeasurementType.IMPEDANCE);

    private MqttPayloads() {
    }

    public static String topicSafe(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    public static String stateTopic(String prefix, ScaleUser user) {
        return prefix + "/" + topicSafe(user.id()) + "/measurement";
    }

    public static byte[] state(ScaleMeasurement m, ScaleUser user) {
        ObjectNode n = JSON.createObjectNode();
        n.put("timestamp", m.timestamp().toString());
        n.put("source", m.source());
        n.put("user", user.id());
        m.values().forEach((t, v) -> n.put(t.key(), Math.round(v * 100.0) / 100.0));
        double[] imps = m.impedancesOhm();
        if (imps.length > 1) {
            ArrayNode arr = n.putArray("impedances");
            for (double d : imps) arr.add(d);
        }
        return bytes(n);
    }

    /** Tópico → payload de configuração, um por sensor. */
    public static Map<String, byte[]> homeAssistantDiscovery(String haPrefix, String statePrefix, ScaleUser user) {
        String uid = "openscale_" + topicSafe(user.id());
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (MeasurementType t : HA_SENSORS) {
            ObjectNode n = JSON.createObjectNode();
            n.put("name", label(t));
            n.put("unique_id", uid + "_" + t.key());
            n.put("object_id", uid + "_" + t.key());
            n.put("state_topic", stateTopic(statePrefix, user));
            n.put("value_template", "{{ value_json." + t.key() + " | default(none) }}");
            n.put("state_class", "measurement");
            if (!t.unit().isEmpty()) n.put("unit_of_measurement", t.unit());
            if (t == MeasurementType.WEIGHT || t == MeasurementType.BONE || t == MeasurementType.LBM) {
                n.put("device_class", "weight");
            }
            ObjectNode dev = n.putObject("device");
            dev.putArray("identifiers").add(uid);
            dev.put("name", "openScale " + user.name());
            dev.put("manufacturer", "openScale Java");
            out.put(haPrefix + "/sensor/" + uid + "/" + t.key() + "/config", bytes(n));
        }
        return out;
    }

    private static String label(MeasurementType t) {
        return switch (t) {
            case WEIGHT -> "Peso";
            case BMI -> "IMC";
            case BODY_FAT -> "Gordura corporal";
            case WATER -> "Água corporal";
            case MUSCLE -> "Massa muscular";
            case BONE -> "Massa óssea";
            case LBM -> "Massa magra";
            case VISCERAL_FAT -> "Gordura visceral";
            case BMR -> "Metabolismo basal";
            case IMPEDANCE -> "Impedância";
            default -> t.key();
        };
    }

    private static byte[] bytes(ObjectNode n) {
        try {
            return JSON.writeValueAsBytes(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
