package com.openscale.sync.healthconnect;

import java.io.PrintStream;
import java.util.List;

/** Writer de demonstração: só imprime o que seria gravado no Health Connect. */
public final class LoggingHealthConnectWriter implements HealthConnectWriter {

    private final PrintStream out;

    public LoggingHealthConnectWriter() {
        this(System.out);
    }

    public LoggingHealthConnectWriter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void insert(List<HealthConnectRecord> records) {
        for (HealthConnectRecord r : records) out.println("[health-connect] " + r);
    }
}
