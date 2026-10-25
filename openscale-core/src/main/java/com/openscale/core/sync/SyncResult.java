package com.openscale.core.sync;

/** Resultado de uma sincronização para um alvo. */
public record SyncResult(String targetId, boolean success, String message) {

    public static SyncResult ok(String targetId) {
        return new SyncResult(targetId, true, "ok");
    }

    public static SyncResult failed(String targetId, String message) {
        return new SyncResult(targetId, false, message);
    }
}
