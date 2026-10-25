package com.openscale.core.sync.csv;

import com.openscale.core.sync.SyncConfig;
import com.openscale.core.sync.SyncTarget;
import com.openscale.core.sync.SyncTargetFactory;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Optional;

/**
 * <pre>
 * sync.csv.enabled=true
 * sync.csv.directory=./dados
 * sync.csv.zone=America/Fortaleza
 * </pre>
 */
public final class CsvSyncTargetFactory implements SyncTargetFactory {

    @Override
    public String id() {
        return "csv";
    }

    @Override
    public Optional<SyncTarget> create(SyncConfig config) {
        Path dir = Path.of(config.get(id(), "directory", "."));
        ZoneId zone = ZoneId.of(config.get(id(), "zone", ZoneId.systemDefault().getId()));
        return Optional.of(new CsvSyncTarget(dir, zone));
    }
}
