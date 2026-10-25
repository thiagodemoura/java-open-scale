package com.openscale.core.sync;

import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Envia uma medição para vários alvos. A falha de um alvo não impede os outros.
 */
public final class SyncDispatcher implements AutoCloseable {

    private final List<SyncTarget> targets = new ArrayList<>();

    public SyncDispatcher add(SyncTarget target) {
        targets.add(target);
        return this;
    }

    public SyncDispatcher addAll(List<SyncTarget> list) {
        targets.addAll(list);
        return this;
    }

    public List<SyncTarget> targets() {
        return Collections.unmodifiableList(targets);
    }

    public List<SyncResult> dispatch(ScaleMeasurement measurement, ScaleUser user) {
        List<SyncResult> results = new ArrayList<>(targets.size());
        for (SyncTarget t : targets) {
            try {
                t.sync(measurement, user);
                results.add(SyncResult.ok(t.id()));
            } catch (SyncException | RuntimeException e) {
                results.add(SyncResult.failed(t.id(), e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public void close() {
        for (SyncTarget t : targets) {
            try {
                t.close();
            } catch (Exception ignored) {
                // fechar o máximo possível
            }
        }
    }
}
