package com.openscale.sync.mqtt;

import com.openscale.core.sync.SyncConfig;
import com.openscale.core.sync.SyncTarget;
import com.openscale.core.sync.SyncTargetFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * <pre>
 * sync.mqtt.enabled=true
 * sync.mqtt.uri=tcp://192.168.0.10:1883      (ssl://... para TLS)
 * sync.mqtt.username=
 * sync.mqtt.password=
 * sync.mqtt.clientId=openscale-java
 * sync.mqtt.topicPrefix=openscale
 * sync.mqtt.qos=1
 * sync.mqtt.homeAssistant=true
 * sync.mqtt.homeAssistantPrefix=homeassistant
 * </pre>
 */
public final class MqttSyncTargetFactory implements SyncTargetFactory {

    @Override
    public String id() {
        return "mqtt";
    }

    @Override
    public Optional<SyncTarget> create(SyncConfig c) {
        String uri = c.require(id(), "uri");
        String clientId = c.get(id(), "clientId", "openscale-" + UUID.randomUUID().toString().substring(0, 8));
        MqttPublisher publisher = new PahoMqttPublisher(uri, clientId,
                c.get(id(), "username", null), c.get(id(), "password", null));
        return Optional.of(new MqttSyncTarget(publisher,
                c.get(id(), "topicPrefix", "openscale"),
                c.getBoolean(id(), "homeAssistant", true),
                c.get(id(), "homeAssistantPrefix", "homeassistant"),
                Integer.parseInt(c.get(id(), "qos", "1"))));
    }
}
