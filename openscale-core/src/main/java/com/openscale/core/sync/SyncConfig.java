package com.openscale.core.sync;

import java.util.Objects;
import java.util.Properties;

/**
 * Leitura de configuração no formato {@code sync.<id>.<chave>=valor}.
 *
 * <pre>
 * sync.mqtt.enabled=true
 * sync.mqtt.uri=tcp://192.168.0.10:1883
 * </pre>
 */
public final class SyncConfig {

    private final Properties props;

    public SyncConfig(Properties props) {
        this.props = Objects.requireNonNull(props);
    }

    public boolean enabled(String targetId) {
        return Boolean.parseBoolean(get(targetId, "enabled", "false"));
    }

    public String get(String targetId, String key, String defaultValue) {
        String v = props.getProperty("sync." + targetId + "." + key);
        return v == null || v.isBlank() ? defaultValue : v.trim();
    }

    public String require(String targetId, String key) {
        String v = get(targetId, key, null);
        if (v == null) throw new IllegalArgumentException("Falta a propriedade sync." + targetId + "." + key);
        return v;
    }

    public boolean getBoolean(String targetId, String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(targetId, key, Boolean.toString(defaultValue)));
    }

    public Properties raw() {
        return props;
    }
}
