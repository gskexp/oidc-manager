package com.example.b2b;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/** Opens attended sign-in in the user's browser, with a printed URL as fallback. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserLauncher {
    static void open(URI uri) {
        log.info("Open this URL to sign in: {}", uri);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            log.warn("Unable to open browser automatically. Use the URL above: {}", e.getMessage());
        }
    }
}
