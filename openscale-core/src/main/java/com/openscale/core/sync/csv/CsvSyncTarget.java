package com.openscale.core.sync.csv;

import com.openscale.core.model.MeasurementType;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.SyncException;
import com.openscale.core.sync.SyncTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Acrescenta cada medição a um arquivo CSV (um por usuário), no estilo da
 * exportação do openScale: dateTime, weight, fat, water, muscle, lbm, bone...
 */
public final class CsvSyncTarget implements SyncTarget {

    static final List<MeasurementType> COLUMNS = List.of(
            MeasurementType.WEIGHT, MeasurementType.BMI, MeasurementType.BODY_FAT, MeasurementType.WATER,
            MeasurementType.MUSCLE, MeasurementType.LBM, MeasurementType.BONE, MeasurementType.VISCERAL_FAT,
            MeasurementType.BMR, MeasurementType.IMPEDANCE);

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final Path directory;
    private final ZoneId zone;

    public CsvSyncTarget(Path directory, ZoneId zone) {
        this.directory = directory;
        this.zone = zone;
    }

    @Override
    public String id() {
        return "csv";
    }

    public Path fileFor(ScaleUser user) {
        return directory.resolve("openscale_" + user.id() + ".csv");
    }

    @Override
    public synchronized void sync(ScaleMeasurement m, ScaleUser user) throws SyncException {
        Path file = fileFor(user);
        try {
            Files.createDirectories(directory);
            StringBuilder sb = new StringBuilder();
            if (!Files.exists(file) || Files.size(file) == 0) {
                sb.append("\"dateTime\"");
                for (MeasurementType t : COLUMNS) sb.append(",\"").append(t.key()).append('"');
                sb.append(",\"comment\"\n");
            }
            sb.append('"').append(DATE_TIME.format(m.timestamp().atZone(zone))).append('"');
            for (MeasurementType t : COLUMNS) {
                OptionalDouble v = m.get(t);
                sb.append(',');
                if (v.isPresent()) sb.append(String.format(Locale.ROOT, "%.2f", v.getAsDouble()));
            }
            sb.append(",\"").append(m.source().replace("\"", "'")).append("\"\n");
            Files.writeString(file, sb, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new SyncException("Falha ao gravar " + file, e);
        }
    }
}
