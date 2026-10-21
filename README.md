# B2B API Testing & Authentication Tool

Interactive CLI for registering devices and running the B2B token → OIDC login → final token → API flow.

## Build & run

```bash
mvn package
java -jar target/oidc-manager-1.0-SNAPSHOT.jar          # run from the project root (paths are relative to it)
java -Db2b.dataDir=/other/dir -jar target/...jar        # use a different config directory
```

## Configuration (`data/{dev,test,prod}-config.json`)

| Field | Notes |
|---|---|
| `endpoints.*` | `registrationEndpoint`, `b2bTokenEndpoint`, `oidcAuthEndpoint`, `oidcTokenEndpoint` (informational), `finalTokenEndpoint`, `targetApiEndpoint` |
| `oauth.clientId`, `redirectUri`, `username`, `password` | Required |
| `oauth.scope` | OIDC scopes, default `openid` |
| `oauth.b2bScope` | Optional scope for the B2B token |
| `oauth.b2bAuthMethod` | `private_key_jwt` (default; signs an RS256/ES256 client assertion with the selected key) or `client_secret_post` (requires `oauth.clientSecret`) |
| `oauth.loginUsernameField` / `loginPasswordField` | Input names on the IdP login form (default `username` / `password`) |
| `oauth.b2bTokenPlacement` | `header` (`Authorization: Bearer <b2b>`, default) or `form` (sent as `oauth.b2bTokenParamName`, default `b2b_token`) |
| `oauth.usePkce` | Send PKCE S256 (default `true`) |
| `jks.jksPath`, `jksPassword`, `keyPassword` | JKS or PKCS12; `keyPassword` defaults to `jksPassword` |
| `jks.keys[]` | `{ "name": "...", "alias": "..." }` – selectable in the menu |

Any string value `${NAME}` is replaced by environment variable `NAME` (used by `prod-config.json`, so prod secrets stay out of the file):

```bash
export PROD_USERNAME=... PROD_USER_PASSWORD=... PROD_JKS_PASSWORD=...
```

For servers using a private CA: `java -Djavax.net.ssl.trustStore=... -Djavax.net.ssl.trustStorePassword=... -jar ...`

## Sample keystores

`data/dev-keystore.jks` and `data/test-keystore.jks` are self-signed samples (one RSA-2048 and one EC P-256 key each). Regenerate / add keys with:

```bash
keytool -genkeypair -keystore data/dev-keystore.jks -storetype JKS -storepass changeit-dev \
  -alias dev-orders-svc -keyalg RSA -keysize 2048 -validity 825 -dname "CN=dev-orders-svc"
```
