import React, { useState } from "react";
import { ENVIRONMENTS } from "../shared/environments.js";
import { LandingView } from "./components/LandingView.jsx";
import { WorkflowView } from "./components/WorkflowView.jsx";
import { useConfigs } from "./hooks/useConfigs.js";
import { useWorkflow } from "./hooks/useWorkflow.js";

const App = () => {
  const [view, setView] = useState("landing");
  const [error, setError] = useState("");

  const {
    configs,
    filteredConfigs,
    loadingConfigs,
    loadConfigs,
    searchTerm,
    setSearchTerm,
    envFilter,
    setEnvFilter
  } = useConfigs({ setError });

  const {
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
  } = useWorkflow({
    configs,
    loadingConfigs,
    setError,
    setView,
    loadConfigs
  });

  const goToLanding = () => {
    resetWorkflow();
    setError("");
    setView("landing");
  };

  const goToWorkflow = () => {
    setError("");
    setView("workflow");
  };

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
              onClick={goToLanding}
              className={`rounded-md px-3 py-1.5 ${
                view === "landing" ? "bg-indigo-500 text-white" : "text-slate-300 hover:bg-slate-900/60"
              }`}
            >
              Landing
            </button>
            <button
              type="button"
              onClick={goToWorkflow}
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

        {view === "landing" ? (
          <LandingView
            environments={ENVIRONMENTS}
            loadingConfigs={loadingConfigs}
            configs={configs}
            filteredConfigs={filteredConfigs}
            searchTerm={searchTerm}
            envFilter={envFilter}
            setSearchTerm={setSearchTerm}
            setEnvFilter={setEnvFilter}
            isSubmitting={isSubmitting}
            onRegister={handleRegister}
            onSelectConfig={handleSelectConfig}
            onGoToWorkflow={goToWorkflow}
          />
        ) : (
          <WorkflowView
            workflowState={workflowState}
            selectedEnvironment={selectedEnvironment}
            isSubmitting={isSubmitting}
            onAuthorizeRedirect={handleAuthorizeRedirect}
            onB2BToken={handleB2BToken}
            onUserToken={handleUserToken}
            onFinalExchange={handleFinalExchange}
            onClearRedirect={handleClearRedirect}
            onClearWorkflowState={handleClearWorkflowState}
            onBackToLanding={goToLanding}
            onUpdateField={updateField}
            onCopy={handleCopy}
            onRegenerateStateNonce={regenerateStateNonce}
          />
        )}
      </main>
    </div>
  );
};

export default App;