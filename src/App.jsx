import React, { useEffect, useMemo, useReducer, useState } from "react";
import { ENVIRONMENTS } from "../shared/environments.js";

const initialWorkflow = {
  environment: "",
  keyId: "",
  organisationId: "",
  otac: "",
  clientId: "",
  audience: "",
  attendedClientId: "",
  attendedClientSecret: "",
  b2bAssertion: "",
  b2bAssertionExpiresAt: "",
  b2bToken: "",
  b2bTokenExpiresAt: "",
  userToken: "",
  userTokenExpiresAt: "",
  finalToken: "",
  finalTokenExpiresAt: "",
  redirectState: "",
  authCode: ""
};

const formatDateTime = (isoString) => {
  if (!isoString) {
    return "—";
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return date.toLocaleString();
};

const workflowReducer = (state, action) => {
  switch (action.type) {
    case "hydrateConfig":
      return {
        ...initialWorkflow,
        environment: action.payload.environment ?? "",
        keyId: action.payload.keyId ?? "",
        organisationId: action.payload.organisationId ?? "",
        otac: action.payload.otac ?? "",
        clientId: action.payload.clientId ?? "",
        audience: action.payload.audience ?? "",
        b2bAssertion: action.payload.b2bAssertion ?? "",
        b2bAssertionExpiresAt: action.payload.b2bAssertionExpiresAt ?? "",
        b2bToken: action.payload.b2bToken ?? "",
        b2bTokenExpiresAt: action.payload.b2bTokenExpiresAt ?? ""
      };
    case "setField":
      return {
        ...state,
        [action.field]: action.value
      };
    case "setMultiple":
      return {
        ...state,
        ...action.payload
      };
    case "hydrateRedirect":
      return {
        ...state,
        redirectState: action.payload.state ?? "",
        authCode: action.payload.code ?? ""
      };
    case "reset":
      return { ...initialWorkflow };
    default:
      return state;
  }
};

const SESSION_STORAGE_KEYS = {
  attendedCredentials: "oidc-manager.session.attendedCredentials"
};

const loadAttendedCredentialsForKey = (keyId) => {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEYS.attendedCredentials);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    return parsed?.[keyId] ?? null;
  } catch {
    return null;
  }
};

const saveAttendedCredentialsForKey = (keyId, credentials) => {
  if (typeof window === "undefined" || !keyId) {
    return;
  }
  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEYS.attendedCredentials);
    const data = raw ? JSON.parse(raw) : {};
    data[keyId] = credentials;
    window.sessionStorage.setItem(SESSION_STORAGE_KEYS.attendedCredentials, JSON.stringify(data));
  } catch {
    /* ignore */
  }
};

const clearAttendedCredentialsForKey = (keyId) => {
  if (typeof window === "undefined" || !keyId) {
    return;
  }
  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEYS.attendedCredentials);
    if (!raw) {
      return;
    }
    const data = JSON.parse(raw);
    delete data[keyId];
    window.sessionStorage.setItem(SESSION_STORAGE_KEYS.attendedCredentials, JSON.stringify(data));
  } catch {
    /* ignore */
  }
};

