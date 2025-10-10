import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import express from "express";
import { randomUUID } from "crypto";
import { registerDeviceWithJws } from "./services/registerDevice.js";
import { ENVIRONMENT_IDS } from "../shared/environments.js";
import { registerMockAuthorizeRoute } from "./routes/mockAuthorize.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.join(__dirname, "..");
const KEYS_DIR = path.join(ROOT_DIR, "keys");
const CONFIG_PATH = path.join(ROOT_DIR, "keys", "config.json");
const DIST_PATH = path.join(ROOT_DIR, "dist");
const INDEX_HTML = path.join(ROOT_DIR, "index.html");
const FRONTEND_FALLBACK = process.env.FRONTEND_BASE_URL ?? "http://localhost:8090";
const AUTHORIZATION_EXPIRY_MS = 5 * 60 * 1000;

const app = express();
app.use(express.json());

const ensureConfigFile = () => {
  if (!fs.existsSync(CONFIG_PATH)) {
    fs.mkdirSync(path.dirname(CONFIG_PATH), { recursive: true });
    fs.writeFileSync(CONFIG_PATH, "{}");
  }
};

const readConfigs = () => {
  ensureConfigFile();
  const contents = fs.readFileSync(CONFIG_PATH, "utf8") || "{}";
  return JSON.parse(contents);
};

const writeConfigs = (data) => {
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(data, null, 2));
};

const sanitizeConfig = (config) => {
  if (!config) return config;
  const { lastAuthorization, lastTokens, ...rest } = config;
  return {
    ...rest,
    ...(lastTokens ?? {})
  };
};

const serveSpa = (_req, res) => {
  const distIndex = path.join(DIST_PATH, "index.html");
  if (fs.existsSync(distIndex)) {
    return res.sendFile(distIndex);
  }
  return res.sendFile(INDEX_HTML);
};

app.get("/api/configs", (_req, res) => {
  try {
    const configs = readConfigs();
    const list = Object.values(configs).map(sanitizeConfig);
    res.json({ configs: list });
  } catch (error) {
    console.error(error);
    res.status(500).json({ message: "Unable to read configs." });
  }
});

app.post("/api/register-config", async (req, res) => {
  const { keyId, environment, organisationId, otac, clientId, audience } = req.body ?? {};
  if (!keyId || !environment || !organisationId || !otac || !clientId || !audience) {
    return res.status(400).json({ message: "Missing required fields." });
  }
  if (!ENVIRONMENT_IDS.has(environment)) {
    return res.status(400).json({ message: "Unknown environment." });
  }

  let jwsResult;
  try {
    jwsResult = await registerDeviceWithJws({
      keyId,
      environment,
      organisationId,
      otac,
      clientId,
      audience
    });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: "JWS registration failed." });
  }

  if (!jwsResult || jwsResult.status !== 200) {
    return res
      .status(jwsResult?.status ?? 502)
      .json({ message: jwsResult?.message ?? "JWS registration failed." });
  }

  try {
    const configs = readConfigs();
    const now = new Date().toISOString();
    const existing = configs[keyId];
    configs[keyId] = {
      keyId,
      environment,
      organisationId,
      otac,
      clientId,
      audience,
      updatedAt: now,
      createdAt: existing?.createdAt ?? now
    };
    if (existing?.lastAuthorization) {
      configs[keyId].lastAuthorization = existing.lastAuthorization;
    }
    writeConfigs(configs);
    const storedConfig = sanitizeConfig(configs[keyId]);
    res.status(201).json({ message: "Configuration stored.", config: storedConfig });
  } catch (error) {
    console.error(error);
    res.status(500).json({ message: "Unable to persist configuration." });
  }
});

registerMockAuthorizeRoute({
  app,
  readConfigs,
  writeConfigs,
  FRONTEND_FALLBACK,
  AUTHORIZATION_EXPIRY_MS
});

