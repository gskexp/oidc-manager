package com.example.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Reloads shared default headers for every outgoing request. */
@RequiredArgsConstructor
final class DefaultHeaders {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    HttpRequest apply(HttpRequest request) throws IOException {
        var headers = mapper.readTree(Files.readString(file));
        if (headers == null || !headers.isObject()) {
            throw new IOException("Default headers file must contain a JSON object: " + file);
        }
        var builder = HttpRequest.newBuilder(request, (name, value) -> true);
        var fields = headers.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getValue().isTextual()) {
                throw new IOException("Header '" + field.getKey() + "' must have a string value in " + file);
            }
            if (request.headers().firstValue(field.getKey()).isEmpty()) {
                addHeader(builder, field.getKey(), field.getValue().asText());
            }
        }
        return builder.build();
    }

    private void addHeader(HttpRequest.Builder builder, String name, String template) throws IOException {
        var value = template.contains("{uuid}")
                ? template.replace("{uuid}", UUID.randomUUID().toString()) : template;
        try {
            builder.header(name, value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid default header '" + name + "' in " + file, e);
        }
    }
}
