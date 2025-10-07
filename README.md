# OIDC Token Workflow Simulator

Single-page React application backed by an Express.js mock server for simulating OIDC device workflows. Device registrations persist on the server in `./keys/config.json`.

## Tech Stack

- React (Vite) + Tailwind CSS
- Express.js backend
- File-based persistence in `keys/config.json`

## Project Structure

```
/oidc-manager
├── keys/
│   ├── config.json
│   ├── id_rsa_priv.pem       # generated
│   └── id_rsa_pub.pem        # generated
├── src/
│   ├── App.jsx
│   ├── index.css
│   └── main.jsx
├── server.js
├── generate_keys.js
├── package.json
└── vite.config.js
```

## Setup

1. Clone or open the repository.
2. Ensure Node.js ≥ 18 is available.
3. Generate RSA keys (one-time):
   ```bash
   node generate_keys.js
   ```

## Running

> `node_modules` is pre-populated—do **not** run `npm install`.

### Start the Express backend

```bash
node server.js
```

The API runs on [http://localhost:3001](http://localhost:3001).

### Start the Vite dev server

```bash
npm run dev
```

The SPA runs on [http://localhost:5173](http://localhost:5173) and proxies API calls to the backend.

## Available Scripts

| Command             | Description                          |
| ------------------- | ------------------------------------ |
| `node server.js`    | Launches the Express backend.        |
| `node generate_keys.js` | Generates RSA key pair in `keys/`. |
| `npm run dev`       | Starts the Vite dev server.          |
| `npm run build`     | Builds the frontend for production.  |
| `npm run preview`   | Serves the production build locally. |

## Key Endpoints

| Endpoint                    | Method | Purpose                          |
| --------------------------- | ------ | -------------------------------- |
| `/api/configs`              | GET    | List registered device configs.  |
| `/api/register-config`      | POST   | Create/update a configuration.   |
| `/api/b2b_token`            | POST   | Mock unattended token issuance.  |
| `/api/user_token`           | POST   | Mock auth-code token exchange.   |
| `/api/final_token_exchange` | POST   | Mock final token exchange.       |
| `/redirect-back-endpoint`   | GET    | Serves SPA for redirect handling.|

Configuration changes are persisted in `keys/config.json`, surviving restarts and refreshes.