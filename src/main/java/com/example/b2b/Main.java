package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.ConfigLoader;
import com.example.b2b.config.Environment;
import com.example.b2b.config.JksSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interactive entry point for the B2B API testing tool.
 *
 * <p>Configuration is read from {@code ./data} by default; override with
 * {@code -Db2b.dataDir=<dir>}.</p>
 */
public final class Main {

    private static final String DATA_DIR_PROPERTY = "b2b.dataDir";

    private final Scanner scanner;
    private final ConfigLoader configLoader;
    private final DeviceStore deviceStore;
    private final ObjectMapper mapper;
    private final PrintStream out;
    private final PrintStream err;

    Main(Scanner scanner, ConfigLoader configLoader, DeviceStore deviceStore, ObjectMapper mapper,
         PrintStream out, PrintStream err) {
        this.scanner = scanner;
        this.configLoader = configLoader;
        this.deviceStore = deviceStore;
        this.mapper = mapper;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        Path dataDir = Path.of(System.getProperty(DATA_DIR_PROPERTY, "data"));
        ObjectMapper mapper = new ObjectMapper();
        try (Scanner scanner = new Scanner(System.in)) {
            new Main(scanner, new ConfigLoader(dataDir), new DeviceStore(dataDir, mapper, System.err), mapper,
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
            out.println("  2. Generate Token & Test API");
            out.println("  3. Exit");
            Optional<Integer> choice = promptNumber("Select an option [1-3]: ", 1, 3);
            if (choice.isEmpty() || choice.get() == 3) {
                out.println("Goodbye.");
                return;
            }
            try {
                if (choice.get() == 1) {
                    registerDevice();
                } else {
                    generateTokenAndTestApi();
                }
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

    private void registerDevice() throws B2bClientException, InterruptedException {
        Optional<Environment> environment = selectEnvironment();
        if (environment.isEmpty()) {
            return;
        }
        AppConfig config = configLoader.load(environment.get());

        out.println();
        Optional<String> raNumber = promptText("RA number: ");
        Optional<String> deviceName = raNumber.isEmpty() ? Optional.empty() : promptText("Device name: ");
        Optional<String> otac = deviceName.isEmpty() ? Optional.empty() : promptText("OTAC (one-time activation code): ");
        if (otac.isEmpty()) {
            return;
        }

        String alias = DeviceStore.aliasFor(deviceName.get());
        Optional<DeviceMetadata> existing = deviceStore.find(environment.get(), alias);
        if (existing.isPresent() && existing.get().isRegistered()
                && !confirm("Device '" + existing.get().deviceName() + "' is already registered in "
                + environment.get().displayName() + ". Replace its key pair? [y/N]: ")) {
            out.println("Cancelled.");
            return;
        }
        if (!confirmProd(environment.get())) {
            return;
        }

        new DeviceRegistrationService(HttpSupport.newClient(), mapper, deviceStore, out, err).register(
                environment.get(), config,
                new DeviceRegistrationService.RegistrationRequest(raNumber.get(), deviceName.get(), otac.get()));
    }

    private void generateTokenAndTestApi() throws B2bClientException, InterruptedException {
        Optional<Environment> environment = selectEnvironment();
        if (environment.isEmpty()) {
            return;
        }
        AppConfig config = configLoader.load(environment.get());
        Optional<SigningKey> key = selectKey(environment.get(), config);
        if (key.isEmpty() || !confirmProd(environment.get())) {
            return;
        }
        AuthService.TokenResult token = new AuthService(mapper, out).authenticate(config, key.get());
        new ApiClient(HttpSupport.newClient(), mapper, out)
                .call(config.endpoints().targetApiEndpoint(), token.accessToken());
    }

    private Optional<Environment> selectEnvironment() {
        Environment[] environments = Environment.values();
        out.println();
        out.println("Select Environment");
        for (int i = 0; i < environments.length; i++) {
            out.println("  " + (i + 1) + ". " + environments[i].displayName());
        }
        return promptNumber("Environment [1-" + environments.length + "]: ", 1, environments.length)
                .map(i -> environments[i - 1]);
    }

    /** Lists the keys from the config followed by the devices registered in this environment. */
    private Optional<SigningKey> selectKey(Environment environment, AppConfig config) {
        JksSettings jks = config.jks();
        List<SigningKey> keys = new ArrayList<>();
        jks.keys().forEach(k -> keys.add(new SigningKey(k.name(), k.alias(), Path.of(jks.jksPath()),
                jks.jksPassword(), jks.keyPassword())));
        for (DeviceMetadata device : deviceStore.listRegistered(environment)) {
            keys.add(new SigningKey("Device '" + device.deviceName() + "' (RA " + device.raNumber() + ")",
                    device.alias(), deviceStore.keystorePath(environment, device.alias()),
                    jks.jksPassword(), jks.jksPassword()));
        }
        if (keys.isEmpty()) {
            err.println("No keys available for " + environment.displayName()
                    + ": add jks.keys to the config or register a device first.");
            return Optional.empty();
        }

        out.println();
        out.println("Available keys for " + config.environment());
        for (int i = 0; i < keys.size(); i++) {
            SigningKey k = keys.get(i);
            out.println("  " + (i + 1) + ". " + k.name() + "  [alias: " + k.alias() + ", " + k.keystorePath() + "]");
        }
        return promptNumber("Key [1-" + keys.size() + "]: ", 1, keys.size()).map(i -> keys.get(i - 1));
    }

    /** Prompts until a number in range is entered; empty when input ends. */
    private Optional<Integer> promptNumber(String prompt, int min, int max) {
        while (true) {
            Optional<String> line = readLine(prompt);
            if (line.isEmpty()) {
                return Optional.empty();
            }
            try {
                int value = Integer.parseInt(line.get());
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
            Optional<String> line = readLine(prompt);
            if (line.isEmpty() || !line.get().isEmpty()) {
                return line;
            }
            err.println("A value is required.");
        }
    }

    private boolean confirmProd(Environment environment) {
        if (environment != Environment.PROD || confirm("You are about to run against PROD. Continue? [y/N]: ")) {
            return true;
        }
        out.println("Cancelled.");
        return false;
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
