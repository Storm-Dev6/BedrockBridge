package io.bedrockbridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.common.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {
    @TempDir Path directory;

    @Test
    void loadsAndValidatesCompleteConfiguration() throws IOException {
        Path file = write("""
                bridge.application-name=BedrockBridge
                bridge.bind-address=0.0.0.0
                bridge.bind-port=19132
                bridge.upstream-address=127.0.0.1
                bridge.upstream-port=25565
                bridge.maximum-sessions=100
                bridge.scheduler-threads=2
                bridge.development-mode=false
                """);
        BridgeConfiguration configuration = new DefaultConfigurationValidator()
                .validate(new PropertiesConfigurationLoader().load(file));
        assertEquals(19132, configuration.bindPort());
        assertEquals(100, configuration.maximumSessions());
    }

    @Test
    void rejectsUnknownProperty() throws IOException {
        Path file = write("bridge.unknown=value\n");
        assertThrows(
                ConfigurationException.class,
                () -> new PropertiesConfigurationLoader().load(file));
    }

    @Test
    void rejectsEndpointLoopInProduction() {
        BridgeConfiguration configuration = new BridgeConfiguration(
                "bridge", "localhost", 19132, "localhost", 19132, 10, 1, false);
        assertThrows(
                ConfigurationException.class,
                () -> new DefaultConfigurationValidator().validate(configuration));
    }

    private Path write(String contents) throws IOException {
        Path path = directory.resolve("bridge.properties");
        return Files.writeString(path, contents);
    }
}
