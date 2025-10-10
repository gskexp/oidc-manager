import React from "react";

export const LandingView = ({
  environments,
  loadingConfigs,
  configs,
  filteredConfigs,
  searchTerm,
  envFilter,
  setSearchTerm,
  setEnvFilter,
  isSubmitting,
  onRegister,
  onSelectConfig,
  onDeleteConfig,
  onGoToWorkflow
}) => (
  <div className="space-y-6">
    <section className="rounded-lg border border-slate-800 bg-slate-900/70 p-6 shadow-lg shadow-slate-950/40">
      <h2 className="text-xl font-semibold text-slate-100">Register new device</h2>
      <p className="mt-2 text-sm text-slate-400">
        Provide device metadata below to persist the configuration on the server.
      </p>
      <form className="mt-6 grid gap-4 md:grid-cols-2" onSubmit={onRegister}>
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
            {environments.map((env) => (
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
            <p className="text-xs text-slate-500">Filter by environment or search across device fields.</p>
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
                {environments.map((env) => (
                  <option key={env.id} value={env.id}>
                    {env.label}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={onGoToWorkflow}
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
            {configs.length === 0 ? "No devices registered yet." : "No devices match the current filters."}
          </p>
        )}
        {filteredConfigs.map((config) => (
          <div
            key={config.keyId}
            className="flex w-full items-center justify-between px-6 py-4 text-left transition hover:bg-slate-900/80"
          >
            <div>
              <p className="font-medium text-slate-100">{config.keyId}</p>
              <p className="text-xs text-slate-400">
                {config.environment} · {config.organisationId} · {config.clientId}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onSelectConfig(config)}
                className="rounded-md border border-indigo-400/60 px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-indigo-200 hover:bg-indigo-500/20"
              >
                Open workflow
              </button>
              <button
                type="button"
                onClick={() => onDeleteConfig(config)}
                className="rounded-md border border-rose-500/50 px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-rose-200 hover:bg-rose-500/15"
              >
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  </div>
);