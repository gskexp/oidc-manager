import { randomUUID } from "crypto";

export function registerMockAuthorizeRoute({
  app,
  readConfigs,
  writeConfigs,
  FRONTEND_FALLBACK,
  AUTHORIZATION_EXPIRY_MS
}) {
  app.get("/api/authorize", (req, res) => {
    const { keyId, state: requestedState } = req.query;
    if (typeof keyId !== "string" || keyId.trim() === "") {
      return res.status(400).send("keyId is required.");
    }

    const configs = readConfigs();
    const config = configs[keyId];
    if (!config) {
      return res.status(404).send("Configuration not found.");
    }

    const state =
      typeof requestedState === "string" && requestedState.trim() !== ""
        ? requestedState.trim()
        : `state-${randomUUID()}`;
    const code = `code-${randomUUID()}`;
    const issuedAt = new Date();
    const expiresAt = new Date(issuedAt.getTime() + AUTHORIZATION_EXPIRY_MS);

    config.lastAuthorization = {
      state,
      code,
      issuedAt: issuedAt.toISOString(),
      expiresAt: expiresAt.toISOString()
    };
    writeConfigs(configs);

    const referer = req.get("referer");
    let origin;
    try {
      origin = referer ? new URL(referer).origin : undefined;
    } catch {
      origin = undefined;
    }
    const redirectBase = origin ?? FRONTEND_FALLBACK;
    const redirectUrl = `${redirectBase}/redirect-back-endpoint?code=${encodeURIComponent(
      code
    )}&state=${encodeURIComponent(state)}`;
    res.redirect(302, redirectUrl);
  });
}