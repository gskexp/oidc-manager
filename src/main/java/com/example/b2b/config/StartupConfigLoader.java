package com.example.b2b.config;

import com.example.b2b.B2bClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;

/** Missing startup configuration means a fully interactive launch. */
@RequiredArgsConstructor
public final class StartupConfigLoader {
    private final Path dataDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public StartupConfig load() throws B2bClientException {
        var file = dataDir.resolve("startup-config.json");
        try {
            var config = mapper.readValue(Files.readString(file), StartupConfig.class);
            if (config == null) throw new IOException("Expected a startup configuration object");
            validateEnvironment(config.environment());
            return config;
        } catch (NoSuchFileException e) {
            return StartupConfig.defaults();
        } catch (IOException e) {
            throw new B2bClientException("Unable to load " + file + ": " + e.getMessage(), e);
        }
    }

    private static void validateEnvironment(String environment) throws B2bClientException {
        if (environment == null || environment.isBlank()) return;
        if (Arrays.stream(Environment.values()).noneMatch(env -> env.id().equalsIgnoreCase(environment.trim()))) {
            throw new B2bClientException("Unknown startup environment '" + environment + "'; use dev1, dev2, test1 or preprod");
        }
    }
}
