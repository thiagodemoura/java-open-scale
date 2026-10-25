package com.openscale.core.composition;

import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;

/**
 * Copia o que o handler da balança já publicou (peso e, se houver, os valores
 * calculados pelo algoritmo do fabricante).
 */
public final class ScaleValuesCalculator implements BodyCompositionCalculator {

    @Override
    public void contribute(BodyComposition.Builder b) {
        ScaleMeasurement m = b.measurement();
        String src = "informado por " + m.source();
        b.offer(Metric.WEIGHT, m.weightKg(), MetricSource.SCALE, src);
        m.get(MeasurementType.BMI).ifPresent(v -> b.offer(Metric.BMI, v, MetricSource.CALCULATED, "peso / altura²"));
        m.get(MeasurementType.BODY_FAT).ifPresent(v -> b.offer(Metric.BODY_FAT_PERCENT, v, MetricSource.SCALE, src));
        m.get(MeasurementType.VISCERAL_FAT).ifPresent(v -> b.offer(Metric.VISCERAL_FAT, v, MetricSource.SCALE, src));
        m.get(MeasurementType.BONE).ifPresent(v -> b.offer(Metric.BONE_MASS, v, MetricSource.SCALE, src));
        m.get(MeasurementType.PROTEIN).ifPresent(v -> b.offer(Metric.PROTEIN_PERCENT, v, MetricSource.SCALE, src));
        m.get(MeasurementType.WATER).ifPresent(v -> b.offer(Metric.BODY_WATER_PERCENT, v, MetricSource.SCALE, src));
        m.get(MeasurementType.BMR).ifPresent(v -> b.offer(Metric.BMR, v, MetricSource.SCALE, src));
        m.get(MeasurementType.METABOLIC_AGE).ifPresent(v -> b.offer(Metric.METABOLIC_AGE, v, MetricSource.SCALE, src));
        m.get(MeasurementType.LBM).ifPresent(v -> b.offer(Metric.FAT_FREE_MASS, v, MetricSource.SCALE, src));
        // MUSCLE vem em % no modelo do openScale; convertido para kg
        m.get(MeasurementType.MUSCLE).ifPresent(pct ->
                b.offer(Metric.MUSCLE_MASS, m.weightKg() * pct / 100.0, MetricSource.SCALE, src + " (em %, convertido)"));
    }
}
