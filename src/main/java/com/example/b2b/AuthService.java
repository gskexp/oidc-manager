package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.OAuthSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the three-step authentication workflow:
 * <ol>
 *   <li>Obtain a B2B token from {@code b2bTokenEndpoint} (signed client assertion or client secret).</li>
 *   <li>Drive the OIDC login (authorize → login form → redirects) with a cookie-aware client and capture
 *       the authorization {@code code} from the redirect to {@code redirectUri}.</li>
 *   <li>Exchange the code plus the B2B token at {@code finalTokenEndpoint} for the final access token.</li>
 * </ol>
 */
public final class AuthService {

    private static final int MAX_REDIRECTS = 20;
    private static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final ObjectMapper mapper;
    private final JwtSigner jwtSigner;
    private final LoginFormParser formParser;
    private final SecureRandom random;
    private final PrintStream out;

    /** Result of the final token exchange. */
    public record TokenResult(String accessToken, String tokenType, Long expiresIn, JsonNode raw) {
    }

    /** Authorization code captured from the redirect, plus the PKCE verifier it is bound to. */
    record AuthorizationCode(String code, String codeVerifier) {
    }

    public AuthService(ObjectMapper mapper, PrintStream out) {
        this.mapper = mapper;
        this.jwtSigner = new JwtSigner(mapper);
        this.formParser = new LoginFormParser();
        this.random = new SecureRandom();
        this.out = out;
    }

    /** Runs all three steps and returns the final token. */
    public TokenResult authenticate(AppConfig config, SigningKey key) throws B2bClientException, InterruptedException {
        out.println();
        out.println("== Authentication [" + config.environment() + " / " + key.name() + "] ==");
        String b2bToken = obtainB2bToken(config, key);
        AuthorizationCode code = obtainAuthorizationCode(config);
        return exchangeForFinalToken(config, code, b2bToken);
    }

    // ---------------------------------------------------------------------------------------------
    // Step 1: B2B token
    // ---------------------------------------------------------------------------------------------

    String obtainB2bToken(AppConfig config, SigningKey key) throws B2bClientException, InterruptedException {
        OAuthSettings oauth = config.oauth();
        URI endpoint = URI.create(config.endpoints().b2bTokenEndpoint());
        out.println("[Step 1] Requesting B2B token (" + oauth.b2bAuthMethod() + ") from " + endpoint);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", oauth.clientId());
        if (oauth.b2bScope() != null && !oauth.b2bScope().isBlank()) {
            form.put("scope", oauth.b2bScope());
        }
        if (OAuthSettings.AUTH_PRIVATE_KEY_JWT.equals(oauth.b2bAuthMethod())) {
            KeyStoreService keyStore = KeyStoreService.load(key);
            PrivateKey privateKey = keyStore.privateKey(key.alias());
            out.println("         Signing client assertion with " + privateKey.getAlgorithm()
                    + " key '" + key.alias() + "'");
            form.put("client_assertion_type", CLIENT_ASSERTION_TYPE);
            form.put("client_assertion",
                    jwtSigner.createClientAssertion(privateKey, key.alias(), oauth.clientId(), endpoint.toString()));
        } else {
            form.put("client_secret", oauth.clientSecret());
        }

        HttpRequest request = formPost(endpoint, form).build();
        HttpResponse<String> response = send(HttpSupport.newClient(), request, "Step 1 (B2B token)");
        String token = requireAccessToken(response, "Step 1 (B2B token)");
        out.println("         B2B token acquired: " + HttpSupport.mask(token));
        return token;
    }

    // ---------------------------------------------------------------------------------------------
    // Step 2: OIDC login & authorization code
    // ---------------------------------------------------------------------------------------------

