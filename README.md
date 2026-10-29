# B2B API Testing & Authentication Tool

Interactive CLI for device registration, attended user sign-in, unattended B2B tokens, and final token exchange.

## Build and run

```bash
mvn package
java -jar target/oidc-manager-1.0-SNAPSHOT.jar
```

Run from the project root. Use `-Db2b.dataDir=/other/dir` to select another data directory.

## Environment details

Each of `data/dev1-config.json`, `data/dev2-config.json`, `data/test1-config.json`, and `data/preprod-config.json` contains the environment details and attended-client settings:

```json
{
  "env": "dev1",
  "label": "Dev1 — Development 1",
  "issuerUrlBase": "https://api.dev1.example.com",
  "tokenEndpoint": "https://auth.dev1.example.com/oauth2/b2b/token",
  "authorizeEndpoint": "https://login.dev1.example.com/realms/partners/protocol/openid-connect/auth",
  "attendedClientId": "replace-with-dev1-attended-client-id",
  "attendedClientSecret": "replace-with-dev1-attended-client-secret",
  "callbackUri": "https://localhost:8090/openid-callback",
  "clientId": "replace-with-dev1-unattended-client-id",
  "audience": "replace-with-dev1-final-token-audience"
}
```

`label` supplies the environment menu text. `tokenEndpoint` is used for all three token exchanges. `attendedClientId`/`attendedClientSecret` identify the attended client; `clientId` identifies the unattended client and `audience` is sent in the final exchange. Set the attended client ID and secret for each environment before signing in. Registration does not require attended credentials.

The callback is configured per environment. The current value is `https://localhost:8090/openid-callback`; register this exact URL, including case, with the authorization server.

## Register device

Choose the environment and enter the RA number, device name, and OTAC. The app generates an RSA-2048 key pair and saves `jwk.json`, `private-key.pem`, `public-key.pem`, and `device.json` under `data/{env}/devices/{device-alias}/` before contacting the server.

It POSTs to `{issuerUrlBase}/{deviceName}/jwk`, encoding the device name as one path segment:

```json
{ "orgId": "<RA number>", "otac": "<OTAC>", "key": { "kty": "RSA", "kid": "<alias>", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" } }
```

`key` is the same public JWK object saved in `jwk.json`. The registration outcome is recorded in `device.json`. Key material remains saved if registration fails. No keystore or certificate is generated.

## Attended user sign-in

Choose **User Sign-in & Generate Token**, then select an environment:

1. The app starts an HTTPS listener at that environment's `callbackUri` before opening the browser.
2. It opens `authorizeEndpoint` with `response_type=code`, `scope=openid`, `client_id`, `redirect_uri`, and fresh `state` and `nonce` URL parameters. If automatic browser opening is unavailable, open the URL printed in the console.
3. Sign in through the authorization server's browser page. The app receives the callback and checks that its `state` matches the current sign-in.
4. It POSTs the code to `tokenEndpoint` with a 10-second timeout, using form fields `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, and `client_secret`. The client secret comes from the selected environment and is sent in the token request body.
5. It prints the user access token and releases the callback port. Sign-in times out after five minutes; cancellation and token errors return to the menu.

The local HTTPS listener generates and reuses `data/callback-tls/certificate.pem` and `private-key.pem`. Trust that localhost certificate in your browser/OS, or replace those PEM files with a matching locally trusted certificate and key for localhost. These files support HTTPS; device registration still creates no certificates or keystores. Nothing modifies the OS trust settings automatically.

Sign-in navigation runs in your browser with its own cookies and networking. Shared custom headers apply to HTTP requests sent by the application, including the token exchange.

## Unattended and final tokens

Menu option **4** selects an environment and registered device, signs an RS256 assertion with its private PEM, and requests an unattended B2B token. Menu option **5** runs the full flow: unattended B2B token → browser sign-in/user token → final token exchange.

All token calls POST `application/x-www-form-urlencoded` to the selected environment's `tokenEndpoint`, with a 10-second timeout and the shared default headers.

| Exchange | Form fields |
|---|---|
| Unattended | `client_id`, `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`, `assertion` |
| User | `grant_type=authorization_code`, `code`, `client_id`, `client_secret`, `redirect_uri` |
| Final | `client_id`, `client_assertion`, `audience`, `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token_type=urn:ietf:params:oauth:token-type:access_token`, `subject_token`, `actor_token_type=urn:ietf:params:oauth:token-type:jwt`, `actor_token` |

The final exchange uses the user access token as `subject_token` and the B2B access token as `actor_token`. It does not add `client_assertion_type`. A fresh assertion is signed after browser sign-in so it has not expired while the user was logging in.

The HTTP forms follow the supplied JavaScript, with pasted whitespace/typing errors in URNs and Content-Type corrected. Existing assertion generation uses RS256, `kid` = device alias, `iss`/`sub` = unattended `clientId`, `aud` = `tokenEndpoint`, a random `jti`, and a five-minute lifetime. The JavaScript assertion builder has not been supplied for comparison.

## Shared HTTP headers

Edit `data/http-headers.json` to set the default headers on every outgoing request. It contains `messageId` and `correlationId` plus eight editable placeholder names and values.

`urn:uuid:{uuid}` generates a fresh independent UUID for that header on every request. Other values are literal, without environment-variable substitution. The file reloads before each request. Request-specific headers take precedence over defaults, matching names without regard to case. When overriding `b2b.dataDir`, put `http-headers.json` in that directory too.

## Console logging

Diagnostics use Lombok `@Slf4j` and Logback. HTTP request URLs, response statuses, results, warnings, and errors are logged at INFO or above. DEBUG adds request/response headers and bodies without masking. The bundled `src/main/resources/logback.xml` sets the default root level to INFO; change it to DEBUG for verbose output and rebuild, or supply an external Logback configuration using `-Dlogback.configurationFile=/path/to/logback.xml`.
