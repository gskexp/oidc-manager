import { useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { ENVIRONMENTS } from "../../shared/environments.js";

const ALPHANUMERIC_CHARS =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

const generateRandomId = () =>
  Array.from({ length: 10 }, () =>
    ALPHANUMERIC_CHARS[Math.floor(Math.random() * ALPHANUMERIC_CHARS.length)]
  ).join("");

const createRandomSeed = () => ({
  userStateInput: generateRandomId(),
  nonceInput: generateRandomId()
});

const baseWorkflow = {
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
  authCode: "",
  userStateInput: "",
  nonceInput: ""
};

const SESSION_STORAGE_KEYS = {
  attendedCredentials: "oidc-manager.session.attendedCredentials"
};

const workflowReducer = (state, action) => {
  switch (action.type) {
    case "hydrateConfig":
      return {
        ...baseWorkflow,
        ...(action.randomSeed ?? createRandomSeed()),
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
      return { ...baseWorkflow, ...(action.randomSeed ?? createRandomSeed()) };
    default:
      return state;
  }
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

export const useWorkflow = ({ configs, loadingConfigs, setError, setView, loadConfigs }) => {
  const [workflowState, dispatch] = useReducer(
    workflowReducer,
    undefined,
    () => ({ ...baseWorkflow, ...createRandomSeed() })
  );
  const [isSubmitting, setIsSubmitting] = useState(false);

  const selectedEnvironment = useMemo(() => {
    return ENVIRONMENTS.find((env) => env.id === workflowState.environment) ?? null;
  }, [workflowState.environment]);

  const hydrateAttendedCredentials = useCallback((keyId) => {
    if (!keyId) {
      return;
    }
    const stored = loadAttendedCredentialsForKey(keyId);
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
        payload: { attendedClientId: "", attendedClientSecret: "" }
      });
    }
  }, []);

  useEffect(() => {
    if (!workflowState.keyId) {
      return;
    }
    saveAttendedCredentialsForKey(workflowState.keyId, {
      attendedClientId: workflowState.attendedClientId ?? "",
      attendedClientSecret: workflowState.attendedClientSecret ?? ""
    });
  }, [workflowState.keyId, workflowState.attendedClientId, workflowState.attendedClientSecret]);

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
        dispatch({
          type: "hydrateConfig",
          payload: matchingConfig,
          randomSeed: createRandomSeed()
        });
        hydrateAttendedCredentials(keyIdParam);
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
  }, [configs, hydrateAttendedCredentials, loadingConfigs, setError, setView]);

  const handleSelectConfig = useCallback(
    (config) => {
      dispatch({
        type: "hydrateConfig",
        payload: config,
        randomSeed: createRandomSeed()
      });
      hydrateAttendedCredentials(config.keyId);
      setError("");
      setView("workflow");
    },
    [hydrateAttendedCredentials, setError, setView]
  );

  const handleRegister = useCallback(
    async (event) => {
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
        dispatch({
          type: "hydrateConfig",
          payload,
          randomSeed: createRandomSeed()
        });
        dispatch({
          type: "setMultiple",
          payload: { attendedClientId: "", attendedClientSecret: "" }
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
    },
    [loadConfigs, setError, setView]
  );

  const handleAuthorizeRedirect = useCallback(() => {
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
        userTokenExpiresAt: "",
        userStateInput: generateRandomId(),
        nonceInput: generateRandomId()
      }
    });
    const params = new URLSearchParams({
      keyId: workflowState.keyId,
      environment: workflowState.environment
    });
    window.location.href = `/api/authorize?${params.toString()}`;
  }, [
    workflowState.keyId,
    workflowState.environment,
    workflowState.attendedClientId,
    workflowState.attendedClientSecret,
    setError
  ]);

  const handleB2BToken = useCallback(async () => {
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
  }, [
    setError,
    workflowState.attendedClientId,
    workflowState.attendedClientSecret,
    workflowState.audience,
    workflowState.clientId,
    workflowState.environment,
    workflowState.keyId,
    workflowState.organisationId,
    workflowState.otac
  ]);

  const handleUserToken = useCallback(async () => {
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
          requestState: workflowState.userStateInput,
          nonce: workflowState.nonceInput,
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
  }, [
    setError,
    workflowState.attendedClientId,
    workflowState.attendedClientSecret,
    workflowState.authCode,
    workflowState.keyId,
    workflowState.redirectState
  ]);

  const handleFinalExchange = useCallback(async () => {
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
  }, [setError, workflowState.b2bToken, workflowState.keyId, workflowState.userToken]);

  const handleClearRedirect = useCallback(() => {
    dispatch({
      type: "setMultiple",
      payload: {
        redirectState: "",
        authCode: "",
        userToken: "",
        userTokenExpiresAt: ""
      }
    });
  }, []);

  const handleClearWorkflowState = useCallback(() => {
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
        authCode: "",
        userStateInput: generateRandomId(),
        nonceInput: generateRandomId()
      }
    });
    setError("");
  }, [setError, workflowState.keyId]);

  const resetWorkflow = useCallback(() => {
    if (workflowState.keyId) {
      clearAttendedCredentialsForKey(workflowState.keyId);
    }
    dispatch({ type: "reset", randomSeed: createRandomSeed() });
  }, [workflowState.keyId]);

  const updateField = useCallback((field, value) => {
    dispatch({ type: "setField", field, value });
  }, []);

  const handleCopy = useCallback(async (value) => {
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
  }, []);

  const regenerateStateNonce = useCallback(() => {
    dispatch({ type: "setMultiple", payload: createRandomSeed() });
  }, []);

  return {
    workflowState,
    selectedEnvironment,
    isSubmitting,
    updateField,
    handleRegister,
    handleSelectConfig,
    handleAuthorizeRedirect,
    handleB2BToken,
    handleUserToken,
    handleFinalExchange,
    handleClearRedirect,
    handleClearWorkflowState,
    handleCopy,
    resetWorkflow,
    regenerateStateNonce
  };
};