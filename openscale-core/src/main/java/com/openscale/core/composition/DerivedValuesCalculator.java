package com.openscale.core.composition;

/**
 * Contas exatas sobre valores já conhecidos: IMC, massa gorda e FFM a partir
 * da gordura %, e vice-versa. Deve rodar depois dos outros calculadores
 * (o {@link BodyCompositionScale} faz isso automaticamente).
 */
public final class DerivedValuesCalculator implements BodyCompositionCalculator {

    @Override
    public void contribute(BodyComposition.Builder b) {
        double w = b.value(Metric.WEIGHT).orElse(Double.NaN);
        if (Double.isNaN(w) || w <= 0) return;

        double h = b.user().heightCm() / 100.0;
        b.offer(Metric.BMI, w / (h * h), MetricSource.CALCULATED, "peso / altura²");

        if (b.has(Metric.BODY_FAT_PERCENT)) {
            double fatPct = b.value(Metric.BODY_FAT_PERCENT).getAsDouble();
            double fatKg = w * fatPct / 100.0;
            b.offer(Metric.FAT_MASS, fatKg, MetricSource.CALCULATED, "peso × gordura%");
            b.offer(Metric.FAT_FREE_MASS, w - fatKg, MetricSource.CALCULATED, "peso − massa gorda");
        } else if (b.has(Metric.FAT_FREE_MASS)) {
            double ffm = b.value(Metric.FAT_FREE_MASS).getAsDouble();
            b.offer(Metric.FAT_MASS, w - ffm, MetricSource.CALCULATED, "peso − FFM");
            b.offer(Metric.BODY_FAT_PERCENT, (w - ffm) / w * 100.0, MetricSource.CALCULATED, "(peso − FFM) / peso");
        }
    }
}
