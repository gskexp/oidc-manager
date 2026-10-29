package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.ConfigLoader;
import com.example.b2b.config.Environment;
import com.example.b2b.config.StartupConfig;
import com.example.b2b.config.StartupConfig.Action;
import com.example.b2b.config.StartupConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;

/** CLI orchestration for device activation, individual token steps and the full flow. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Main {
    private final ConsoleInput input;
    private final Path dataDir;
    private final ConfigLoader configLoader;
    private final DeviceStore deviceStore;
    private final ObjectMapper mapper;
    private final PrintStream out;
    private final PrintStream err;
    private final Consumer<URI> openBrowser;
    private final Map<String, String> userTokens = new HashMap<>();
    private final Map<String, String> b2bTokens = new HashMap<>();
    private StartupConfig startup = StartupConfig.defaults();

    public static void main(String[] args) {
        var dataDir = Path.of(System.getProperty("b2b.dataDir", "data"));
        var mapper = new ObjectMapper();
        try (var scanner = new Scanner(System.in)) {
            var input = new ConsoleInput(scanner, System.out, System.err);
            new Main(input, dataDir, new ConfigLoader(dataDir), new DeviceStore(dataDir, mapper), mapper,
                    System.out, System.err, BrowserLauncher::open).run();
        }
    }

    void run() {
        out.println("B2B API Testing & Authentication Tool");
        try {
            startup = new StartupConfigLoader(dataDir).load();
        } catch (B2bClientException e) {
            err.println("[ERROR] " + e.getMessage());
            return;
        }
        if (startup.action() != Action.MAIN_MENU) {
            execute(startup.action());
            if (startup.exitAfterAction() || Thread.currentThread().isInterrupted()) return;
        }
        mainMenu();
    }

    private void mainMenu() {
        while (!Thread.currentThread().isInterrupted()) {
            out.println("\nMain Menu");
            out.println("  1. Activate Device");
            out.println("  2. Select Device & Run Full Flow");
            out.println("  3. Check Individual Token Steps");
            out.println("  4. Exit");
            var choice = input.number("Select an option [1-4]: ", 1, 4);
            if (choice.isEmpty() || choice.get() == 4) {
                out.println("Goodbye.");
                return;
            }
            execute(switch (choice.get()) {
                case 1 -> Action.ACTIVATE_DEVICE;
                case 2 -> Action.FULL_FLOW;
                default -> Action.TOKEN_STEPS;
            });
        }
    }

    private void tokenStepsMenu() {
        while (!Thread.currentThread().isInterrupted()) {
            out.println("\nIndividual Token Steps");
            out.println("  1. Unattended B2B Token");
            out.println("  2. Attended User Token");
            out.println("  3. Final Token Exchange Only");
            out.println("  4. Back");
            var choice = input.number("Step [1-4]: ", 1, 4);
            if (choice.isEmpty() || choice.get() == 4) return;
            execute(switch (choice.get()) {
                case 1 -> Action.UNATTENDED_TOKEN;
                case 2 -> Action.ATTENDED_TOKEN;
                default -> Action.FINAL_TOKEN;
            });
        }
    }

    private void execute(Action action) {
        try {
            switch (action) {
                case ACTIVATE_DEVICE -> activateDevice();
                case FULL_FLOW -> fullFlow();
                case TOKEN_STEPS -> tokenStepsMenu();
                case UNATTENDED_TOKEN -> unattendedToken();
                case ATTENDED_TOKEN -> attendedToken();
                case FINAL_TOKEN -> finalToken();
                default -> throw new IllegalArgumentException("Unsupported startup action: " + action);
            }
        } catch (B2bClientException | IllegalArgumentException e) {
            err.println("[ERROR] " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("[ERROR] Operation interrupted.");
        }
    }

    private void activateDevice() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var config = configLoader.load(environment.get());
        var defaults = startup.activation();
        var raNumber = input.text("RA number: ", defaults.raNumber());
        if (raNumber.isEmpty()) return;
        var deviceName = input.text("Device name: ", defaults.deviceName());
        if (deviceName.isEmpty()) return;
        var otac = input.text("OTAC (one-time activation code): ", defaults.otac());
        if (otac.isEmpty()) return;
        var existing = deviceStore.find(environment.get(), DeviceStore.aliasFor(deviceName.get()));
        if (existing.isPresent() && existing.get().isRegistered() && !input.confirm("Device '"
                + existing.get().deviceName() + "' is already activated. Replace its key pair? [y/N]: ")) {
            out.println("Cancelled.");
            return;
        }
        new DeviceRegistrationService(HttpSupport.newClient(), mapper, deviceStore).register(environment.get(), config,
                new DeviceRegistrationService.RegistrationRequest(raNumber.get(), deviceName.get(), otac.get()));
    }

    private void fullFlow() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var config = configLoader.load(environment.get());
        var key = selectDeviceKey(environment.get());
        if (key.isEmpty()) return;
        var flow = new TokenFlowService(exchanges(), userAuth(), new JwtSigner(mapper));
        var token = flow.authenticate(config, key.get());
        out.println("Final access token: " + token.accessToken());
    }

    private void unattendedToken() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var config = configLoader.load(environment.get());
        var key = selectDeviceKey(environment.get());
        if (key.isEmpty()) return;
        var token = exchanges().unattended(config, assertion(config, key.get()));
        b2bTokens.put(deviceTokenKey(environment.get(), key.get()), token.accessToken());
        out.println("Unattended B2B access token: " + token.accessToken());
    }

    private void attendedToken() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var token = userAuth().authenticate(configLoader.load(environment.get()));
        userTokens.put(environment.get().id(), token.accessToken());
        out.println("User access token: " + token.accessToken());
    }

    private void finalToken() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var config = configLoader.load(environment.get());
        var key = selectDeviceKey(environment.get());
        if (key.isEmpty()) return;
        var userToken = input.text("User access token: ",
                userTokens.getOrDefault(environment.get().id(), startup.tokens().userToken()));
        if (userToken.isEmpty()) return;
        var b2bToken = input.text("B2B access token: ",
                b2bTokens.getOrDefault(deviceTokenKey(environment.get(), key.get()), startup.tokens().b2bToken()));
        if (b2bToken.isEmpty()) return;
        var token = exchanges().finalToken(config, userToken.get(), b2bToken.get(), assertion(config, key.get()));
        out.println("Final access token: " + token.accessToken());
    }

    private TokenExchangeService exchanges() {
        return new TokenExchangeService(new TokenClient(HttpSupport.newClient(), mapper));
    }

    private UserAuthService userAuth() {
        return new UserAuthService(HttpSupport.newClient(), mapper, dataDir, openBrowser);
    }

    private String assertion(AppConfig config, SigningKey key) throws B2bClientException {
        return new JwtSigner(mapper).createClientAssertion(key.privateKey(), key.alias(), config.clientId(), config.tokenEndpoint());
    }

    private static String deviceTokenKey(Environment environment, SigningKey key) {
        return environment.id() + "/" + key.alias();
    }

    private Optional<SigningKey> selectDeviceKey(Environment environment) throws B2bClientException {
        var devices = deviceStore.listRegistered(environment);
        if (devices.isEmpty()) throw new B2bClientException("No activated devices for " + environment.displayName() + ". Activate a device first.");
        if (hasValue(startup.deviceAlias())) {
            var device = devices.stream().filter(item -> item.alias().equalsIgnoreCase(startup.deviceAlias().trim()))
                    .findFirst().orElseThrow(() -> new B2bClientException("No activated device with alias '"
                            + startup.deviceAlias() + "' in " + environment.displayName()));
            return Optional.of(signingKey(environment, device));
        }
        out.println("Select activated device");
        for (var index = 0; index < devices.size(); index++) out.println("  " + (index + 1) + ". " + devices.get(index).deviceName());
        return input.number("Device [1-" + devices.size() + "]: ", 1, devices.size())
                .map(index -> signingKey(environment, devices.get(index - 1)));
    }

    private SigningKey signingKey(Environment environment, DeviceMetadata device) {
        return new SigningKey(device.deviceName(), device.alias(), deviceStore.signingKeyPath(environment, device.alias()));
    }

    private Optional<Environment> selectEnvironment() throws B2bClientException {
        var environments = Environment.values();
        if (hasValue(startup.environment())) {
            return Arrays.stream(environments).filter(env -> env.id().equalsIgnoreCase(startup.environment().trim())).findFirst();
        }
        out.println("Select Environment");
        for (var index = 0; index < environments.length; index++) {
            out.println("  " + (index + 1) + ". " + configLoader.load(environments[index]).label());
        }
        return input.number("Environment [1-" + environments.length + "]: ", 1, environments.length)
                .map(index -> environments[index - 1]);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
