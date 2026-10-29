package com.example.b2b.config;

/** Optional startup state and reusable selections, separate from environment configuration. */
public record StartupConfig(Action action, String environment, String deviceAlias, Activation activation,
                            Tokens tokens, boolean exitAfterAction) {
    public enum Action { MAIN_MENU, TOKEN_STEPS, ACTIVATE_DEVICE, FULL_FLOW, UNATTENDED_TOKEN, ATTENDED_TOKEN, FINAL_TOKEN }
    public record Activation(String raNumber, String deviceName, String otac) { }
    public record Tokens(String userToken, String b2bToken) { }

    public StartupConfig {
        action = action == null ? Action.MAIN_MENU : action;
        activation = activation == null ? new Activation(null, null, null) : activation;
        tokens = tokens == null ? new Tokens(null, null) : tokens;
    }

    public static StartupConfig defaults() {
        return new StartupConfig(null, null, null, null, null, false);
    }
}
