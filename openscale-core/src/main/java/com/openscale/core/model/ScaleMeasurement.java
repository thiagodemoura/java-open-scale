package com.openscale.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Uma medição completa vinda de uma balança (ou digitada manualmente). */
public final class ScaleMeasurement {

    private final Instant timestamp;
    private final String source;
    private final EnumMap<MeasurementType, Double> values;
    private final double[] impedancesOhm;

    private ScaleMeasurement(Builder b) {
        this.timestamp = b.timestamp;
        this.source = b.source;
        this.values = new EnumMap<>(b.values);
        this.impedancesOhm = b.impedancesOhm.clone();
        if (!values.containsKey(MeasurementType.WEIGHT)) {
            throw new IllegalStateException("Toda medição precisa de peso");
        }
    }

    public static Builder builder(double weightKg) {
        return new Builder().set(MeasurementType.WEIGHT, weightKg);
    }

    public Instant timestamp() {
        return timestamp;
    }

    /** Nome do dispositivo de origem, por exemplo "Relaxmedic" ou "manual". */
    public String source() {
        return source;
    }

    public double weightKg() {
        return values.get(MeasurementType.WEIGHT);
    }

    public OptionalDouble get(MeasurementType type) {
        Double v = values.get(type);
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
    }

    public Map<MeasurementType, Double> values() {
        return Collections.unmodifiableMap(values);
    }

    /** Impedâncias brutas por eletrodo/frequência, quando a balança envia mais de uma. */
    public double[] impedancesOhm() {
        return impedancesOhm.clone();
    }

    /** Devolve uma cópia com o IMC calculado, se ainda não houver. */
    public ScaleMeasurement withBmi(ScaleUser user) {
        if (values.containsKey(MeasurementType.BMI) || user == null) return this;
        double h = user.heightCm() / 100.0;
        double bmi = Math.round(weightKg() / (h * h) * 10.0) / 10.0;
        return toBuilder().set(MeasurementType.BMI, bmi).build();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.timestamp = timestamp;
        b.source = source;
        b.values.putAll(values);
        b.impedancesOhm = impedancesOhm.clone();
        return b;
    }

    @Override
    public String toString() {
        return "ScaleMeasurement{" + timestamp + ", " + source + ", " + values + "}";
    }

    public static final class Builder {
        private Instant timestamp = Instant.now();
        private String source = "manual";
        private final EnumMap<MeasurementType, Double> values = new EnumMap<>(MeasurementType.class);
        private double[] impedancesOhm = new double[0];

        public Builder timestamp(Instant t) {
            this.timestamp = Objects.requireNonNull(t);
            return this;
        }

        public Builder source(String s) {
            this.source = Optional.ofNullable(s).orElse("manual");
            return this;
        }

        public Builder set(MeasurementType type, double value) {
            if (Double.isFinite(value)) values.put(type, value);
            return this;
        }

        public Builder impedances(double[] ohms) {
            this.impedancesOhm = ohms == null ? new double[0] : ohms.clone();
            if (impedancesOhm.length > 0) values.put(MeasurementType.IMPEDANCE, impedancesOhm[0]);
            return this;
        }

        public ScaleMeasurement build() {
            return new ScaleMeasurement(this);
        }
    }
}
