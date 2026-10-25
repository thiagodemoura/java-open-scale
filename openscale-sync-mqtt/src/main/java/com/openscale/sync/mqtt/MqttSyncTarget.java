package com.openscale.sync.mqtt;

import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.SyncException;
import com.openscale.core.sync.SyncTarget;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Publica cada medição como JSON retido em {@code <prefix>/<usuario>/measurement}.
 * Com a descoberta habilitada, anuncia os sensores ao Home Assistant na primeira medição de cada usuário.
 */
public final class MqttSyncTarget implements SyncTarget {

    private final MqttPublisher publisher;
    private final String topicPrefix;
    private final boolean haDiscovery;
    private final String haPrefix;
    private final int qos;
    private final Set<String> announced = new HashSet<>();

    public MqttSyncTarget(MqttPublisher publisher, String topicPrefix, boolean haDiscovery, String haPrefix, int qos) {
        this.publisher = publisher;
        this.topicPrefix = topicPrefix;
        this.haDiscovery = haDiscovery;
        this.haPrefix = haPrefix;
        this.qos = qos;
    }

    @Override
    public String id() {
        return "mqtt";
    }

    @Override
    public synchronized void sync(ScaleMeasurement m, ScaleUser user) throws SyncException {
        try {
            if (haDiscovery && announced.add(user.id())) {
                for (Map.Entry<String, byte[]> e :
                        MqttPayloads.homeAssistantDiscovery(haPrefix, topicPrefix, user).entrySet()) {
                    publisher.publish(e.getKey(), e.getValue(), qos, true);
                }
            }
            publisher.publish(MqttPayloads.stateTopic(topicPrefix, user), MqttPayloads.state(m, user), qos, true);
        } catch (Exception e) {
            announced.remove(user.id());
            throw new SyncException("MQTT: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        publisher.close();
    }
}
