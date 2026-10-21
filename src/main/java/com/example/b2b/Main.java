package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.ConfigLoader;
import com.example.b2b.config.Environment;
import com.example.b2b.config.KeyEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
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
    private final ObjectMapper mapper;
    private final PrintStream out;
    private final PrintStream err;

    Main(Scanner scanner, ConfigLoader configLoader, ObjectMapper mapper, PrintStream out, PrintStream err) {
        this.scanner = scanner;
        this.configLoader = configLoader;
        this.mapper = mapper;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        Path dataDir = Path.of(System.getProperty(DATA_DIR_PROPERTY, "data"));
        try (Scanner scanner = new Scanner(System.in)) {
            new Main(scanner, new ConfigLoader(dataDir), new ObjectMapper(), System.out, System.err).run();
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
            runAction(choice.get());
        }
    }

    private void runAction(int action) {
        try {
            Optional<Environment> environment = selectEnvironment();
            if (environment.isEmpty()) {
                return;
            }
            AppConfig config = configLoader.load(environment.get());
            Optional<KeyEntry> key = selectKey(config);
            if (key.isEmpty()) {
                return;
            }
            if (environment.get() == Environment.PROD && !confirm("You are about to run against PROD. Continue? [y/N]: ")) {
                out.println("Cancelled.");
                return;
            }

            if (action == 1) {
                new DeviceRegistrationService(HttpSupport.newClient(), mapper, out, err).register(config, key.get());
            } else {
                AuthService.TokenResult token = new AuthService(mapper, out).authenticate(config, key.get());
                new ApiClient(HttpSupport.newClient(), mapper, out)
                        .call(config.endpoints().targetApiEndpoint(), token.accessToken());
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

    private Optional<KeyEntry> selectKey(AppConfig config) {
        List<KeyEntry> keys = config.jks().keys();
        out.println();
        out.println("Available keys for " + config.environment() + " (" + config.jks().jksPath() + ")");
        for (int i = 0; i < keys.size(); i++) {
            out.println("  " + (i + 1) + ". " + keys.get(i).name() + "  [alias: " + keys.get(i).alias() + "]");
        }
        return promptNumber("Key [1-" + keys.size() + "]: ", 1, keys.size()).map(i -> keys.get(i - 1));
    }

    /** Prompts until a number in range is entered; empty when input ends. */
    private Optional<Integer> promptNumber(String prompt, int min, int max) {
        while (true) {
            out.print(prompt);
            out.flush();
            if (!scanner.hasNextLine()) {
                return Optional.empty();
            }
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) {
                    return Optional.of(value);
                }
            } catch (NumberFormatException ignored) {
                // fall through to the error message
            }
            err.println("Please enter a number between " + min + " and " + max + ".");
        }
    }

    private boolean confirm(String prompt) {
        out.print(prompt);
        out.flush();
        return scanner.hasNextLine() && scanner.nextLine().trim().toLowerCase().startsWith("y");
    }
}
