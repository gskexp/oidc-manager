package com.example.b2b.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Device registration settings.
 *
 * @param fixedFields fixed, per-environment fields merged into every registration payload
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistrationSettings(ObjectNode fixedFields) {

    public RegistrationSettings {
        fixedFields = fixedFields == null ? JsonNodeFactory.instance.objectNode() : fixedFields;
    }
}
