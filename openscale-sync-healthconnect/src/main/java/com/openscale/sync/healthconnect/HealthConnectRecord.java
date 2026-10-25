package com.openscale.sync.healthconnect;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Espelho, em Java puro, dos registros de composição corporal do Health Connect
 * ({@code androidx.health.connect.client.records.*}). Cada tipo corresponde a uma
 * classe do Jetpack com o mesmo nome sem o prefixo.
 *
 * <p>{@code clientRecordId} vai em {@code Metadata.clientRecordId}: o Health
 * Connect usa esse campo para não duplicar o registro se a sincronização repetir.
 */
public sealed interface HealthConnectRecord {

    Instant time();

    ZoneOffset zoneOffset();

    String clientRecordId();

    /** {@code WeightRecord(weight = Mass.kilograms(kg))}. */
    record Weight(Instant time, ZoneOffset zoneOffset, String clientRecordId, double kilograms)
            implements HealthConnectRecord {
    }

    /** {@code BodyFatRecord(percentage = Percentage(percent))}. */
    record BodyFat(Instant time, ZoneOffset zoneOffset, String clientRecordId, double percent)
            implements HealthConnectRecord {
    }

    /** {@code BoneMassRecord(mass = Mass.kilograms(kg))}. */
    record BoneMass(Instant time, ZoneOffset zoneOffset, String clientRecordId, double kilograms)
            implements HealthConnectRecord {
    }

    /** {@code LeanBodyMassRecord(mass = Mass.kilograms(kg))}. */
    record LeanBodyMass(Instant time, ZoneOffset zoneOffset, String clientRecordId, double kilograms)
            implements HealthConnectRecord {
    }

    /** {@code BodyWaterMassRecord(mass = Mass.kilograms(kg))}; o Health Connect guarda água em massa, não em %. */
    record BodyWaterMass(Instant time, ZoneOffset zoneOffset, String clientRecordId, double kilograms)
            implements HealthConnectRecord {
    }

    /** {@code BasalMetabolicRateRecord(basalMetabolicRate = Power.kilocaloriesPerDay(kcal))}. */
    record BasalMetabolicRate(Instant time, ZoneOffset zoneOffset, String clientRecordId, double kcalPerDay)
            implements HealthConnectRecord {
    }
}
