package com.example.b2b.config;

/** Supported target environments and their configuration file names. */
public enum Environment {
    DEV("dev", "Dev"),
    TEST("test", "Test"),
    PROD("prod", "Prod");

    private final String id;
    private final String displayName;

    Environment(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Short id used for file and directory names, e.g. {@code dev}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String fileName() {
        return id + "-config.json";
    }
}
