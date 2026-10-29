# B2B API Testing & Authentication Tool

## Architecture
- Java 17+, Maven, Jackson, and native `java.net.http.HttpClient`.
- Supported environments: Dev1, Dev2, Test1, PreProd.
- Environment files `data/{dev1,dev2,test1,preprod}-config.json` contain `env`, `label`, `issuerUrlBase`, `tokenEndpoint`, `authorizeEndpoint`, `attendedClientId`, `attendedClientSecret`, `callbackUri`, `clientId` (unattended), and `audience` (final exchange).
- Use the attended client ID and secret from the selected environment. Use browser sign-in; do not restore automated username/password login or API targets.
- Current callback for every environment is `https://localhost:8090/openid-callback`, stored as `callbackUri` in each environment file. Preserve this latest spelling and case.
- Read application settings and credentials literally from the selected JSON configuration. Do not use environment variables or placeholder substitution.

## Device registration and keys
- Prompt for environment, RA number, device name, and OTAC.
- Generate a fresh RSA-2048 key pair and sign JWT client assertions with RS256 (`SHA256withRSA`).
- Save `jwk.json` (public RSA JWK), `private-key.pem` (PKCS8), `public-key.pem`, and `device.json` under `data/{env}/devices/{alias}/` before calling registration.
- Do not use keystores or maintain legacy keystore support. Device authentication signs with the private PEM file.
- Registration POSTs to `{issuerUrlBase}/{deviceName}/jwk`, with the device name encoded as one path segment. Send exactly `orgId` (RA number), `otac`, and `key` (the public JWK object saved in `jwk.json`).
- Use the selected device private PEM for RS256 assertions in the unattended and final exchanges.

## Authentication and HTTP
- Attended user sign-in starts an HTTPS callback listener before opening the authorization URL in the browser. Send response_type=code, scope=openid, client_id, redirect_uri, generated state and nonce.
- Accept a matching-state callback, then POST grant_type=authorization_code, code, redirect_uri, client_id and client_secret to tokenEndpoint using HttpSupport.send. Keep the secret out of the authorization URL.
- Use PEM TLS files under `data/callback-tls/` for the localhost listener; generate and reuse a localhost SAN certificate. This certificate is required for HTTPS, separate from device key files. Do not introduce a keystore.
- Close the listener after completion, error, timeout or interruption.
- Log all outgoing HTTP requests and response statuses or transport failures to the console. Show full URLs and literal diagnostic values; do not mask or redact.
- Never hardcode credentials or private keys in Java source.
- Use Lombok annotations for boilerplate where applicable; retain native Java records.
- Use modular Java code and actionable error handling with useful error details.

## Console verbosity
- Use Lombok `@Slf4j` with Logback for diagnostics; keep interactive prompts and menus on the CLI output stream.
- Logback controls verbosity independently from environment details; use INFO for minimal output and DEBUG for verbose output.
- Keep HTTP request/status lines, results, warnings and errors at INFO/WARN/ERROR. Put workflow details and response bodies at DEBUG.
- Verbose logging includes full request/response headers and bodies and token values, without masking.

## Method design
- Keep methods at or below 50 source lines, including their signatures and braces. Extract helpers around clear responsibilities rather than arbitrary line counts.

## Local variable style
- Prefer Java `var` for local variables, enhanced for loops, and try-with-resources wherever type inference preserves behavior. Keep explicit types where Java requires them, including fields, parameters, return types, uninitialized locals, and null initializers.

## Shared HTTP headers
- Apply default headers from `data/http-headers.json` centrally in `HttpSupport.send` for every outgoing HTTP request. Resolve this file under `b2b.dataDir` when overridden.
- Reload the file for each request. Preserve request-specific header values over defaults.
- Expand `{uuid}` in header values using a fresh UUID per header and request; default messageId and correlationId use `urn:uuid:{uuid}`. Do not use environment variables.

## JavaScript-compatible token exchanges
- POST form-encoded token requests to tokenEndpoint with a 10-second timeout through TokenClient and HttpSupport.send.
- Unattended fields: client_id, grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer, assertion.
- User fields: grant_type=authorization_code, code, client_id (attended), client_secret (attended), redirect_uri.
- Final fields: client_id (unattended), client_assertion, audience, grant_type=urn:ietf:params:oauth:grant-type:token-exchange, subject_token_type=urn:ietf:params:oauth:token-type:access_token, subject_token (user), actor_token_type=urn:ietf:params:oauth:token-type:jwt, actor_token (B2B). Do not add client_assertion_type.
- Full flow: B2B token, user browser sign-in/token, then final exchange. Sign a fresh assertion after browser login.
