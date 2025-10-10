import { useCallback, useEffect, useMemo, useState } from "react";

const LOCAL_STORAGE_KEYS = {
  envFilter: "oidc-manager.envFilter",
  searchTerm: "oidc-manager.searchTerm"
};

const readLocalStorageValue = (key, fallback) => {
  if (typeof window === "undefined") {
    return fallback;
  }
  try {
    const value = window.localStorage.getItem(key);
    return value ?? fallback;
  } catch {
    return fallback;
  }
};

export const useConfigs = ({ setError }) => {
  const [configs, setConfigs] = useState([]);
  const [loadingConfigs, setLoadingConfigs] = useState(true);
  const [searchTerm, setSearchTerm] = useState(() => readLocalStorageValue(LOCAL_STORAGE_KEYS.searchTerm, ""));
  const [envFilter, setEnvFilter] = useState(() => readLocalStorageValue(LOCAL_STORAGE_KEYS.envFilter, "all"));

  const loadConfigs = useCallback(async () => {
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
  }, [setError]);

  useEffect(() => {
    loadConfigs();
  }, [loadConfigs]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    try {
      window.localStorage.setItem(LOCAL_STORAGE_KEYS.searchTerm, searchTerm);
    } catch {
      /* ignore */
    }
  }, [searchTerm]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    try {
      window.localStorage.setItem(LOCAL_STORAGE_KEYS.envFilter, envFilter);
    } catch {
      /* ignore */
    }
  }, [envFilter]);

  const deleteConfig = useCallback(
    async (keyId) => {
      if (!keyId) {
        return false;
      }
      setError("");
      try {
        const response = await fetch(`/api/configs/${encodeURIComponent(keyId)}`, {
          method: "DELETE"
        });
        if (!response.ok) {
          const body = await response.json().catch(() => ({}));
          throw new Error(body.message ?? `HTTP ${response.status}`);
        }
        await loadConfigs();
        return true;
      } catch (deleteError) {
        console.error(deleteError);
        setError(deleteError.message ?? "Unable to delete configuration.");
        return false;
      }
    },
    [loadConfigs, setError]
  );

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

  return {
    configs,
    filteredConfigs,
    loadingConfigs,
    loadConfigs,
    searchTerm,
    setSearchTerm,
    envFilter,
    setEnvFilter,
    deleteConfig
  };
};