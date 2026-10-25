package com.openscale.core.composition;

import com.openscale.core.model.ScaleUser;

import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Estimativas por equações de bioimpedância publicadas, para quando não há o
 * algoritmo do fabricante. Os resultados ficam marcados como
 * {@link MetricSource#ESTIMATED}.
 *
 * <p><b>Limitações:</b> as equações foram validadas em adultos (18+) com
 * resistência mão-pé a 50 kHz. Balanças de pé medem outro caminho elétrico, e
 * não se sabe ao certo qual das impedâncias da Relaxmedic corresponde à
 * resistência de corpo inteiro. Por isso o {@link ImpedanceSelector} é
 * <b>obrigatório</b>: escolha o índice comparando com o app do fabricante. Use
 * os valores para ver tendências, não como valor absoluto.
 *
 * <ul>
 *   <li>Água corporal total e FFM: Sun SS et al., Am J Clin Nutr 2003;77:331–40.</li>
 *   <li>Músculo esquelético: Janssen I et al., J Appl Physiol 2000;89:465–71.</li>
 *   <li>TMB: Katch-McArdle, 370 + 21,6 × FFM.</li>
 * </ul>
 * Gordura visceral, massa óssea, proteína, idade corporal e ECW não têm equação
 * validada para esses dados e ficam indisponíveis.
 */
public final class GenericBiaEstimator implements BodyCompositionCalculator {

    /** Escolhe a resistência de corpo inteiro (Ω) entre as impedâncias recebidas. */
    @FunctionalInterface
    public interface ImpedanceSelector extends Function<double[], OptionalDouble> {

        static ImpedanceSelector index(int i) {
            return imps -> i >= 0 && i < imps.length ? OptionalDouble.of(imps[i]) : OptionalDouble.empty();
        }
    }

    /** Faixa plausível de resistência de corpo inteiro; fora dela a estimativa é descartada. */
    static final double MIN_OHM = 150;
    static final double MAX_OHM = 1200;

    private final ImpedanceSelector selector;

    public GenericBiaEstimator(ImpedanceSelector selector) {
        this.selector = java.util.Objects.requireNonNull(selector);
    }

    @Override
    public void contribute(BodyComposition.Builder b) {
        ScaleUser u = b.user();
        if (u.age() < 18) return; // equações só para adultos
        OptionalDouble r = selector.apply(b.measurement().impedancesOhm());
        if (r.isEmpty() || r.getAsDouble() < MIN_OHM || r.getAsDouble() > MAX_OHM) return;

        double R = r.getAsDouble();
        double w = b.measurement().weightKg();
        double h = u.heightCm();
        double ri = h * h / R; // índice de resistência H²/R
        boolean male = u.gender().isMale();

        double tbw = male ? 1.20 + 0.45 * ri + 0.18 * w : 3.75 + 0.45 * ri + 0.11 * w;
        double ffm = male ? -10.68 + 0.65 * ri + 0.26 * w + 0.02 * R : -9.53 + 0.69 * ri + 0.17 * w + 0.02 * R;
        if (ffm <= 0 || ffm >= w) return;
        double smm = ri * 0.401 + (male ? 3.825 : 0) - 0.071 * u.age() + 5.102;

        b.offer(Metric.BODY_WATER_PERCENT, tbw / w * 100.0, MetricSource.ESTIMATED, "Sun 2003 (água total)");
        b.offer(Metric.SKELETAL_MUSCLE, smm, MetricSource.ESTIMATED, "Janssen 2000");

        // Se a balança já informou gordura ou FFM, não cria um valor concorrente:
        // o DerivedValuesCalculator completa a partir do valor da balança.
        boolean fatKnown = b.has(Metric.BODY_FAT_PERCENT) || b.has(Metric.FAT_FREE_MASS);
        if (!fatKnown) {
            b.offer(Metric.FAT_FREE_MASS, ffm, MetricSource.ESTIMATED, "Sun 2003");
            b.offer(Metric.BODY_FAT_PERCENT, (w - ffm) / w * 100.0, MetricSource.ESTIMATED, "(peso − FFM Sun 2003) / peso");
            b.offer(Metric.FAT_MASS, w - ffm, MetricSource.ESTIMATED, "peso − FFM Sun 2003");
        }

        double knownFfm = b.has(Metric.FAT_FREE_MASS)
                ? b.value(Metric.FAT_FREE_MASS).getAsDouble()
                : w * (1 - b.value(Metric.BODY_FAT_PERCENT).orElse((w - ffm) / w * 100.0) / 100.0);
        b.offer(Metric.BMR, 370 + 21.6 * knownFfm, MetricSource.ESTIMATED, "Katch-McArdle");
    }
}