    /**
     * Follows the OIDC login flow hop by hop. Redirects are tracked manually (rather than with
     * {@link HttpClient.Redirect#NORMAL}) so that the final redirect to {@code redirectUri} is
     * intercepted instead of followed: the callback is usually not a reachable server, and the code
     * must be read from the {@code Location} header.
     */
    AuthorizationCode obtainAuthorizationCode(AppConfig config) throws B2bClientException, InterruptedException {
        OAuthSettings oauth = config.oauth();
        URI redirectUri = URI.create(oauth.redirectUri());
        String state = randomUrlSafe(16);
        String nonce = randomUrlSafe(16);
        String codeVerifier = oauth.usePkce() ? randomUrlSafe(32) : null;

        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", oauth.clientId());
        query.put("redirect_uri", oauth.redirectUri());
        query.put("scope", oauth.scope());
        query.put("state", state);
        query.put("nonce", nonce);
        if (codeVerifier != null) {
            query.put("code_challenge", s256(codeVerifier));
            query.put("code_challenge_method", "S256");
        }
        String authEndpoint = config.endpoints().oidcAuthEndpoint();
        URI authorizeUri = URI.create(authEndpoint + (authEndpoint.contains("?") ? "&" : "?")
                + HttpSupport.formEncode(query));

        out.println("[Step 2] Starting OIDC login at " + HttpSupport.withoutQuery(authorizeUri));

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpSupport.newClient(cookies);
        HttpRequest request = browserGet(authorizeUri);
        boolean credentialsSubmitted = false;

        for (int hop = 1; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<String> response = send(client, request, "Step 2 (OIDC login)");
            int status = response.statusCode();
            URI current = request.uri();
            out.printf("         %2d. %-4s %s -> %d%n", hop, request.method(), HttpSupport.withoutQuery(current), status);

            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElseThrow(() ->
                        new B2bClientException("Step 2 (OIDC login): HTTP " + status + " redirect from "
                                + HttpSupport.withoutQuery(current) + " has no Location header"));
                URI next = current.resolve(location.trim());
                if (isCallback(next, redirectUri)) {
                    out.println("             Captured redirect to " + HttpSupport.withoutQuery(next));
                    return new AuthorizationCode(extractCode(next, state), codeVerifier);
                }
                request = browserGet(next);
                continue;
            }

            if (status == 200) {
                if (credentialsSubmitted) {
                    throw new B2bClientException("Step 2 (OIDC login): the IdP returned a page instead of a redirect "
                            + "after the credentials were submitted. Check oauth.username/oauth.password, or the "
                            + "account may require an extra step (consent, MFA, password change).");
                }
                LoginFormParser.HtmlForm form = formParser.parse(response.body(), oauth.loginUsernameField())
                        .orElseThrow(() -> new B2bClientException("Step 2 (OIDC login): no login form found at "
                                + HttpSupport.withoutQuery(current)
                                + ". Check oauth.loginUsernameField or the oidcAuthEndpoint."));
                request = loginSubmission(current, form, oauth);
                credentialsSubmitted = true;
                out.println("             Submitting credentials for user '" + oauth.username() + "'");
                continue;
            }

            throw new B2bClientException("Step 2 (OIDC login): unexpected HTTP " + status + " from "
                    + HttpSupport.withoutQuery(current) + ": "
                    + HttpSupport.describeOAuthError(mapper, response.body()));
        }
        throw new B2bClientException("Step 2 (OIDC login): exceeded " + MAX_REDIRECTS
                + " redirects without reaching redirectUri " + oauth.redirectUri()
                + ". Verify oauth.redirectUri matches the client registration exactly.");
    }

    private HttpRequest loginSubmission(URI pageUri, LoginFormParser.HtmlForm form, OAuthSettings oauth) {
        Map<String, String> fields = new LinkedHashMap<>(form.fields());
        fields.put(oauth.loginUsernameField(), oauth.username());
        fields.put(oauth.loginPasswordField(), oauth.password());
        URI action = form.action().isBlank() ? pageUri : pageUri.resolve(form.action().trim());

        if ("GET".equals(form.method())) {
            String base = HttpSupport.withoutQuery(action);
            return browserGet(URI.create(base + "?" + HttpSupport.formEncode(fields)));
        }
        return HttpRequest.newBuilder(action)
                .timeout(HttpSupport.REQUEST_TIMEOUT)
                .header("Content-Type", HttpSupport.FORM_CONTENT_TYPE)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .header("Origin", action.getScheme() + "://" + action.getRawAuthority())
                .header("Referer", HttpSupport.withoutQuery(pageUri))
                .POST(HttpRequest.BodyPublishers.ofString(HttpSupport.formEncode(fields)))
                .build();
    }

    private static boolean isCallback(URI candidate, URI redirectUri) {
        return Objects.equals(lower(candidate.getScheme()), lower(redirectUri.getScheme()))
                && Objects.equals(lower(candidate.getHost()), lower(redirectUri.getHost()))
                && effectivePort(candidate) == effectivePort(redirectUri)
                && Objects.equals(normalisePath(candidate.getPath()), normalisePath(redirectUri.getPath()));
    }

    private static String extractCode(URI callback, String expectedState) throws B2bClientException {
        Map<String, String> params = HttpSupport.queryParams(callback);
        if (params.containsKey("error")) {
            throw new B2bClientException("Step 2 (OIDC login): IdP returned error '" + params.get("error") + "'"
                    + (params.containsKey("error_description") ? ": " + params.get("error_description") : ""));
        }
        String code = params.get("code");
        if (code == null || code.isBlank()) {
            throw new B2bClientException("Step 2 (OIDC login): redirect to redirectUri did not contain a 'code' "
                    + "query parameter (is the IdP using response_mode=fragment or form_post?)");
        }
        if (!expectedState.equals(params.get("state"))) {
            throw new B2bClientException("Step 2 (OIDC login): 'state' in the callback does not match the request "
                    + "(possible CSRF or a stale session)");
        }
        return code;
    }

    // ---------------------------------------------------------------------------------------------
    // Step 3: Final token exchange
    // ---------------------------------------------------------------------------------------------

    TokenResult exchangeForFinalToken(AppConfig config, AuthorizationCode code, String b2bToken)
            throws B2bClientException, InterruptedException {
        OAuthSettings oauth = config.oauth();
        URI endpoint = URI.create(config.endpoints().finalTokenEndpoint());
        boolean inHeader = OAuthSettings.PLACEMENT_HEADER.equals(oauth.b2bTokenPlacement());
        out.println("[Step 3] Exchanging authorization code at " + endpoint + " (B2B token via "
                + (inHeader ? "Authorization header" : "form parameter '" + oauth.b2bTokenParamName() + "'") + ")");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code.code());
        form.put("redirect_uri", oauth.redirectUri());
        form.put("client_id", oauth.clientId());
        form.put("code_verifier", code.codeVerifier());
        if (!inHeader) {
            form.put(oauth.b2bTokenParamName(), b2bToken);
        }

        HttpRequest.Builder builder = formPost(endpoint, form);
        if (inHeader) {
            builder.header("Authorization", "Bearer " + b2bToken);
        }
        HttpResponse<String> response = send(HttpSupport.newClient(), builder.build(), "Step 3 (final token)");
        String accessToken = requireAccessToken(response, "Step 3 (final token)");

        JsonNode json = HttpSupport.parseJson(mapper, response.body()).orElseThrow();
        Long expiresIn = json.hasNonNull("expires_in") ? json.path("expires_in").asLong() : null;
        String tokenType = json.path("token_type").asText("Bearer");
        out.println("         Final access token acquired: " + HttpSupport.mask(accessToken)
                + " (type " + tokenType + (expiresIn != null ? ", expires in " + expiresIn + "s" : "") + ")");
        return new TokenResult(accessToken, tokenType, expiresIn, json);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static HttpRequest.Builder formPost(URI endpoint, Map<String, String> form) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(HttpSupport.REQUEST_TIMEOUT)
                .header("Content-Type", HttpSupport.FORM_CONTENT_TYPE)
                .header("Accept", "application/json")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(HttpSupport.formEncode(form)));
    }

    private static HttpRequest browserGet(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(HttpSupport.REQUEST_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .GET()
                .build();
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request, String step)
            throws B2bClientException, InterruptedException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw HttpSupport.transportError(step, request.uri(), e);
        }
    }

    private String requireAccessToken(HttpResponse<String> response, String step) throws B2bClientException {
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new B2bClientException(step + ": token endpoint returned HTTP " + status + ": "
                    + HttpSupport.describeOAuthError(mapper, response.body()));
        }
        JsonNode json = HttpSupport.parseJson(mapper, response.body()).orElseThrow(() ->
                new B2bClientException(step + ": token endpoint returned a non-JSON body (HTTP " + status + ")"));
        String token = json.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new B2bClientException(step + ": token response has no 'access_token' field");
        }
        return token;
    }

    private String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String s256(String verifier) throws B2bClientException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new B2bClientException("SHA-256 is not available in this JVM", e);
        }
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String normalisePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
