package com.openscale.scales.relaxmedic;

import com.openscale.core.ble.SimulatedBleTransport;
import com.openscale.core.composition.BodyComposition;
import com.openscale.core.composition.BodyCompositionScale;
import com.openscale.core.composition.GenericBiaEstimator;
import com.openscale.core.composition.Metric;
import com.openscale.core.composition.MetricSource;
import com.openscale.core.model.Gender;
import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BodyCompositionScaleTest {

    private final ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);

    private SimulatedBleTransport simulatedRelaxmedic(double kg) {
        SimulatedBleTransport t = new SimulatedBleTransport("Relaxmedic-SIM");
        new RelaxmedicSimulator(t, kg, RelaxmedicSimulator.defaultImpedances()).attach();
        return t;
    }

    @Test
    void selectsScaleConnectsAndReturnsEveryMetric() throws Exception {
        List<Double> live = new ArrayList<>();
        BodyCompositionScale scale = BodyCompositionScale.builder(user)
                .scale("Relaxmedic")
                .onLiveWeight(live::add)
                .build();

        BodyComposition bc = scale.measure(simulatedRelaxmedic(78.45), Duration.ofSeconds(5));

        assertEquals(Metric.values().length, bc.all().size(), "todos os indicadores devem estar no resultado");
        assertEquals(78.45, bc.weightKg(), 1e-9);
        assertEquals(MetricSource.SCALE, bc.get(Metric.WEIGHT).source());
        assertEquals(25.6, bc.bmi().getAsDouble(), 1e-9);
        assertFalse(live.isEmpty());

        // sem algoritmo configurado: indisponível, com motivo
        assertTrue(bc.bodyFatPercent().isEmpty());
        assertEquals(MetricSource.UNAVAILABLE, bc.get(Metric.EXTRACELLULAR_WATER).source());
        assertNotNull(bc.get(Metric.VISCERAL_FAT).note());
    }

    @Test
    void genericEstimatorFillsWhatPublishedEquationsAllow() throws Exception {
        BodyCompositionScale scale = BodyCompositionScale.builder(user)
                .scale("Relaxmedic")
                .calculator(new GenericBiaEstimator(GenericBiaEstimator.ImpedanceSelector.index(1)))
                .build();

        BodyComposition bc = scale.measure(simulatedRelaxmedic(78.45), Duration.ofSeconds(5));

        double fat = bc.bodyFatPercent().getAsDouble();
        double ffm = bc.fatFreeMassKg().getAsDouble();
        assertTrue(fat > 5 && fat < 50, "gordura plausível: " + fat);
        assertEquals(78.45, ffm + bc.fatMassKg().getAsDouble(), 0.02);
        assertTrue(bc.bodyWaterPercent().getAsDouble() > 40 && bc.bodyWaterPercent().getAsDouble() < 70);
        assertTrue(bc.skeletalMuscleKg().getAsDouble() > 15 && bc.skeletalMuscleKg().getAsDouble() < 50);
        assertEquals(370 + 21.6 * ffm, bc.bmrKcal().getAsDouble(), 0.5);
        assertEquals(MetricSource.ESTIMATED, bc.get(Metric.BODY_FAT_PERCENT).source());

        // sem equação validada para esses dados
        assertTrue(bc.visceralFat().isEmpty());
        assertTrue(bc.boneMassKg().isEmpty());
        assertTrue(bc.proteinPercent().isEmpty());
        assertTrue(bc.metabolicAge().isEmpty());
        assertTrue(bc.extracellularWaterKg().isEmpty());
        System.out.println(bc.report());
    }

    @Test
    void scaleProvidedValuesWinOverEstimates() {
        BodyCompositionScale scale = BodyCompositionScale.builder(user)
                .scale("Relaxmedic")
                .calculator(new GenericBiaEstimator(GenericBiaEstimator.ImpedanceSelector.index(1)))
                .build();
        ScaleMeasurement m = ScaleMeasurement.builder(80)
                .source("Relaxmedic")
                .set(MeasurementType.BODY_FAT, 20.0)
                .set(MeasurementType.MUSCLE, 40.0)
                .set(MeasurementType.VISCERAL_FAT, 9)
                .impedances(RelaxmedicSimulator.defaultImpedances())
                .build();

        BodyComposition bc = scale.compute(m);

        assertEquals(20.0, bc.bodyFatPercent().getAsDouble(), 1e-9);
        assertEquals(MetricSource.SCALE, bc.get(Metric.BODY_FAT_PERCENT).source());
        assertEquals(16.0, bc.fatMassKg().getAsDouble(), 1e-9);
        assertEquals(64.0, bc.fatFreeMassKg().getAsDouble(), 1e-9);
        assertEquals(MetricSource.CALCULATED, bc.get(Metric.FAT_FREE_MASS).source());
        assertEquals(32.0, bc.muscleMassKg().getAsDouble(), 1e-9);
        assertEquals(9.0, bc.visceralFat().getAsDouble(), 1e-9);
        assertEquals(370 + 21.6 * 64.0, bc.bmrKcal().getAsDouble(), 0.01);
    }

    @Test
    void unknownScaleNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BodyCompositionScale.builder(user).scale("Mi Scale"));
        assertTrue(BodyCompositionScale.availableScales().contains("Relaxmedic"));
    }
}