app.post("/api/b2b_token", (req, res) => {
  const { keyId } = req.body ?? {};
  if (!keyId) {
    return res.status(400).json({ message: "keyId is required." });
  }
  const configs = readConfigs();
  const config = configs[keyId];
  if (!config) {
    return res.status(404).json({ message: "Configuration not found." });
  }

  const now = new Date();
  const assertionExpiresAt = new Date(now.getTime() + 5 * 60 * 1000);
  const tokenExpiresAt = new Date(now.getTime() + 60 * 60 * 1000);

  const payload = {
    assertion: `mock-assertion-${keyId}-${randomUUID()}`,
    assertionExpiresAt: assertionExpiresAt.toISOString(),
    token: `mock-b2b-token-${keyId}-${randomUUID()}`,
    tokenExpiresAt: tokenExpiresAt.toISOString(),
    issuedAt: now.toISOString(),
    environment: config.environment
  };

  config.lastTokens = {
    b2bAssertion: payload.assertion,
    b2bAssertionExpiresAt: payload.assertionExpiresAt,
    b2bToken: payload.token,
    b2bTokenExpiresAt: payload.tokenExpiresAt
  };
  writeConfigs(configs);

  res.json(payload);
});

app.post("/api/user_token", (req, res) => {
  const {
    keyId,
    code,
    state,
    attendedClientId,
    attendedClientSecret,
    requestState,
    nonce
  } = req.body ?? {};
  if (!keyId || !code) {
    return res.status(400).json({ message: "keyId and code are required." });
  }
  if (!attendedClientId || !attendedClientSecret) {
    return res.status(400).json({ message: "Attended client credentials are required." });
  }
  if (!requestState || !nonce) {
    return res.status(400).json({ message: "State and nonce are required." });
  }

  const configs = readConfigs();
  const config = configs[keyId];
  if (!config) {
    return res.status(404).json({ message: "Configuration not found." });
  }

  const authorization = config.lastAuthorization;
  if (
    !authorization ||
    authorization.code !== code ||
    (state && authorization.state !== state) ||
    (authorization.expiresAt && Date.now() > Date.parse(authorization.expiresAt))
  ) {
    return res.status(400).json({ message: "Authorization code is invalid or expired." });
  }

  delete config.lastAuthorization;
  writeConfigs(configs);

  const now = new Date();
  const expiresAt = new Date(now.getTime() + 15 * 60 * 1000);

  res.json({
    token: `mock-user-token-${keyId}-${randomUUID()}`,
    tokenExpiresAt: expiresAt.toISOString(),
    receivedCode: code,
    requestState,
    nonce
  });
});

app.post("/api/final_token_exchange", (req, res) => {
  const { keyId, userToken, b2bToken } = req.body ?? {};
  if (!keyId || !userToken || !b2bToken) {
    return res
      .status(400)
      .json({ message: "keyId, userToken, and b2bToken are required." });
  }
  const configs = readConfigs();
  if (!configs[keyId]) {
    return res.status(404).json({ message: "Configuration not found." });
  }

  const expiresAt = new Date(Date.now() + 60 * 60 * 1000).toISOString();

  res.json({
    finalToken: `mock-final-token-${keyId}-${randomUUID()}`,
    expiresAt
  });
});

app.delete("/api/configs/:keyId", (req, res) => {
  const keyId = req.params.keyId?.trim();
  if (!keyId) {
    return res.status(400).json({ message: "keyId is required." });
  }
  const configs = readConfigs();
  if (!configs[keyId]) {
    return res.status(404).json({ message: "Configuration not found." });
  }
  delete configs[keyId];
  writeConfigs(configs);
  deleteKeyArtifacts(keyId);
  return res.status(204).end();
});

app.use(express.static(DIST_PATH));
app.get("/redirect-back-endpoint", serveSpa);
app.get("*", serveSpa);

const port =
  process.env.PORT ??
  (process.env.NODE_ENV === "production" ? 8090 : 3001);

app.listen(port, () => {
  console.log(`Server listening on http://localhost:${port}`);
});