package com.example.b2b;

import lombok.RequiredArgsConstructor;

import java.io.PrintStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/** Interactive input with optional configured defaults and consistent EOF handling. */
@RequiredArgsConstructor
final class ConsoleInput {
    private final Scanner scanner;
    private final PrintStream out;
    private final PrintStream err;

    Optional<Integer> number(String prompt, int min, int max) {
        while (true) {
            var line = line(prompt);
            if (line.isEmpty()) return Optional.empty();
            try {
                var value = Integer.parseInt(line.get());
                if (value >= min && value <= max) return Optional.of(value);
            } catch (NumberFormatException ignored) { }
            err.println("Please enter a number between " + min + " and " + max + ".");
        }
    }

    Optional<String> text(String prompt, String configured) {
        if (configured != null && !configured.isBlank()) return Optional.of(configured);
        while (true) {
            var line = line(prompt);
            if (line.isEmpty() || !line.get().isEmpty()) return line;
            err.println("A value is required.");
        }
    }

    boolean confirm(String prompt) {
        return line(prompt).map(value -> value.toLowerCase(Locale.ROOT).startsWith("y")).orElse(false);
    }

    private Optional<String> line(String prompt) {
        out.print(prompt);
        out.flush();
        return scanner.hasNextLine() ? Optional.of(scanner.nextLine().trim()) : Optional.empty();
    }
}
