package com.openscale.core.composition;

import com.openscale.core.ble.BleTransport;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.scale.ScaleHandlerProvider;
import com.openscale.core.scale.ScaleHandlerRegistry;
import com.openscale.core.scale.ScaleSession;
import com.openscale.core.sync.SyncDispatcher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleConsumer;

/**
 * Fachada de alto nível: escolhe a balança, conecta e devolve a composição corporal completa.
 *
 * <pre>{@code
 * BodyCompositionScale scale = BodyCompositionScale.builder(user)
 *         .scale("Relaxmedic")                       // seleciona o handler
 *         .onLiveWeight(kg -> System.out.println(kg))
 *         .build();
 *
 * BodyComposition bc = scale.measure(transport, Duration.ofSeconds(60));
 * System.out.println(bc.report());
 * bc.bodyFatPercent().ifPresent(...);
 * }</pre>
 *
 * Os indicadores vêm, por ordem de prioridade:
 * <ol>
 *   <li>do que a balança/handler publicou ({@link ScaleValuesCalculator});</li>
 *   <li>dos calculadores extras configurados, por exemplo o algoritmo do
 *       fabricante ou o {@link GenericBiaEstimator};</li>
 *   <li>de contas exatas ({@link DerivedValuesCalculator}).</li>
 * </ol>
 * O que nenhum deles conseguir obter volta como {@link MetricSource#UNAVAILABLE}, com o motivo.
 */
public final class BodyCompositionScale {

    private final ScaleUser user;
    private final ScaleHandlerProvider provider;
    private final List<BodyCompositionCalculator> calculators;
    private final DoubleConsumer onLiveWeight;
    private final SyncDispatcher sync;

    private BodyCompositionScale(Builder b) {
        this.user = b.user;
        this.provider = b.provider;
        this.calculators = List.copyOf(b.calculators);
        this.onLiveWeight = b.onLiveWeight;
        this.sync = b.sync;
    }

    public static Builder builder(ScaleUser user) {
        return new Builder(user);
    }

    /** Balanças disponíveis no classpath, para montar um seletor na interface. */
    public static List<String> availableScales() {
        return ScaleHandlerRegistry.fromClasspath().all().stream().map(ScaleHandlerProvider::displayName).toList();
    }

    public ScaleHandlerProvider selectedScale() {
        return provider;
    }

    /** Conecta, espera a medição final e devolve a composição. Assíncrono. */
    public CompletableFuture<BodyComposition> measureAsync(BleTransport transport, Duration timeout) {
        CompletableFuture<BodyComposition> result = new CompletableFuture<>();
        AtomicReference<ScaleSession> sessionRef = new AtomicReference<>();

        ScaleSession session = new ScaleSession(transport, provider, user, new ScaleSession.Listener() {
            @Override
            public void onLiveWeight(double kg) {
                if (onLiveWeight != null) onLiveWeight.accept(kg);
            }

            @Override
            public void onMeasurement(ScaleMeasurement m) {
                try {
                    result.complete(compute(m));
                } catch (RuntimeException e) {
                    result.completeExceptionally(e);
                }
            }

            @Override
            public void onDisconnected() {
                result.completeExceptionally(new IllegalStateException("Balança desconectou antes do resultado"));
            }

            @Override
            public void onError(String message, Throwable cause) {
                result.completeExceptionally(new IllegalStateException(message, cause));
            }
        });
        if (sync != null) session.withSync(sync);
        sessionRef.set(session);

        result.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((bc, err) -> sessionRef.get().close());
        try {
            session.start();
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /** Versão bloqueante de {@link #measureAsync}. */
    public BodyComposition measure(BleTransport transport, Duration timeout)
            throws TimeoutException, InterruptedException {
        try {
            return measureAsync(transport, timeout).get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof TimeoutException t) throw t;
            if (c instanceof RuntimeException r) throw r;
            throw new IllegalStateException(c);
        }
    }

    /** Calcula a composição a partir de uma medição já obtida (útil para reprocessar histórico). */
    public BodyComposition compute(ScaleMeasurement m) {
        BodyComposition.Builder b = new BodyComposition.Builder(m, user);
        new ScaleValuesCalculator().contribute(b);
        for (BodyCompositionCalculator c : calculators) c.contribute(b);
        new DerivedValuesCalculator().contribute(b);
        return b.build();
    }

    public static final class Builder {
        private final ScaleUser user;
        private ScaleHandlerProvider provider;
        private final List<BodyCompositionCalculator> calculators = new ArrayList<>();
        private DoubleConsumer onLiveWeight;
        private SyncDispatcher sync;
        private ScaleHandlerRegistry registry;

        private Builder(ScaleUser user) {
            this.user = Objects.requireNonNull(user);
        }

        public Builder registry(ScaleHandlerRegistry registry) {
            this.registry = registry;
            return this;
        }

        /** Seleciona a balança pelo nome anunciado no Bluetooth (ou pelo nome do modelo). */
        public Builder scale(String advertisedName) {
            ScaleHandlerRegistry r = registry != null ? registry : ScaleHandlerRegistry.fromClasspath();
            this.provider = r.find(advertisedName).orElseThrow(() ->
                    new IllegalArgumentException("Nenhuma balança suportada com o nome '" + advertisedName
                            + "'. Disponíveis: " + r.all().stream().map(ScaleHandlerProvider::displayName).toList()));
            return this;
        }

        public Builder scale(ScaleHandlerProvider provider) {
            this.provider = Objects.requireNonNull(provider);
            return this;
        }

        /** Algoritmo extra (fabricante ou estimador). Ordem importa: o primeiro a preencher vence. */
        public Builder calculator(BodyCompositionCalculator calculator) {
            calculators.add(Objects.requireNonNull(calculator));
            return this;
        }

        public Builder onLiveWeight(DoubleConsumer consumer) {
            this.onLiveWeight = consumer;
            return this;
        }

        /** Também envia a medição bruta para os destinos de sync (CSV, MQTT, wger, Health Connect). */
        public Builder sync(SyncDispatcher dispatcher) {
            this.sync = dispatcher;
            return this;
        }

        public BodyCompositionScale build() {
            if (provider == null) throw new IllegalStateException("Selecione a balança com scale(...)");
            return new BodyCompositionScale(this);
        }
    }
}
