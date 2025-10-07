# OIDC Token Workflow Simulator

Single-page React application backed by an Express.js mock server that simulates OIDC attended and unattended device workflows. Device registrations are persisted on the server in `./keys/config.json`.

## Architecture

- **Frontend:** [`src/App.jsx`](src/App.jsx) rendered through Vite with Tailwind CSS utilities loaded from [`src/index.css`](src/index.css).
- **Backend:** [`server/index.js`](server/index.js) exposing mock OIDC endpoints and file-based persistence.
- **Shared constants:** [`shared/environments.js`](shared/environments.js).

## Project Structure

```text
/
├── index.html
├── keys/
│   └── config.json        # Created automatically if missing
├── server/
│   ├── index.js
│   ├── routes/
│   │   └── mockAuthorize.js
│   └── services/
│       └── registerDevice.js
├── shared/
│   └── environments.js
├── src/
│   ├── App.jsx
│   ├── components/
│   ├── hooks/
│   ├── index.css
│   └── main.jsx
├── package.json
├── postcss.config.js
└── tailwind.config.js
```

## Prerequisites

- Node.js 18 or newer.
- Pre-populated `node_modules/` directory (no `npm install` required).

## Initial Setup

1. Generate the RSA key pair (one-time step):
   ```bash
   node generate_keys.js
   ```
2. Ensure `keys/config.json` exists (the server will create it otherwise).

## Development Workflow

1. Start the Express backend:
   ```bash
   npm run server
   ```
   The API listens on `http://localhost:3001`.

2. In a separate terminal, start the Vite dev server:
   ```bash
   npm run dev
   ```
   The SPA runs on `http://localhost:8090` and proxies `/api` requests to the backend.

## Production Build

```bash
npm run build
npm run preview
```

`npm run build` outputs the SPA into `dist/` which is served by `server/index.js` when running in production mode.

## Mock API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/configs` | GET | Returns all persisted device configurations. |
| `/api/register-config` | POST | Registers or updates a device configuration. |
| `/api/b2b_token` | POST | Issues a mock unattended (B2B) token. |
| `/api/user_token` | POST | Exchanges an authorization code for a mock user token. |
| `/api/final_token_exchange` | POST | Produces a final combined token from B2B and user tokens. |
| `/api/authorize` | GET | Simulates the OIDC authorization redirect, returning to `/redirect-back-endpoint`. |
| `/redirect-back-endpoint` | GET | Serves the SPA so [`useWorkflow`](src/hooks/useWorkflow.js) can capture `code` and `state`. |
| `*` | GET | Catch-all that serves the SPA for client-side routing. |

## Key Frontend Flows

- The landing page loads configurations via [`useConfigs`](src/hooks/useConfigs.js) and persists search/filter state between sessions.
- The workflow view orchestrates the attended and unattended steps through [`useWorkflow`](src/hooks/useWorkflow.js), capturing redirect parameters and calling backend endpoints.

## Docker

A multi-stage [`Dockerfile`](Dockerfile) installs dependencies, builds the SPA, prunes dev dependencies, and packages the app for production with the Express server listening on port `8090`.