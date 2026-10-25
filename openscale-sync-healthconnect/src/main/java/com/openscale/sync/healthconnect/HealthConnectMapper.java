package com.openscale.sync.healthconnect;

import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Converte uma medição nos registros correspondentes do Health Connect. */
public final class HealthConnectMapper {

    private HealthConnectMapper() {
    }

    public static List<HealthConnectRecord> map(ScaleMeasurement m, ScaleUser user, ZoneId zone) {
        Instant t = m.timestamp();
        ZoneOffset off = zone.getRules().getOffset(t);
        String base = "openscale-" + user.id() + "-" + t.toEpochMilli() + "-";
        double weight = m.weightKg();

        List<HealthConnectRecord> out = new ArrayList<>();
        out.add(new HealthConnectRecord.Weight(t, off, base + "weight", weight));
        m.get(MeasurementType.BODY_FAT).ifPresent(v ->
                out.add(new HealthConnectRecord.BodyFat(t, off, base + "fat", v)));
        m.get(MeasurementType.BONE).ifPresent(v ->
                out.add(new HealthConnectRecord.BoneMass(t, off, base + "bone", v)));
        m.get(MeasurementType.LBM).ifPresent(v ->
                out.add(new HealthConnectRecord.LeanBodyMass(t, off, base + "lbm", v)));
        m.get(MeasurementType.WATER).ifPresent(pct ->
                out.add(new HealthConnectRecord.BodyWaterMass(t, off, base + "water",
                        Math.round(weight * pct / 100.0 * 100.0) / 100.0)));
        m.get(MeasurementType.BMR).ifPresent(v ->
                out.add(new HealthConnectRecord.BasalMetabolicRate(t, off, base + "bmr", v)));
        return out;
    }
}
