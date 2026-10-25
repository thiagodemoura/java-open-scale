package com.openscale.sync.wger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.SyncException;
import com.openscale.core.sync.SyncTarget;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Grava o peso em {@code /api/v2/weightentry/} do wger.
 *
 * <p>Versões antigas do wger guardam só a data (uma entrada por dia); versões
 * novas aceitam data e hora. No modo {@link DateMode#AUTO} a primeira tentativa
 * usa data e hora e, se o servidor recusar com 400, passa a usar só a data,
 * atualizando a entrada do dia quando ela já existir.
 */
public final class WgerSyncTarget implements SyncTarget {

    public enum DateMode { AUTO, DATETIME, DATE }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String authorization;
    private final ZoneId zone;
    private DateMode mode;

    /**
     * @param baseUrl    ex.: {@code https://wger.de}
     * @param token      chave da API (Configurações → API key) ou token JWT
     * @param authScheme {@code Token} para a chave permanente, {@code Bearer} para JWT
     */
    public WgerSyncTarget(String baseUrl, String token, String authScheme, ZoneId zone, DateMode mode) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.authorization = authScheme + " " + token;
        this.zone = zone;
        this.mode = mode;
    }

    @Override
    public String id() {
        return "wger";
    }

    public DateMode effectiveMode() {
        return mode;
    }

    @Override
    public synchronized void sync(ScaleMeasurement m, ScaleUser user) throws SyncException {
        String weight = String.format(Locale.ROOT, "%.2f", m.weightKg());
        try {
            if (mode != DateMode.DATE) {
                String dateTime = m.timestamp().atZone(zone).truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                HttpResponse<String> r = send("POST", "/api/v2/weightentry/", body(dateTime, weight));
                if (ok(r)) {
                    mode = DateMode.DATETIME;
                    return;
                }
                if (r.statusCode() != 400 || mode == DateMode.DATETIME) throw fail("POST", r);
                mode = DateMode.DATE; // servidor só aceita data: tenta de novo
            }
            upsertByDate(m.timestamp().atZone(zone).toLocalDate().toString(), weight);
        } catch (IOException e) {
            throw new SyncException("wger: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncException("wger: interrompido", e);
        }
    }

    private void upsertByDate(String date, String weight) throws IOException, InterruptedException, SyncException {
        HttpResponse<String> get = send("GET",
                "/api/v2/weightentry/?date=" + URLEncoder.encode(date, StandardCharsets.UTF_8), null);
        if (!ok(get)) throw fail("GET", get);

        JsonNode results = JSON.readTree(get.body()).path("results");
        if (results.isArray() && !results.isEmpty()) {
            long id = results.get(0).path("id").asLong();
            ObjectNode patch = JSON.createObjectNode().put("weight", weight);
            HttpResponse<String> r = send("PATCH", "/api/v2/weightentry/" + id + "/", JSON.writeValueAsString(patch));
            if (!ok(r)) throw fail("PATCH", r);
        } else {
            HttpResponse<String> r = send("POST", "/api/v2/weightentry/", body(date, weight));
            if (!ok(r)) throw fail("POST", r);
        }
    }

    private String body(String date, String weight) throws IOException {
        return JSON.writeValueAsString(JSON.createObjectNode().put("date", date).put("weight", weight));
    }

    private HttpResponse<String> send(String method, String path, String json)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authorization)
                .header("Accept", "application/json");
        if (json == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static boolean ok(HttpResponse<?> r) {
        return r.statusCode() >= 200 && r.statusCode() < 300;
    }

    private static SyncException fail(String what, HttpResponse<String> r) {
        String body = r.body() == null ? "" : r.body();
        if (body.length() > 200) body = body.substring(0, 200) + "…";
        return new SyncException("wger " + what + " → HTTP " + r.statusCode() + " " + body);
    }
}
