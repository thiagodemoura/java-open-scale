package com.openscale.sync.healthconnect;

import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.SyncException;
import com.openscale.core.sync.SyncTarget;

import java.time.ZoneId;
import java.util.Objects;

public final class HealthConnectSyncTarget implements SyncTarget {

    private final HealthConnectWriter writer;
    private final ZoneId zone;

    public HealthConnectSyncTarget(HealthConnectWriter writer, ZoneId zone) {
        this.writer = Objects.requireNonNull(writer);
        this.zone = Objects.requireNonNull(zone);
    }

    @Override
    public String id() {
        return "healthconnect";
    }

    @Override
    public void sync(ScaleMeasurement m, ScaleUser user) throws SyncException {
        try {
            writer.insert(HealthConnectMapper.map(m, user, zone));
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Health Connect: " + e.getMessage(), e);
        }
    }
}
