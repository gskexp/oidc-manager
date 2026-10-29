# B2B API Testing & Authentication Tool

Interactive CLI for device registration, attended user sign-in, unattended B2B tokens, and final token exchange.

## Build and run

```bash
mvn package
java -jar target/oidc-manager-1.0-SNAPSHOT.jar
```

Run from the project root. Use `-Db2b.dataDir=/other/dir` to select another data directory.

## Startup state and defaults

Edit `data/startup-config.json` to skip repeated selections. The shipped file launches the normal menu with no defaults. For example, run the full flow immediately for an existing device and exit afterward:

```json
{
  "action": "FULL_FLOW",
  "environment": "dev1",
  "deviceAlias": "device-a",
  "exitAfterAction": true
}
```

`deviceAlias` is the alias in `data/{env}/devices/{alias}/device.json` (also the device folder name). Unknown or inactive device aliases produce an error. Use `null` or omit a default to be prompted for that selection.

| Setting | Meaning |
|---|---|
| `action` | `MAIN_MENU` (default), `TOKEN_STEPS`, `ACTIVATE_DEVICE`, `FULL_FLOW`, `UNATTENDED_TOKEN`, `ATTENDED_TOKEN`, or `FINAL_TOKEN` |
| `environment` | `dev1`, `dev2`, `test1`, or `preprod`; skips the environment prompt on subsequent actions too |
| `deviceAlias` | Skips device selection for unattended, final-only and full-flow actions |
| `activation` | Optional `raNumber`, `deviceName`, and `otac` defaults; missing values are prompted |
| `tokens` | Optional `userToken` and `b2bToken` inputs for final-only exchange when no token exists in the current session |
| `exitAfterAction` | `true` exits after the startup action; `false` returns to the main menu |

To skip the initial menu and environment choice for activation, use `"action": "ACTIVATE_DEVICE"` and `"environment": "dev1"`. To open the individual-step submenu directly, use `"action": "TOKEN_STEPS"`. Already activated devices still require confirmation before replacing their keys.

The startup file is read once per launch and lives under `b2b.dataDir` when that directory is overridden. A missing file defaults to interactive behavior; invalid settings produce an error.

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

## Activate device

Choose **Activate Device**, then the environment and enter the RA number, device name, and OTAC. The app generates an RSA-2048 key pair and saves `jwk.json`, `private-key.pem`, `public-key.pem`, and `device.json` under `data/{env}/devices/{device-alias}/` before contacting the server.

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

The main menu offers **Activate Device**, **Select Device & Run Full Flow**, **Check Individual Token Steps**, and **Exit**.

Full flow selects an environment and activated device, then runs unattended B2B token → browser sign-in/user token → final exchange. At INFO level, only the final token is presented as a token result; HTTP diagnostics and browser instructions remain visible. DEBUG also includes intermediate tokens and response bodies. Browser sign-in remains the current attended login mechanism.

The individual-step submenu offers **Unattended B2B Token**, **Attended User Token**, and **Final Token Exchange Only**. Tokens obtained in that session are reused for the same environment (and device for B2B). Final-only never repeats sign-in or unattended exchange: missing tokens come from startup config or console prompts.

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

## PKIX failures when exchanging an authorization code

A callback reaching localhost and a token POST reaching the remote token server use different TLS connections. The browser can trust the sign-in server while the JVM does not trust the token endpoint's CA chain. A private issuer, corporate TLS proxy, or missing server intermediate certificate can produce `PKIX path building failed`.

Place the organisation's root/intermediate CA certificate files in `data/trusted-certs/` as `.pem`, `.crt`, or `.cer` files, then start the operation again. PEM bundles are supported. The app adds these certificates to Java's normal trusted CAs; it does not modify the global JDK installation. When using `-Db2b.dataDir`, put `trusted-certs/` under that directory.

Use the CA that issued the remote token server's certificate, or the corporate proxy CA if TLS interception is in use. The generated localhost callback certificate does not establish trust of the remote token endpoint. Token exchange errors now include the failed endpoint and the certificate directory to configure.
