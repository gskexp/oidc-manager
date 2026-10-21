# Project Generation Instructions: B2B API Testing & Authentication Tool

You are a senior Java developer with expert knowledge in OAuth 2.0, OpenID Connect (OIDC), TLS, Java KeyStore (JKS), and API testing. Your task is to write and structure a complete, production-ready, runnable command-line Java application from scratch based on the specifications below. Do not output partial snippets; implement all files fully.

---

## 1. Project Architecture & Requirements

### Tech Stack
- **Language**: Java 17+
- **Build Tool**: Apache Maven (`pom.xml`)
- **JSON Library**: Jackson (`com.fasterxml.jackson.core:jackson-databind`)
- **HTTP Client**: Native `java.net.http.HttpClient` with `CookieManager` for cookie and redirect handling.

### Directory Structure & Storage
- All configuration files, JKS keystores, and device metadata must reside inside a local `./data/` folder in the project root.
- Maintain separate configuration files for environments: `./data/dev-config.json`, `./data/test-config.json`, and `./data/prod-config.json`.
- Each configuration file must support **multiple keys/service accounts** via an array of keys (each containing a friendly `name` and a JKS `alias`).

---

## 2. Component Specifications to Generate

### A. Maven Configuration (`pom.xml`)
- Configure for Java 17 source/target.
- Include the Jackson databind dependency.

### B. Configuration Files (`data/{env}-config.json`)
Create realistic sample configuration files (`dev-config.json`, `test-config.json`, `prod-config.json`) featuring:
- Environment name
- Endpoints: `registrationEndpoint`, `b2bTokenEndpoint`, `oidcAuthEndpoint`, `oidcTokenEndpoint`, `finalTokenEndpoint`, `targetApiEndpoint`
- OAuth parameters: `clientId`, `redirectUri`, `username`, `password`
- JKS properties: `jksPath`, `jksPassword`, and a `keys` array containing at least two key objects (`name` and `alias`).

### C. Interactive CLI Menu (`com.example.b2b.Main.java`)
The application must start with an interactive command-line menu using `java.util.Scanner`:
1. **Register Device**
2. **Generate Token & Test API**
3. **Exit**

If the user selects **1** or **2**:
- Prompt them to select an **Environment** (1. Dev, 2. Test, 3. Prod).
- Load the corresponding configuration file, parse the `keys` array, display the available keys with their friendly names, and prompt the user to **select a specific key/alias**.
- Proceed with either Device Registration or the full Token & API testing flow using the selected environment and key alias.

### D. Device Registration Service (`com.example.b2b.DeviceRegistrationService.java`)
- Loads the specified JKS file and extracts the `PublicKey` corresponding to the chosen key alias.
- Base64 encodes the public key.
- Sends a POST request to the `registrationEndpoint` containing the `client_id` and public key payload using `HttpClient`.
- Prints success or failure logs.

### E. Authentication Service (`com.example.b2b.AuthService.java`)
Implements the multi-step authentication workflow:
1. **Step 1 (B2B Token)**: Loads the JKS file and private key for the selected alias. Authenticates against the `b2bTokenEndpoint` (handling client assertion signing or form-based credentials) to obtain the B2B token.
2. **Step 2 (OIDC User Login & Auth Code)**: Uses `HttpClient` with a `CookieManager` and automatic redirect tracking to initiate the OIDC auth flow, post the username/password to the login form, handle cookies and redirects, and capture the authorization code from the callback redirect query parameters (`code=...`).
3. **Step 3 (Final Token Exchange)**: Sends a token request to the `finalTokenEndpoint` supplying the authorization code and the B2B token (e.g., via header or form parameter) to acquire the final access token.

### F. API Client (`com.example.b2b.ApiClient.java`)
- Takes the final token and invokes the `targetApiEndpoint`.
- Prints the HTTP status code and response body clearly to the console for ad hoc debugging.

---

## 3. Development Guidelines & Constraints
- **No Hardcoded Secrets**: Passwords, private keys, and tokens must never be hardcoded into Java source files; always read them from configuration files or keystores.
- **Robust Error Handling**: Catch specific exceptions (`IOException`, `KeyStoreException`, `InterruptedException`, etc.) and provide clean, actionable error messages to `System.err` without leaking credentials.
- **Clean Code**: Write well-documented, modular, object-oriented Java code following standard conventions.