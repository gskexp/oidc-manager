import React from "react";
import { formatDateTime } from "../utils/formatDateTime.js";

export const WorkflowView = ({
  workflowState,
  selectedEnvironment,
  isSubmitting,
  onAuthorizeRedirect,
  onB2BToken,
  onUserToken,
  onFinalExchange,
  onClearRedirect,
  onClearWorkflowState,
  onBackToLanding,
  onUpdateField,
  onCopy,
  onRegenerateStateNonce
}) => (
  <div className="space-y-6">
    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div>
        <h2 className="text-lg font-semibold text-slate-100">OIDC Workflow</h2>
        <p className="text-xs text-slate-400">
          Simulate attended and unattended flows using the selected device configuration.
        </p>
      </div>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onClearWorkflowState}
          className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-900/60"
        >
          Clear workflow state
        </button>
        <button
          type="button"
          onClick={onBackToLanding}
          className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-900/60"
        >
          Back to landing
        </button>
      </div>
    </div>

    <section className="grid gap-4 md:grid-cols-[minmax(0,1.2fr)_minmax(0,0.9fr)]">
      <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 text-sm">
        <h3 className="text-sm font-semibold text-slate-100">Configuration details</h3>
        <dl className="mt-3 grid gap-2 text-xs text-slate-300">
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">Environment</dt>
            <dd className="text-slate-100">
              {(selectedEnvironment?.label ?? workflowState.environment) || "Not set"}
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">Issuer</dt>
            <dd className="text-right text-slate-100">
              {selectedEnvironment?.issuer ?? "Not available"}
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">Key ID</dt>
            <dd className="text-slate-100">{workflowState.keyId || "Not set"}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">Organisation ID</dt>
            <dd className="text-right text-slate-100">
              {workflowState.organisationId || "Not set"}
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">OTAC</dt>
            <dd className="text-right text-slate-100">{workflowState.otac || "Not set"}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">Client ID</dt>
            <dd className="text-right text-slate-100">
              {workflowState.clientId || "Not set"}
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="uppercase tracking-wide text-slate-500">Audience</dt>
            <dd className="text-right text-slate-100">
              {workflowState.audience || "Not set"}
            </dd>
          </div>
        </dl>
      </div>

      <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 text-sm">
        <h3 className="text-sm font-semibold text-slate-100">Redirect parameters</h3>
        <div className="mt-3 space-y-3">
          <div>
            <div className="flex items-center justify-between text-xs">
              <label className="text-slate-300" htmlFor="redirect-state-display">
                State (captured)
              </label>
              <button
                type="button"
                onClick={() => onCopy(workflowState.redirectState)}
                className="text-indigo-300 hover:text-indigo-200"
                disabled={!workflowState.redirectState}
              >
                Copy
              </button>
            </div>
            <textarea
              id="redirect-state-display"
              className="mt-1 h-14 w-full resize-none rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-xs text-slate-100"
              readOnly
              value={workflowState.redirectState}
            />
          </div>
          <div>
            <div className="flex items-center justify-between text-xs">
              <label className="text-slate-300" htmlFor="auth-code-display">
                Authorization code
              </label>
              <button
                type="button"
                onClick={() => onCopy(workflowState.authCode)}
                className="text-indigo-300 hover:text-indigo-200"
                disabled={!workflowState.authCode}
              >
                Copy
              </button>
            </div>
            <textarea
              id="auth-code-display"
              className="mt-1 h-14 w-full resize-none rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-xs text-slate-100"
              readOnly
              value={workflowState.authCode}
            />
          </div>
          <button
            type="button"
            onClick={onClearRedirect}
            className="w-full rounded-md border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-900/60"
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
          onClick={onB2BToken}
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
                onClick={() => onCopy(workflowState.b2bAssertion)}
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
                onClick={() => onCopy(workflowState.b2bToken)}
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
              onChange={(event) => onUpdateField("attendedClientId", event.target.value)}
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
              onChange={(event) => onUpdateField("attendedClientSecret", event.target.value)}
              className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
              placeholder="********"
            />
          </div>

          <div className="rounded border border-slate-800 bg-slate-900/70 px-4 py-4">
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-100">Request parameters</p>
              <button
                type="button"
                onClick={onRegenerateStateNonce}
                className="text-xs text-indigo-300 hover:text-indigo-200"
              >
                Regenerate
              </button>
            </div>
            <p className="mt-1 text-xs text-slate-400">
              These values are included when exchanging for a user token. Defaults are 10-character alphanumeric strings.
            </p>
            <label
              className="mt-3 block text-xs font-medium uppercase tracking-wide text-slate-400"
              htmlFor="manual-state-input"
            >
              State
            </label>
            <input
              id="manual-state-input"
              value={workflowState.userStateInput}
              onChange={(event) => onUpdateField("userStateInput", event.target.value)}
              className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
            />

            <label
              className="mt-3 block text-xs font-medium uppercase tracking-wide text-slate-400"
              htmlFor="nonce-input"
            >
              Nonce
            </label>
            <input
              id="nonce-input"
              value={workflowState.nonceInput}
              onChange={(event) => onUpdateField("nonceInput", event.target.value)}
              className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
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
              onClick={onAuthorizeRedirect}
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
            <p className="mt-2 text-xs text-slate-500">Note: this navigation will reload the SPA when returning.</p>
          </div>

          <div className="rounded border border-slate-800 bg-slate-900/60 px-4 py-4">
            <p className="font-medium text-slate-100">Part 2 · Exchange authorization code</p>
            <p className="mt-1 text-xs text-slate-400">
              Use the captured authorization code to request a user token from the backend.
            </p>
            <button
              type="button"
              onClick={onUserToken}
              className="mt-3 rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-400 disabled:opacity-60"
              disabled={
                isSubmitting ||
                !workflowState.keyId ||
                !workflowState.authCode ||
                !workflowState.attendedClientId ||
                !workflowState.attendedClientSecret ||
                !workflowState.userStateInput ||
                !workflowState.nonceInput
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
                  onClick={() => onCopy(workflowState.userToken)}
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
          onClick={onFinalExchange}
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
              onClick={() => onCopy(workflowState.finalToken)}
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