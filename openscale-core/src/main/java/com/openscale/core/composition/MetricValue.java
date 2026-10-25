package com.openscale.core.composition;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Valor de um indicador, com a origem e uma observação (fórmula usada ou motivo
 * da indisponibilidade).
 */
public record MetricValue(Metric metric, Double value, MetricSource source, String note) {

    public static MetricValue of(Metric m, double value, MetricSource source, String note) {
        return new MetricValue(m, round(value), source, note);
    }

    public static MetricValue unavailable(Metric m, String reason) {
        return new MetricValue(m, null, MetricSource.UNAVAILABLE, reason);
    }

    public boolean isAvailable() {
        return value != null;
    }

    public OptionalDouble optional() {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        String v = value == null ? "—" : String.format(Locale.ROOT, "%.2f %s", value, metric.unit());
        return String.format(Locale.ROOT, "%-30s %-16s %-11s %s", metric.label(), v, source, note == null ? "" : note);
    }
}
