package com.example.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/** Calls the target API with the final access token and prints the response for debugging. */
public final class ApiClient {

    private static final List<String> INTERESTING_HEADERS = List.of(
            "content-type", "content-length", "www-authenticate", "x-request-id", "x-correlation-id", "date");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final PrintStream out;

    public ApiClient(HttpClient httpClient, ObjectMapper mapper, PrintStream out) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.out = out;
    }

    /**
     * Sends {@code GET targetApiEndpoint} with {@code Authorization: Bearer <token>}.
     *
     * @return the HTTP status code
     */
    public int call(String targetApiEndpoint, String accessToken) throws B2bClientException, InterruptedException {
        URI endpoint = URI.create(targetApiEndpoint);
        out.println();
        out.println("== API Call ==");
        out.println("GET " + endpoint);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(HttpSupport.REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json, */*;q=0.8")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw HttpSupport.transportError("API call", endpoint, e);
        }

        int status = response.statusCode();
        out.println("-------------------------------------------------------------");
        out.println("HTTP Status : " + status + (status / 100 == 2 ? " (OK)" : " (ERROR)"));
        for (String name : INTERESTING_HEADERS) {
            response.headers().firstValue(name).ifPresent(value -> out.println(capitalise(name) + ": " + value));
        }
        out.println("-------------------------------------------------------------");
        String body = response.body();
        if (body == null || body.isEmpty()) {
            out.println("<empty body>");
        } else {
            out.println(HttpSupport.parseJson(mapper, body).map(node -> node.toPrettyString()).orElse(body));
        }
        out.println("-------------------------------------------------------------");
        return status;
    }

    private static String capitalise(String headerName) {
        StringBuilder sb = new StringBuilder(headerName.length());
        boolean upper = true;
        for (char c : headerName.toCharArray()) {
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = c == '-';
        }
        return sb.toString();
    }
}