const App = () => {
  const [view, setView] = useState("landing");
  const [configs, setConfigs] = useState([]);
  const [loadingConfigs, setLoadingConfigs] = useState(true);
  const [error, setError] = useState("");
  const [workflowState, dispatch] = useReducer(workflowReducer, initialWorkflow);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [envFilter, setEnvFilter] = useState("all");

  const filteredConfigs = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return configs.filter((config) => {
      const matchesEnv = envFilter === "all" || config.environment === envFilter;
      if (!matchesEnv) {
        return false;
      }
      if (!term) {
        return true;
      }
      const haystack = [
        config.keyId,
        config.environment,
        config.organisationId,
        config.otac,
        config.clientId,
        config.audience
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(term);
    });
  }, [configs, envFilter, searchTerm]);

  const loadConfigs = async () => {
    setLoadingConfigs(true);
    setError("");
    try {
      const response = await fetch("/api/configs");
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const body = await response.json();
      setConfigs(Array.isArray(body.configs) ? body.configs : []);
    } catch (loadError) {
      console.error(loadError);
      setError("Unable to load registered devices.");
    } finally {
      setLoadingConfigs(false);
    }
  };

  useEffect(() => {
    loadConfigs();
  }, []);

  useEffect(() => {
    if (loadingConfigs) {
      return;
    }

    const searchParams = new URLSearchParams(window.location.search);
    const code = searchParams.get("code");
    const stateParam = searchParams.get("state");
    const keyIdParam = searchParams.get("keyId");

    if (!code && !stateParam && !keyIdParam) {
      return;
    }

    if (keyIdParam) {
      const matchingConfig = configs.find((cfg) => cfg.keyId === keyIdParam);
      if (matchingConfig) {
        dispatch({ type: "hydrateConfig", payload: matchingConfig });
        const stored = loadAttendedCredentialsForKey(keyIdParam);
        if (stored) {
          dispatch({
            type: "setMultiple",
            payload: {
              attendedClientId: stored.attendedClientId ?? "",
              attendedClientSecret: stored.attendedClientSecret ?? ""
            }
          });
        }
        setView("workflow");
      } else if (configs.length > 0) {
        setError("Returned configuration was not found. Please register or select it manually.");
      }
    } else if (code || stateParam) {
      setView("workflow");
    }

    if (code || stateParam) {
      dispatch({ type: "hydrateRedirect", payload: { code, state: stateParam ?? "" } });
    }

    window.history.replaceState({}, "", window.location.pathname);
  }, [configs, loadingConfigs]);

  useEffect(() => {
    if (!workflowState.keyId) {
      return;
    }
    saveAttendedCredentialsForKey(workflowState.keyId, {
      attendedClientId: workflowState.attendedClientId ?? "",
      attendedClientSecret: workflowState.attendedClientSecret ?? ""
    });
  }, [workflowState.keyId, workflowState.attendedClientId, workflowState.attendedClientSecret]);

  const selectedEnvironment = useMemo(() => {
    return ENVIRONMENTS.find((env) => env.id === workflowState.environment) ?? null;
  }, [workflowState.environment]);

  const handleCopy = async (value) => {
    if (!value) {
      return;
    }
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
      }
    } catch (copyError) {
      console.error("Copy failed:", copyError);
    }
  };

  const handleSelectConfig = (config) => {
    dispatch({ type: "hydrateConfig", payload: config });
    const stored = loadAttendedCredentialsForKey(config.keyId);
    if (stored) {
      dispatch({
        type: "setMultiple",
        payload: {
          attendedClientId: stored.attendedClientId ?? "",
          attendedClientSecret: stored.attendedClientSecret ?? ""
        }
      });
    } else {
      dispatch({
        type: "setMultiple",
        payload: {
          attendedClientId: "",
          attendedClientSecret: ""
        }
      });
    }
    setError("");
    setView("workflow");
  };

  const handleRegister = async (event) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError("");
    const form = event.currentTarget;
    const formData = new FormData(form);
    const payload = {
      keyId: formData.get("keyId")?.toString().trim(),
      environment: formData.get("environment")?.toString(),
      organisationId: formData.get("organisationId")?.toString().trim(),
      otac: formData.get("otac")?.toString().trim(),
      clientId: formData.get("clientId")?.toString().trim(),
      audience: formData.get("audience")?.toString().trim()
    };

    if (
      !payload.keyId ||
      !payload.environment ||
      !payload.organisationId ||
      !payload.otac ||
      !payload.clientId ||
      !payload.audience
    ) {
      setError("All fields are required.");
      setIsSubmitting(false);
      return;
    }

    try {
      const response = await fetch("/api/register-config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message ?? `HTTP ${response.status}`);
      }
      await loadConfigs();
      dispatch({ type: "hydrateConfig", payload });
      dispatch({
        type: "setMultiple",
        payload: {
          attendedClientId: "",
          attendedClientSecret: ""
        }
      });
      clearAttendedCredentialsForKey(payload.keyId);
      setView("workflow");
      form.reset();
    } catch (registerError) {
      console.error(registerError);
      setError(registerError.message ?? "Unable to register configuration.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAuthorizeRedirect = () => {
    if (!workflowState.keyId || !workflowState.environment) {
      setError("Select a configuration before requesting an authorization code.");
      return;
    }
    if (!workflowState.attendedClientId || !workflowState.attendedClientSecret) {
      setError("Attended client credentials are required before starting Step 3.");
      return;
    }
    setError("");
    saveAttendedCredentialsForKey(workflowState.keyId, {
      attendedClientId: workflowState.attendedClientId,
      attendedClientSecret: workflowState.attendedClientSecret
    });
    dispatch({
      type: "setMultiple",
      payload: {
        redirectState: "",
        authCode: "",
        userToken: "",
        userTokenExpiresAt: ""
      }
    });
    const params = new URLSearchParams({
      keyId: workflowState.keyId,
      environment: workflowState.environment
    });
    window.location.href = `/api/authorize?${params.toString()}`;
  };

  const handleB2BToken = async () => {
    if (!workflowState.keyId || !workflowState.environment) {
      setError("Select a configuration before requesting a B2B token.");
      return;
    }
    setIsSubmitting(true);
    setError("");
    try {
      const response = await fetch("/api/b2b_token", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          keyId: workflowState.keyId,
          environment: workflowState.environment,
          organisationId: workflowState.organisationId,
          otac: workflowState.otac,
          clientId: workflowState.clientId,
          audience: workflowState.audience
        })
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message ?? `HTTP ${response.status}`);
      }
      const body = await response.json();
      dispatch({
        type: "setMultiple",
        payload: {
          b2bAssertion: body.assertion ?? "",
          b2bAssertionExpiresAt: body.assertionExpiresAt ?? "",
          b2bToken: body.token ?? "",
          b2bTokenExpiresAt: body.tokenExpiresAt ?? "",
          userToken: "",
          userTokenExpiresAt: "",
          finalToken: "",
          finalTokenExpiresAt: ""
        }
      });
    } catch (b2bError) {
      console.error(b2bError);
      setError(b2bError.message ?? "Unable to request B2B token.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUserToken = async () => {
    if (!workflowState.keyId || !workflowState.authCode) {
      setError("An authorization code is required before exchanging for a user token.");
      return;
    }
    if (!workflowState.attendedClientId || !workflowState.attendedClientSecret) {
      setError("Attended client credentials are required before exchanging for a user token.");
      return;
    }
    setIsSubmitting(true);
    setError("");
    try {
      const response = await fetch("/api/user_token", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          keyId: workflowState.keyId,
          code: workflowState.authCode,
          state: workflowState.redirectState,
          attendedClientId: workflowState.attendedClientId,
          attendedClientSecret: workflowState.attendedClientSecret
        })
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message ?? `HTTP ${response.status}`);
      }
      const body = await response.json();
      dispatch({
        type: "setMultiple",
        payload: {
          userToken: body.token ?? "",
          userTokenExpiresAt: body.tokenExpiresAt ?? "",
          finalToken: "",
          finalTokenExpiresAt: ""
        }
      });
    } catch (userError) {
      console.error(userError);
      setError(userError.message ?? "Unable to exchange user token.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleFinalExchange = async () => {
    if (!workflowState.keyId || !workflowState.userToken || !workflowState.b2bToken) {
      setError("Both B2B and user tokens are required before the final exchange.");
      return;
    }
    setIsSubmitting(true);
    setError("");
    try {
      const response = await fetch("/api/final_token_exchange", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          keyId: workflowState.keyId,
          userToken: workflowState.userToken,
          b2bToken: workflowState.b2bToken
        })
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message ?? `HTTP ${response.status}`);
      }
      const body = await response.json();
      dispatch({
        type: "setMultiple",
        payload: {
          finalToken: body.finalToken ?? "",
          finalTokenExpiresAt: body.expiresAt ?? ""
        }
      });
    } catch (finalError) {
      console.error(finalError);
      setError(finalError.message ?? "Unable to complete final exchange.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClearRedirect = () => {
    dispatch({
      type: "setMultiple",
      payload: {
        redirectState: "",
        authCode: "",
        userToken: "",
        userTokenExpiresAt: ""
      }
    });
  };

  const handleClearWorkflowState = () => {
    if (workflowState.keyId) {
      clearAttendedCredentialsForKey(workflowState.keyId);
    }
    dispatch({
      type: "setMultiple",
      payload: {
        attendedClientId: "",
        attendedClientSecret: "",
        b2bAssertion: "",
        b2bAssertionExpiresAt: "",
        b2bToken: "",
        b2bTokenExpiresAt: "",
        userToken: "",
        userTokenExpiresAt: "",
        finalToken: "",
        finalTokenExpiresAt: "",
        redirectState: "",
        authCode: ""
      }
    });
    setError("");
  };

  const renderLanding = () => (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-800 bg-slate-900/70 p-6 shadow-lg shadow-slate-950/40">
        <h2 className="text-xl font-semibold text-slate-100">Register new device</h2>
        <p className="mt-2 text-sm text-slate-400">
          Provide device metadata below to persist the configuration on the server.
        </p>
        <form className="mt-6 grid gap-4 md:grid-cols-2" onSubmit={handleRegister}>
          <div className="md:col-span-1">
            <label className="text-sm font-medium text-slate-300" htmlFor="environment">
              Environment
            </label>
            <select
              id="environment"
              name="environment"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              defaultValue=""
            >
              <option value="" disabled>
                Select environment
              </option>
              {ENVIRONMENTS.map((env) => (
                <option key={env.id} value={env.id}>
                  {env.label}
                </option>
              ))}
            </select>
          </div>
          <div className="md:col-span-1">
            <label className="text-sm font-medium text-slate-300" htmlFor="keyId">
              Key ID
            </label>
            <input
              id="keyId"
              name="keyId"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              placeholder="device-key-id"
            />
          </div>
          <div className="md:col-span-1">
            <label className="text-sm font-medium text-slate-300" htmlFor="organisationId">
              Organisation ID (RA)
            </label>
            <input
              id="organisationId"
              name="organisationId"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              placeholder="organisation-id"
            />
          </div>
          <div className="md:col-span-1">
            <label className="text-sm font-medium text-slate-300" htmlFor="otac">
              OTAC
            </label>
            <input
              id="otac"
              name="otac"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              placeholder="one-time-access-code"
            />
          </div>
          <div className="md:col-span-1">
            <label className="text-sm font-medium text-slate-300" htmlFor="clientId">
              Client ID
            </label>
            <input
              id="clientId"
              name="clientId"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              placeholder="client-id"
            />
          </div>
          <div className="md:col-span-1">
            <label className="text-sm font-medium text-slate-300" htmlFor="audience">
              Audience
            </label>
            <input
              id="audience"
              name="audience"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              placeholder="api://resource"
            />
          </div>
          <div className="md:col-span-2 flex justify-end">
            <button
              type="submit"
              className="inline-flex items-center rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-400 disabled:opacity-60"
              disabled={isSubmitting}
            >
              Register new device
            </button>
          </div>
        </form>
      </section>

      <section className="rounded-lg border border-slate-800 bg-slate-900/40">
        <header className="border-b border-slate-800 px-6 py-4">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-100">Registered devices</h2>
              <p className="text-xs text-slate-500">
                Filter by environment or search across device fields.
              </p>
            </div>
            <div className="flex flex-1 flex-col gap-3 md:flex-row md:items-center md:justify-end">
              <input
                type="search"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                placeholder="Search devices…"
                className="w-full md:w-64 rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              />
              <select
                value={envFilter}
                onChange={(event) => setEnvFilter(event.target.value)}
                className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              >
                <option value="all">All environments</option>
                {ENVIRONMENTS.map((env) => (
                  <option key={env.id} value={env.id}>
                    {env.label}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={() => setView("workflow")}
                className="rounded-md border border-indigo-400/50 px-3 py-1.5 text-sm text-indigo-300 hover:bg-indigo-500/20"
              >
                Go to workflow
              </button>
            </div>
          </div>
        </header>
        <div className="divide-y divide-slate-800">
          {loadingConfigs && (
            <p className="px-6 py-4 text-sm text-slate-400">Loading configurations…</p>
          )}
          {!loadingConfigs && filteredConfigs.length === 0 && (
            <p className="px-6 py-4 text-sm text-slate-400">
              {configs.length === 0
                ? "No devices registered yet."
                : "No devices match the current filters."}
            </p>
          )}
          {filteredConfigs.map((config) => (
            <button
              key={config.keyId}
              type="button"
              onClick={() => handleSelectConfig(config)}
              className="flex w-full items-center justify-between px-6 py-4 text-left transition hover:bg-slate-900/80"
            >
              <div>
                <p className="font-medium text-slate-100">{config.keyId}</p>
                <p className="text-xs text-slate-400">
                  {config.environment} · {config.organisationId} · {config.clientId}
                </p>
              </div>
              <span className="text-xs uppercase tracking-wide text-indigo-300">Open workflow</span>
            </button>
          ))}
        </div>
      </section>
    </div>
  );

  const renderWorkflow = () => (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-slate-100">OIDC Workflow</h2>
          <p className="text-sm text-slate-400">
            Simulate unattended and user flows using the registered configuration.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleClearWorkflowState}
            className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-900/60"
          >
            Clear workflow state
          </button>
          <button
            type="button"
            onClick={() => {
              setView("landing");
              dispatch({ type: "reset" });
            }}
            className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-900/60"
          >
            Back to landing
          </button>
        </div>
      </div>

      <section className="grid gap-6 md:grid-cols-2">
        <div className="rounded-lg border border-slate-800 bg-slate-900/70 p-6 shadow shadow-slate-950/30">
          <h3 className="text-lg font-medium text-slate-100">Configuration details</h3>
          <dl className="mt-4 grid gap-4 text-sm text-slate-300">
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Environment</dt>
              <dd className="mt-1 text-slate-100">
                {(selectedEnvironment?.label ?? workflowState.environment) || "Not set"}
              </dd>
            </div>
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Issuer</dt>
              <dd className="mt-1 text-slate-100">
                {selectedEnvironment?.issuer ?? "Not available"}
              </dd>
            </div>
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Key ID</dt>
              <dd className="mt-1 text-slate-100">{workflowState.keyId || "Not set"}</dd>
            </div>
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Organisation ID (RA)</dt>
              <dd className="mt-1 text-slate-100">{workflowState.organisationId || "Not set"}</dd>
            </div>
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">OTAC</dt>
              <dd className="mt-1 text-slate-100">{workflowState.otac || "Not set"}</dd>
            </div>
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Client ID</dt>
              <dd className="mt-1 text-slate-100">{workflowState.clientId || "Not set"}</dd>
            </div>
            <div className="rounded border border-slate-800 bg-slate-900/60 px-3 py-2">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Audience</dt>
              <dd className="mt-1 text-slate-100">{workflowState.audience || "Not set"}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-6">
          <h3 className="text-lg font-medium text-slate-100">Redirect parameters</h3>
          <div className="mt-4 space-y-4">
            <div>
              <div className="flex items-center justify-between">
                <label className="text-sm text-slate-300" htmlFor="redirect-state-display">
                  State
                </label>
                <button
                  type="button"
                  onClick={() => handleCopy(workflowState.redirectState)}
                  className="text-xs text-indigo-300 hover:text-indigo-200"
                  disabled={!workflowState.redirectState}
                >
                  Copy
                </button>
              </div>
              <textarea
                id="redirect-state-display"
                className="mt-2 h-16 w-full resize-none rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                readOnly
                value={workflowState.redirectState}
              />
            </div>
            <div>
              <div className="flex items-center justify-between">
                <label className="text-sm text-slate-300" htmlFor="auth-code-display">
                  Authorization code
                </label>
                <button
                  type="button"
                  onClick={() => handleCopy(workflowState.authCode)}
                  className="text-xs text-indigo-300 hover:text-indigo-200"
                  disabled={!workflowState.authCode}
                >
                  Copy
                </button>
              </div>
              <textarea
                id="auth-code-display"
                className="mt-2 h-16 w-full resize-none rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                readOnly
                value={workflowState.authCode}
              />
            </div>
            <button
              type="button"
              onClick={handleClearRedirect}
              className="rounded-md border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-900/60"
              disabled={!workflowState.redirectState && !workflowState.authCode}
            >
              Clear redirect data
            </button>
          </div>
        </div>
      </section>

      <section className="grid gap-6 md:grid-cols-3">
        <div className="rounded-lg border border-slate-800 bg-slate-900/70 p-6">
          <h4 className="text-base font-semibold text-slate-100">Step 2 · B2B token</h4>
          <p className="mt-2 text-sm text-slate-400">
            Generate a client assertion and exchange it for an unattended device token.
          </p>
          <button
            type="button"
            onClick={handleB2BToken}
            className="mt-4 rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-400 disabled:opacity-60"
            disabled={isSubmitting || !workflowState.keyId}
          >
            Issue B2B token
          </button>

          <div className="mt-4 space-y-4">
            <div>
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Client assertion
                </label>
                <button
                  type="button"
                  onClick={() => handleCopy(workflowState.b2bAssertion)}
                  className="text-xs text-indigo-300 hover:text-indigo-200"
                  disabled={!workflowState.b2bAssertion}
                >
                  Copy
                </button>
              </div>
              <textarea
                className="mt-2 h-32 w-full rounded-md border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs text-slate-200"
                readOnly
                value={workflowState.b2bAssertion}
              />
              <p className="mt-2 text-xs text-slate-400">
                Assertion expires at: {formatDateTime(workflowState.b2bAssertionExpiresAt)}
              </p>
            </div>
            <div>
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  B2B token
                </label>
                <button
                  type="button"
                  onClick={() => handleCopy(workflowState.b2bToken)}
                  className="text-xs text-indigo-300 hover:text-indigo-200"
                  disabled={!workflowState.b2bToken}
                >
                  Copy
                </button>
              </div>
              <textarea
                className="mt-2 h-32 w-full rounded-md border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs text-slate-200"
                readOnly
                value={workflowState.b2bToken}
              />
              <p className="mt-2 text-xs text-slate-400">
                Token expires at: {formatDateTime(workflowState.b2bTokenExpiresAt)}
              </p>
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/70 p-6">
          <h4 className="text-base font-semibold text-slate-100">Step 3 · User token</h4>
          <div className="mt-4 space-y-4 text-sm text-slate-200">
            <div className="rounded border border-slate-800 bg-slate-900/70 px-4 py-4">
              <p className="text-sm font-semibold text-slate-100">Attended client credentials</p>
              <p className="mt-1 text-xs text-slate-400">
                Provide the attended client ID and secret before launching the redirect or exchanging the authorization code.
              </p>
              <label
                className="mt-3 block text-xs font-medium uppercase tracking-wide text-slate-400"
                htmlFor="attended-client-id"
              >
                Attended client ID
              </label>
              <input
                id="attended-client-id"
                value={workflowState.attendedClientId}
                onChange={(event) =>
                  dispatch({ type: "setField", field: "attendedClientId", value: event.target.value })
                }
                className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
                placeholder="attended-client-id"
              />

              <label
                className="mt-3 block text-xs font-medium uppercase tracking-wide text-slate-400"
                htmlFor="attended-client-secret"
              >
                Attended client secret
              </label>
              <input
                id="attended-client-secret"
                type="password"
                value={workflowState.attendedClientSecret}
                onChange={(event) =>
                  dispatch({
                    type: "setField",
                    field: "attendedClientSecret",
                    value: event.target.value
                  })
                }
                className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
                placeholder="********"
              />
            </div>

            <div className="rounded border border-slate-800 bg-slate-900/60 px-4 py-4">
              <p className="font-medium text-slate-100">Part 1 · Obtain authorization code</p>
              <p className="mt-1 text-xs text-slate-400">
                Redirect to the OIDC login screen. After authentication, this app receives
                <code className="mx-1 rounded bg-slate-800 px-1 py-0.5 text-xs text-slate-200">code</code>
                and
                <code className="mx-1 rounded bg-slate-800 px-1 py-0.5 text-xs text-slate-200">state</code>
                parameters and stores them above.
              </p>
              <button
                type="button"
                onClick={handleAuthorizeRedirect}
                className="mt-3 rounded-md border border-indigo-400/60 px-3 py-2 text-xs font-medium text-indigo-200 hover:bg-indigo-500/20 disabled:opacity-60"
                disabled={
                  isSubmitting ||
                  !workflowState.keyId ||
                  !workflowState.attendedClientId ||
                  !workflowState.attendedClientSecret
                }
              >
                Redirect for login
              </button>
              <p className="mt-2 text-xs text-slate-500">
                Note: this navigation will reload the SPA when returning.
              </p>
            </div>

            <div className="rounded border border-slate-800 bg-slate-900/60 px-4 py-4">
              <p className="font-medium text-slate-100">Part 2 · Exchange authorization code</p>
              <p className="mt-1 text-xs text-slate-400">
                Use the captured authorization code to request a user token from the backend.
              </p>
              <button
                type="button"
                onClick={handleUserToken}
                className="mt-3 rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-400 disabled:opacity-60"
                disabled={
                  isSubmitting ||
                  !workflowState.keyId ||
                  !workflowState.authCode ||
                  !workflowState.attendedClientId ||
                  !workflowState.attendedClientSecret
                }
              >
                Exchange for user token
              </button>
              <div className="mt-4">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    User token
                  </label>
                  <button
                    type="button"
                    onClick={() => handleCopy(workflowState.userToken)}
                    className="text-xs text-indigo-300 hover:text-indigo-200"
                    disabled={!workflowState.userToken}
                  >
                    Copy
                  </button>
                </div>
                <textarea
                  className="mt-2 h-32 w-full rounded-md border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs text-slate-200"
                  readOnly
                  value={workflowState.userToken}
                />
                <p className="mt-2 text-xs text-slate-400">
                  Token expires at: {formatDateTime(workflowState.userTokenExpiresAt)}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/70 p-6">
          <h4 className="text-base font-semibold text-slate-100">Step 4 · Final exchange</h4>
          <p className="mt-2 text-sm text-slate-400">
            Combine the unattended and user tokens to simulate the final token response.
          </p>
          <button
            type="button"
            onClick={handleFinalExchange}
            className="mt-4 rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-400 disabled:opacity-60"
            disabled={
              isSubmitting ||
              !workflowState.keyId ||
              !workflowState.b2bToken ||
              !workflowState.userToken
            }
          >
            Complete exchange
          </button>
          <div className="mt-4">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                Final token
              </label>
              <button
                type="button"
                onClick={() => handleCopy(workflowState.finalToken)}
                className="text-xs text-indigo-300 hover:text-indigo-200"
                disabled={!workflowState.finalToken}
              >
                Copy
              </button>
            </div>
            <textarea
              className="mt-2 h-32 w-full rounded-md border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs text-slate-200"
              readOnly
              value={workflowState.finalToken}
            />
            <p className="mt-2 text-xs text-slate-400">
              Token expires at: {formatDateTime(workflowState.finalTokenExpiresAt)}
            </p>
          </div>
        </div>
      </section>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-slate-900/80 bg-slate-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div>
            <h1 className="text-xl font-semibold">OIDC Token Workflow Simulator</h1>
            <p className="text-xs text-slate-400">
              React SPA backed by Express with server-side configuration storage.
            </p>
          </div>
          <nav className="flex items-center gap-2 text-sm">
            <button
              type="button"
              onClick={() => setView("landing")}
              className={`rounded-md px-3 py-1.5 ${
                view === "landing" ? "bg-indigo-500 text-white" : "text-slate-300 hover:bg-slate-900/60"
              }`}
            >
              Landing
            </button>
            <button
              type="button"
              onClick={() => setView("workflow")}
              className={`rounded-md px-3 py-1.5 ${
                view === "workflow"
                  ? "bg-indigo-500 text-white"
                  : "text-slate-300 hover:bg-slate-900/60"
              }`}
            >
              Workflow
            </button>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-6 py-10">
        {error && (
          <div className="mb-6 rounded border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-100">
            {error}
          </div>
        )}
        {view === "landing" ? renderLanding() : renderWorkflow()}
      </main>
    </div>
  );
};

export default App;