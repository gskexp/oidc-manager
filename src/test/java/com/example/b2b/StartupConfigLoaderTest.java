package com.example.b2b;

import com.example.b2b.config.StartupConfig;
import com.example.b2b.config.StartupConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StartupConfigLoaderTest {
    @TempDir Path dir;

    @Test
    void absentFileOrEmptyObjectStartsInteractiveMenu() throws Exception {
        var loader = new StartupConfigLoader(dir);
        assertEquals(StartupConfig.defaults(), loader.load());
        Files.writeString(dir.resolve("startup-config.json"), "{}");
        assertEquals(StartupConfig.defaults(), loader.load());
    }

    @Test
    void unknownActionsEnvironmentsAndMalformedFilesAreReported() throws Exception {
        var loader = new StartupConfigLoader(dir);
        for (var json : new String[]{"null", "{", "{\"action\":\"TYPO\"}", "{\"environment\":\"prod\"}"}) {
            Files.writeString(dir.resolve("startup-config.json"), json);
            assertThrows(B2bClientException.class, loader::load);
        }
    }
}
