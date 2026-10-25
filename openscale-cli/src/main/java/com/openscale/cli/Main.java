package com.openscale.cli;

import com.openscale.core.ble.BleUuids;
import com.openscale.core.ble.SimulatedBleTransport;
import com.openscale.core.model.Gender;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.scale.ScaleHandlerProvider;
import com.openscale.core.scale.ScaleHandlerRegistry;
import com.openscale.core.scale.ScaleSession;
import com.openscale.core.sync.SyncConfig;
import com.openscale.core.sync.SyncDispatcher;
import com.openscale.core.sync.SyncResult;
import com.openscale.core.sync.SyncTargetFactory;
import com.openscale.core.util.Bytes;
import com.openscale.scales.relaxmedic.RelaxmedicSimulator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * <pre>
 * java -jar openscale-cli.jar list
 * java -jar openscale-cli.jar manual   --weight 78.4
 * java -jar openscale-cli.jar simulate --weight 78.4
 * java -jar openscale-cli.jar replay   --file captura.txt --name Relaxmedic
 *
 * Opção comum: --config openscale.properties (padrão: ./openscale.properties)
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            usage();
            return;
        }
        Map<String, String> opts = parseOptions(args);
        Properties props = loadConfig(Path.of(opts.getOrDefault("config", "openscale.properties")));

        int exit = switch (args[0]) {
            case "list" -> list();
            case "manual" -> manual(props, opts);
            case "simulate" -> simulate(props, opts);
            case "replay" -> replay(props, opts);
            default -> {
                System.err.println("Comando desconhecido: " + args[0]);
                usage();
                yield 2;
            }
        };
        System.exit(exit);
    }

    // ------------------------------------------------------------------ comandos

    private static int list() {
        System.out.println("Balanças registradas:");
        for (ScaleHandlerProvider p : ScaleHandlerRegistry.fromClasspath().all()) {
            System.out.println("  - " + p.displayName() + " " + p.capabilities());
        }
        System.out.println("Destinos de sincronização disponíveis: " + SyncTargetFactory.available());
        return 0;
    }

    private static int manual(Properties props, Map<String, String> opts) {
        ScaleUser user = userFrom(props);
        ScaleMeasurement m = ScaleMeasurement.builder(requireWeight(opts)).source("manual").build().withBmi(user);
        try (SyncDispatcher d = dispatcher(props)) {
            printMeasurement(m);
            printResults(d.dispatch(m, user));
        }
        return 0;
    }

    private static int simulate(Properties props, Map<String, String> opts) {
        ScaleUser user = userFrom(props);
        double weight = Double.parseDouble(opts.getOrDefault("weight", "78.45"));
        SimulatedBleTransport transport = new SimulatedBleTransport("Relaxmedic-SIM");
        new RelaxmedicSimulator(transport, weight, RelaxmedicSimulator.defaultImpedances()).attach();
        return runSession(props, user, transport);
    }

    /**
     * Reproduz uma captura real. Formato: uma notificação por linha,
     * {@code <uuid curto ou completo> <bytes em hex>}; linhas com # são ignoradas.
     * <pre>
     * FFB2 00 07 00 a2 01 00 01 32 a8 00 00 00 00 00 00 00 00 00 0d
     * FFB3 ...
     * </pre>
     */
    private static int replay(Properties props, Map<String, String> opts) throws IOException {
        ScaleUser user = userFrom(props);
        String file = opts.get("file");
        if (file == null) throw new IllegalArgumentException("--file é obrigatório");
        String name = opts.getOrDefault("name", "Relaxmedic");

        SimulatedBleTransport transport = new SimulatedBleTransport(name);
        List<String> lines = Files.readAllLines(Path.of(file));
        transport.onWrite(w -> System.out.println("  → " + shortUuid(w.characteristic()) + " " + Bytes.hex(w.value())));

        int code = runSession(props, user, transport, () -> {
            for (String line : lines) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+", 2);
                transport.inject(parseUuid(parts[0]), Bytes.fromHex(parts[1]));
            }
        });
        return code;
    }

    // ------------------------------------------------------------------ sessão

    private static int runSession(Properties props, ScaleUser user, SimulatedBleTransport transport) {
        return runSession(props, user, transport, () -> { });
    }

    private static int runSession(Properties props, ScaleUser user, SimulatedBleTransport transport, Runnable afterConnect) {
        ScaleHandlerProvider provider = ScaleHandlerRegistry.fromClasspath().find(transport.deviceName())
                .orElseThrow(() -> new IllegalArgumentException("Nenhum handler para " + transport.deviceName()));
        boolean verbose = Boolean.parseBoolean(props.getProperty("cli.verbose", "false"));
        int[] count = {0};

        try (SyncDispatcher d = dispatcher(props)) {
            ScaleSession session = new ScaleSession(transport, provider, user, new ScaleSession.Listener() {
                @Override
                public void onInfo(String message) {
                    System.out.println("ℹ " + message);
                }

                @Override
                public void onLiveWeight(double kg) {
                    System.out.printf(Locale.ROOT, "  … %.2f kg%n", kg);
                }

                @Override
                public void onMeasurement(ScaleMeasurement m) {
                    count[0]++;
                    printMeasurement(m);
                }

                @Override
                public void onSynced(ScaleMeasurement m, List<SyncResult> results) {
                    printResults(results);
                }

                @Override
                public void onError(String message, Throwable cause) {
                    System.err.println("✗ " + message + (cause != null ? ": " + cause.getMessage() : ""));
                }

                @Override
                public void onDebug(String message) {
                    if (verbose) System.out.println("  [debug] " + message);
                }
            }).withSync(d);
            session.start();
            afterConnect.run();
            session.close();
        }
        if (count[0] == 0) {
            System.err.println("Nenhuma medição concluída.");
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ util

    private static SyncDispatcher dispatcher(Properties props) {
        SyncDispatcher d = new SyncDispatcher().addAll(SyncTargetFactory.createEnabled(new SyncConfig(props)));
        if (d.targets().isEmpty()) {
            System.out.println("ℹ Nenhum destino de sincronização habilitado em openscale.properties.");
        }
        return d;
    }

    static ScaleUser userFrom(Properties p) {
        return new ScaleUser(
                p.getProperty("user.id", "default"),
                p.getProperty("user.name", "Usuário"),
                Integer.parseInt(p.getProperty("user.heightCm", "170")),
                Integer.parseInt(p.getProperty("user.age", "35")),
                Gender.parse(p.getProperty("user.gender", "M")),
                Double.parseDouble(p.getProperty("user.weightKg", "70")));
    }

    private static void printMeasurement(ScaleMeasurement m) {
        System.out.println("✔ Medição (" + m.source() + ", " + m.timestamp() + ")");
        m.values().forEach((t, v) -> System.out.printf(Locale.ROOT, "    %-14s %8.2f %s%n", t.key(), v, t.unit()));
    }

    private static void printResults(List<SyncResult> results) {
        for (SyncResult r : results) {
            System.out.println((r.success() ? "  ✔ " : "  ✗ ") + r.targetId()
                    + (r.success() ? "" : ": " + r.message()));
        }
    }

    private static double requireWeight(Map<String, String> opts) {
        String w = opts.get("weight");
        if (w == null) throw new IllegalArgumentException("--weight é obrigatório");
        return Double.parseDouble(w.replace(',', '.'));
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                out.put(args[i].substring(2), args[++i]);
            }
        }
        return out;
    }

    static Properties loadConfig(Path path) throws IOException {
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return p;
    }

    private static UUID parseUuid(String s) {
        return s.length() <= 4 ? BleUuids.uuid16(Integer.parseInt(s, 16)) : UUID.fromString(s);
    }

    private static String shortUuid(UUID u) {
        return u.toString().substring(4, 8).toUpperCase(Locale.ROOT);
    }

    private static void usage() {
        System.out.println("""
                openScale Java

                Uso: java -jar openscale-cli.jar <comando> [opções]

                Comandos:
                  list                         balanças e destinos de sync disponíveis
                  manual   --weight 78.4       registra um peso digitado e sincroniza
                  simulate [--weight 78.4]     simula a Relaxmedic de ponta a ponta
                  replay   --file captura.txt  reproduz notificações capturadas (nRF Connect)
                           [--name Relaxmedic]

                Opções:
                  --config openscale.properties
                """);
    }
}
