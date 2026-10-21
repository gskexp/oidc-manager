package com.example.b2b.config;

import com.example.b2b.B2bClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and validates {@code data/{env}-config.json} files.
 *
 * <p>Any string value of the form {@code ${NAME}} is replaced with the environment variable
 * {@code NAME}, so production secrets can stay out of the file entirely.</p>
 */
public final class ConfigLoader {

    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final Function<String, String> envLookup;

    public ConfigLoader(Path dataDir) {
        this(dataDir, new ObjectMapper(), System::getenv);
    }

    ConfigLoader(Path dataDir, ObjectMapper mapper, Function<String, String> envLookup) {
        this.dataDir = dataDir;
        this.mapper = mapper;
        this.envLookup = envLookup;
    }

    /**
     * Loads, resolves and validates the configuration for the given environment.
     *
     * @throws B2bClientException if the file is missing, malformed, or incomplete
     */
    public AppConfig load(Environment environment) throws B2bClientException {
        Path file = dataDir.resolve(environment.fileName());
        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(file));
        } catch (NoSuchFileException e) {
            throw new B2bClientException("Configuration file not found: " + file.toAbsolutePath());
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Configuration file " + file + " is not valid JSON (line "
                    + e.getLocation().getLineNr() + "): " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new B2bClientException("Unable to read configuration file " + file + ": " + e.getMessage(), e);
        }

        List<String> unresolved = new ArrayList<>();
        JsonNode resolved = resolvePlaceholders(root, unresolved);
        if (!unresolved.isEmpty()) {
            throw new B2bClientException("Configuration " + file + " references unset environment variable(s): "
                    + String.join(", ", unresolved));
        }

        AppConfig config;
        try {
            config = mapper.treeToValue(resolved, AppConfig.class);
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Configuration file " + file + " has an invalid structure: "
                    + e.getOriginalMessage());
        }
        validate(config, file);
        return config;
    }

    private JsonNode resolvePlaceholders(JsonNode node, List<String> unresolved) {
        if (node.isTextual()) {
            Matcher matcher = ENV_PLACEHOLDER.matcher(node.asText());
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String value = envLookup.apply(matcher.group(1));
                if (value == null) {
                    unresolved.add(matcher.group(1));
                    value = "";
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(sb);
            return TextNode.valueOf(sb.toString());
        }
        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), resolvePlaceholders(field.getValue(), unresolved));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            node.forEach(element -> copy.add(resolvePlaceholders(element, unresolved)));
            return copy;
        }
        return node;
    }

    private static void validate(AppConfig config, Path file) throws B2bClientException {
        List<String> missing = new ArrayList<>();
        if (config.endpoints() == null) {
            missing.add("endpoints");
        } else {
            Endpoints e = config.endpoints();
            require(missing, "endpoints.registrationEndpoint", e.registrationEndpoint());
            require(missing, "endpoints.b2bTokenEndpoint", e.b2bTokenEndpoint());
            require(missing, "endpoints.oidcAuthEndpoint", e.oidcAuthEndpoint());
            require(missing, "endpoints.finalTokenEndpoint", e.finalTokenEndpoint());
            require(missing, "endpoints.targetApiEndpoint", e.targetApiEndpoint());
        }
        if (config.oauth() == null) {
            missing.add("oauth");
        } else {
            OAuthSettings o = config.oauth();
            require(missing, "oauth.clientId", o.clientId());
            require(missing, "oauth.redirectUri", o.redirectUri());
            require(missing, "oauth.username", o.username());
            require(missing, "oauth.password", o.password());
            if (OAuthSettings.AUTH_CLIENT_SECRET_POST.equals(o.b2bAuthMethod())) {
                require(missing, "oauth.clientSecret", o.clientSecret());
            }
        }
        if (config.jks() == null) {
            missing.add("jks");
        } else {
            // jksPassword also protects keystores generated by device registration.
            JksSettings j = config.jks();
            require(missing, "jks.jksPassword", j.jksPassword());
            if (!j.keys().isEmpty()) {
                require(missing, "jks.jksPath", j.jksPath());
            }
            for (int i = 0; i < j.keys().size(); i++) {
                KeyEntry k = j.keys().get(i);
                require(missing, "jks.keys[" + i + "].name", k == null ? null : k.name());
                require(missing, "jks.keys[" + i + "].alias", k == null ? null : k.alias());
            }
        }
        if (!missing.isEmpty()) {
            throw new B2bClientException("Configuration " + file + " is missing required field(s): "
                    + String.join(", ", missing));
        }

        OAuthSettings o = config.oauth();
        if (!List.of(OAuthSettings.AUTH_PRIVATE_KEY_JWT, OAuthSettings.AUTH_CLIENT_SECRET_POST)
                .contains(o.b2bAuthMethod())) {
            throw new B2bClientException("Unsupported oauth.b2bAuthMethod '" + o.b2bAuthMethod()
                    + "' in " + file + " (expected private_key_jwt or client_secret_post)");
        }
        if (!List.of(OAuthSettings.PLACEMENT_HEADER, OAuthSettings.PLACEMENT_FORM)
                .contains(o.b2bTokenPlacement())) {
            throw new B2bClientException("Unsupported oauth.b2bTokenPlacement '" + o.b2bTokenPlacement()
                    + "' in " + file + " (expected header or form)");
        }
    }

    private static void require(List<String> missing, String field, String value) {
        if (value == null || value.isBlank()) {
            missing.add(field);
        }
    }
}
