package com.example.b2b.config;

import com.example.b2b.B2bClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads literal environment details and attended-client settings from {@code data/{env}-config.json}. */
@RequiredArgsConstructor
public final class ConfigLoader {
    private final Path dataDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public AppConfig load(Environment environment) throws B2bClientException {
        var file = dataDir.resolve(environment.fileName());
        AppConfig config;
        try {
            config = mapper.readValue(Files.readString(file), AppConfig.class);
        } catch (NoSuchFileException e) {
            throw new B2bClientException("Configuration file not found: " + file.toAbsolutePath());
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Configuration file " + file + " is invalid: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new B2bClientException("Unable to read configuration file " + file + ": " + e.getMessage(), e);
        }
        validate(config, environment, file);
        return config;
    }

    private static void validate(AppConfig config, Environment environment, Path file) throws B2bClientException {
        if (config == null) {
            throw new B2bClientException("Configuration file must contain an environment object: " + file);
        }
        var missing = new ArrayList<String>();
        require(missing, "env", config.env());
        require(missing, "label", config.label());
        require(missing, "issuerUrlBase", config.issuerUrlBase());
        require(missing, "tokenEndpoint", config.tokenEndpoint());
        require(missing, "authorizeEndpoint", config.authorizeEndpoint());
        if (!missing.isEmpty()) {
            throw new B2bClientException("Configuration " + file + " is missing required field(s): "
                    + String.join(", ", missing));
        }
        if (!environment.id().equals(config.env())) {
            throw new B2bClientException("Configuration " + file + " must use env '" + environment.id() + "'");
        }
    }

    private static void require(List<String> missing, String field, String value) {
        if (value == null || value.isBlank()) missing.add(field);
    }
}
