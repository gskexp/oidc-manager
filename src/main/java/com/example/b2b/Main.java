package com.example.b2b;

import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

import com.example.b2b.config.ConfigLoader;
import com.example.b2b.config.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interactive entry point for the B2B API testing tool.
 *
 * <p>Configuration is read from {@code ./data} by default; override with
 * {@code -Db2b.dataDir=<dir>}.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Main {

    private static final String DATA_DIR_PROPERTY = "b2b.dataDir";

    private final Scanner scanner;
    private final Path dataDir;
    private final ConfigLoader configLoader;
    private final DeviceStore deviceStore;
    private final ObjectMapper mapper;
    private final PrintStream out;
    private final PrintStream err;

    public static void main(String[] args) {
        var dataDir = Path.of(System.getProperty(DATA_DIR_PROPERTY, "data"));
        var mapper = new ObjectMapper();
        try (var scanner = new Scanner(System.in)) {
            new Main(scanner, dataDir, new ConfigLoader(dataDir), new DeviceStore(dataDir, mapper), mapper,
                    System.out, System.err).run();
        }
    }

    /** Runs the menu loop until the user exits or input ends. */
    void run() {
        out.println("=============================================");
        out.println("   B2B API Testing & Authentication Tool");
        out.println("=============================================");
        while (true) {
            out.println();
            out.println("Main Menu");
            out.println("  1. Register Device");
            out.println("  2. User Sign-in & Generate Token");
            out.println("  3. Exit");
            out.println("  4. Generate Unattended B2B Token");
            out.println("  5. Generate Final Token (Full Flow)");
            var choice = promptNumber("Select an option [1-5]: ", 1, 5);
            if (choice.isEmpty() || choice.get() == 3) {
                out.println("Goodbye.");
                return;
            }
            try {
                runOperation(choice.get());
            } catch (B2bClientException e) {
                err.println("[ERROR] " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                err.println("[ERROR] Operation interrupted.");
            } catch (IllegalArgumentException e) {
                // Typically a malformed endpoint URI in the configuration.
                err.println("[ERROR] Invalid value in configuration: " + e.getMessage());
            }
        }
    }

    private void runOperation(int choice) throws B2bClientException, InterruptedException {
        switch (choice) {
            case 1 -> registerDevice();
            case 2 -> generateUserToken();
            case 4 -> generateDeviceToken(false);
            case 5 -> generateDeviceToken(true);
            default -> throw new IllegalArgumentException("Unknown menu option: " + choice);
        }
    }

    private void registerDevice() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) {
            return;
        }
        var config = configLoader.load(environment.get());

        out.println();
        var raNumber = promptText("RA number: ");
        var deviceName = raNumber.isEmpty() ? Optional.<String>empty() : promptText("Device name: ");
        var otac = deviceName.isEmpty() ? Optional.<String>empty() : promptText("OTAC (one-time activation code): ");
        if (otac.isEmpty()) {
            return;
        }

        var alias = DeviceStore.aliasFor(deviceName.get());
        var existing = deviceStore.find(environment.get(), alias);
        if (existing.isPresent() && existing.get().isRegistered()
                && !confirm("Device '" + existing.get().deviceName() + "' is already registered in "
                + environment.get().displayName() + ". Replace its key pair? [y/N]: ")) {
            out.println("Cancelled.");
            return;
        }

        new DeviceRegistrationService(HttpSupport.newClient(), mapper, deviceStore).register(
                environment.get(), config,
                new DeviceRegistrationService.RegistrationRequest(raNumber.get(), deviceName.get(), otac.get()));
    }

    private void generateUserToken() throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var config = configLoader.load(environment.get());
        var service = new UserAuthService(HttpSupport.newClient(), mapper, dataDir, BrowserLauncher::open);
        var token = service.authenticate(config);
        out.println("User access token: " + token.accessToken());
        out.println("Token type: " + token.tokenType() + ", expires in: " + token.expiresIn() + " seconds");
    }

    private void generateDeviceToken(boolean fullFlow) throws B2bClientException, InterruptedException {
        var environment = selectEnvironment();
        if (environment.isEmpty()) return;
        var config = configLoader.load(environment.get());
        var key = selectDeviceKey(environment.get());
        if (key.isEmpty()) return;
        var exchanges = new TokenExchangeService(new TokenClient(HttpSupport.newClient(), mapper));
        var signer = new JwtSigner(mapper);
        TokenResponse token;
        if (fullFlow) {
            var userAuth = new UserAuthService(HttpSupport.newClient(), mapper, dataDir, BrowserLauncher::open);
            token = new TokenFlowService(exchanges, userAuth, signer).authenticate(config, key.get());
        } else {
            var assertion = signer.createClientAssertion(key.get().privateKey(), key.get().alias(),
                    config.clientId(), config.tokenEndpoint());
            token = exchanges.unattended(config, assertion);
        }
        out.println((fullFlow ? "Final access token: " : "Unattended B2B access token: ") + token.accessToken());
    }

    private Optional<SigningKey> selectDeviceKey(Environment environment) {
        var devices = deviceStore.listRegistered(environment);
        if (devices.isEmpty()) {
            err.println("No registered devices for " + environment.displayName() + ". Register a device first.");
            return Optional.empty();
        }
        out.println("Select registered device");
        for (var index = 0; index < devices.size(); index++) {
            out.println("  " + (index + 1) + ". " + devices.get(index).deviceName());
        }
        return promptNumber("Device [1-" + devices.size() + "]: ", 1, devices.size()).map(index -> {
            var device = devices.get(index - 1);
            return new SigningKey(device.deviceName(), device.alias(), deviceStore.signingKeyPath(environment, device.alias()));
        });
    }

    private Optional<Environment> selectEnvironment() throws B2bClientException {
        var environments = Environment.values();
        out.println();
        out.println("Select Environment");
        for (var i = 0; i < environments.length; i++) {
            out.println("  " + (i + 1) + ". " + configLoader.load(environments[i]).label());
        }
        return promptNumber("Environment [1-" + environments.length + "]: ", 1, environments.length)
                .map(i -> environments[i - 1]);
    }

    /** Prompts until a number in range is entered; empty when input ends. */
    private Optional<Integer> promptNumber(String prompt, int min, int max) {
        while (true) {
            var line = readLine(prompt);
            if (line.isEmpty()) {
                return Optional.empty();
            }
            try {
                var value = Integer.parseInt(line.get());
                if (value >= min && value <= max) {
                    return Optional.of(value);
                }
            } catch (NumberFormatException ignored) {
                // fall through to the error message
            }
            err.println("Please enter a number between " + min + " and " + max + ".");
        }
    }

    /** Prompts until a non-blank value is entered; empty when input ends. */
    private Optional<String> promptText(String prompt) {
        while (true) {
            var line = readLine(prompt);
            if (line.isEmpty() || !line.get().isEmpty()) {
                return line;
            }
            err.println("A value is required.");
        }
    }

    private boolean confirm(String prompt) {
        return readLine(prompt).map(s -> s.toLowerCase(Locale.ROOT).startsWith("y")).orElse(false);
    }

    private Optional<String> readLine(String prompt) {
        out.print(prompt);
        out.flush();
        return scanner.hasNextLine() ? Optional.of(scanner.nextLine().trim()) : Optional.empty();
    }
}
