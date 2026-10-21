package com.example.b2b.config;

/** Supported target environments and their configuration file names. */
public enum Environment {
    DEV("Dev", "dev-config.json"),
    TEST("Test", "test-config.json"),
    PROD("Prod", "prod-config.json");

    private final String displayName;
    private final String fileName;

    Environment(String displayName, String fileName) {
        this.displayName = displayName;
        this.fileName = fileName;
    }

    public String displayName() {
        return displayName;
    }

    public String fileName() {
        return fileName;
    }
}
