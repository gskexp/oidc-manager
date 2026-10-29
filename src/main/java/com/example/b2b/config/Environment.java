package com.example.b2b.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Supported target environments and their configuration file names. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Environment {
    DEV1("dev1", "Dev1"),
    DEV2("dev2", "Dev2"),
    TEST1("test1", "Test1"),
    PREPROD("preprod", "PreProd");

    private final String id;
    private final String displayName;

    public String fileName() {
        return id + "-config.json";
    }
}
