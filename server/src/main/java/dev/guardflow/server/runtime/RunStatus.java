package dev.guardflow.server.runtime;

import java.util.Locale;

public enum RunStatus {
    IDLE,
    RUNNING,
    PAUSED;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
