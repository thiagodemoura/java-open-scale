package com.openscale.sync.wger;

import com.openscale.core.model.Gender;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.SyncException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class WgerSyncTargetTest {

    record Call(String method, String uri, String auth, String body) {
    }

    record Reply(int status, String body) {
    }

    private HttpServer server;
    private final List<Call> calls = new ArrayList<>();
    private Function<Call, Reply> responder;
    private final ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);
    private final ScaleMeasurement m = ScaleMeasurement.builder(78.456)
            .timestamp(Instant.parse("2026-10-25T23:30:00Z")).build();
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Call c = new Call(ex.getRequestMethod(), ex.getRequestURI().toString(),
                    ex.getRequestHeaders().getFirst("Authorization"), body);
            calls.add(c);
            Reply r = responder.apply(c);
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(r.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private WgerSyncTarget target(WgerSyncTarget.DateMode mode) {
        return new WgerSyncTarget("http://127.0.0.1:" + server.getAddress().getPort() + "/", "abc", "Token",
                FORTALEZA, mode);
    }

    @Test
    void autoModeUsesDateTimeWhenAccepted() throws SyncException {
        responder = c -> new Reply(201, "{\"id\":1}");
        WgerSyncTarget t = target(WgerSyncTarget.DateMode.AUTO);
        t.sync(m, user);

        assertEquals(1, calls.size());
        assertEquals("POST", calls.get(0).method());
        assertEquals("Token abc", calls.get(0).auth());
        assertTrue(calls.get(0).body().contains("\"date\":\"2026-10-25T20:30:00-03:00\""), calls.get(0).body());
        assertTrue(calls.get(0).body().contains("\"weight\":\"78.46\""));
        assertEquals(WgerSyncTarget.DateMode.DATETIME, t.effectiveMode());
    }

    @Test
    void autoModeFallsBackToDateAndPatchesExistingEntry() throws SyncException {
        responder = c -> {
            if (c.method().equals("POST")) return new Reply(400, "{\"date\":[\"formato inválido\"]}");
            if (c.method().equals("GET")) return new Reply(200, "{\"count\":1,\"results\":[{\"id\":42}]}");
            return new Reply(200, "{\"id\":42}");
        };
        WgerSyncTarget t = target(WgerSyncTarget.DateMode.AUTO);
        t.sync(m, user);

        assertEquals(List.of("POST", "GET", "PATCH"), calls.stream().map(Call::method).toList());
        assertEquals("/api/v2/weightentry/?date=2026-10-25", calls.get(1).uri());
        assertEquals("/api/v2/weightentry/42/", calls.get(2).uri());
        assertEquals(WgerSyncTarget.DateMode.DATE, t.effectiveMode());
    }

    @Test
    void dateModeCreatesEntryWhenDayIsEmpty() throws SyncException {
        responder = c -> c.method().equals("GET")
                ? new Reply(200, "{\"count\":0,\"results\":[]}")
                : new Reply(201, "{\"id\":7}");
        target(WgerSyncTarget.DateMode.DATE).sync(m, user);

        assertEquals(List.of("GET", "POST"), calls.stream().map(Call::method).toList());
        assertTrue(calls.get(1).body().contains("\"date\":\"2026-10-25\""));
    }

    @Test
    void authErrorIsReported() {
        responder = c -> new Reply(401, "{\"detail\":\"Invalid token.\"}");
        SyncException e = assertThrows(SyncException.class, () -> target(WgerSyncTarget.DateMode.AUTO).sync(m, user));
        assertTrue(e.getMessage().contains("401"));
    }
}
