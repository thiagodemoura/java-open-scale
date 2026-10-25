package com.openscale.core.composition;

import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Resultado completo de uma pesagem: todos os indicadores pedidos, cada um com
 * valor (ou ausência justificada) e origem.
 */
public final class BodyComposition {

    private final ScaleMeasurement measurement;
    private final ScaleUser user;
    private final EnumMap<Metric, MetricValue> values;

    BodyComposition(ScaleMeasurement measurement, ScaleUser user, EnumMap<Metric, MetricValue> values) {
        this.measurement = measurement;
        this.user = user;
        this.values = values;
    }

    /** Medição bruta (peso, impedâncias, horário, origem). */
    public ScaleMeasurement measurement() {
        return measurement;
    }

    public ScaleUser user() {
        return user;
    }

    public MetricValue get(Metric metric) {
        return values.get(metric);
    }

    public Map<Metric, MetricValue> all() {
        return Collections.unmodifiableMap(values);
    }

    // Atalhos para cada indicador -------------------------------------------

    public double weightKg() {
        return values.get(Metric.WEIGHT).value();
    }

    public OptionalDouble bmi() { return opt(Metric.BMI); }

    public OptionalDouble bodyFatPercent() { return opt(Metric.BODY_FAT_PERCENT); }

    public OptionalDouble fatMassKg() { return opt(Metric.FAT_MASS); }

    public OptionalDouble visceralFat() { return opt(Metric.VISCERAL_FAT); }

    public OptionalDouble muscleMassKg() { return opt(Metric.MUSCLE_MASS); }

    public OptionalDouble skeletalMuscleKg() { return opt(Metric.SKELETAL_MUSCLE); }

    public OptionalDouble boneMassKg() { return opt(Metric.BONE_MASS); }

    public OptionalDouble proteinPercent() { return opt(Metric.PROTEIN_PERCENT); }

    public OptionalDouble bodyWaterPercent() { return opt(Metric.BODY_WATER_PERCENT); }

    public OptionalDouble extracellularWaterKg() { return opt(Metric.EXTRACELLULAR_WATER); }

    public OptionalDouble bmrKcal() { return opt(Metric.BMR); }

    public OptionalDouble metabolicAge() { return opt(Metric.METABOLIC_AGE); }

    public OptionalDouble fatFreeMassKg() { return opt(Metric.FAT_FREE_MASS); }

    private OptionalDouble opt(Metric m) {
        return values.get(m).optional();
    }

    /** Tabela legível com todos os indicadores. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Composição corporal — ").append(user.name())
                .append(" (").append(measurement.source()).append(", ").append(measurement.timestamp()).append(")\n");
        for (MetricValue v : values.values()) sb.append("  ").append(v).append('\n');
        return sb.toString();
    }

    @Override
    public String toString() {
        return report();
    }

    /** Montado pelos {@link BodyCompositionCalculator}; o primeiro a definir um indicador vence. */
    public static final class Builder {
        private final ScaleMeasurement measurement;
        private final ScaleUser user;
        private final EnumMap<Metric, MetricValue> values = new EnumMap<>(Metric.class);

        public Builder(ScaleMeasurement measurement, ScaleUser user) {
            this.measurement = measurement;
            this.user = user;
        }

        public ScaleMeasurement measurement() {
            return measurement;
        }

        public ScaleUser user() {
            return user;
        }

        public boolean has(Metric m) {
            MetricValue v = values.get(m);
            return v != null && v.isAvailable();
        }

        public OptionalDouble value(Metric m) {
            MetricValue v = values.get(m);
            return v == null ? OptionalDouble.empty() : v.optional();
        }

        /** Define o valor se ainda não houver um (valores com prioridade maior vêm primeiro). */
        public Builder offer(Metric m, double value, MetricSource source, String note) {
            if (!has(m) && Double.isFinite(value)) values.put(m, MetricValue.of(m, value, source, note));
            return this;
        }

        public BodyComposition build() {
            EnumMap<Metric, MetricValue> out = new EnumMap<>(Metric.class);
            for (Metric m : Metric.values()) {
                MetricValue v = values.get(m);
                out.put(m, v != null ? v : MetricValue.unavailable(m, defaultReason(m)));
            }
            return new BodyComposition(measurement, user, out);
        }

        private static String defaultReason(Metric m) {
            return switch (m) {
                case EXTRACELLULAR_WATER ->
                        "exige bioimpedância multifrequência documentada; a balança não informa ECW";
                case SKELETAL_MUSCLE, BODY_FAT_PERCENT, FAT_MASS, FAT_FREE_MASS, BODY_WATER_PERCENT, BMR ->
                        "sem algoritmo de composição configurado";
                default -> "o algoritmo do fabricante não está disponível";
            };
        }
    }
}
