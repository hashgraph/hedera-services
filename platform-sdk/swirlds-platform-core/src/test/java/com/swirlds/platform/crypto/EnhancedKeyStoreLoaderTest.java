package com.swirlds.platform.crypto;

import com.swirlds.config.api.Configuration;
import com.swirlds.platform.test.fixtures.resource.ResourceLoader;
import org.assertj.core.api.Assertions;
import org.bouncycastle.crypto.digests.ParallelHash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

public class EnhancedKeyStoreLoaderTest {

    @TempDir
    static Path testDataDirectory;

    @BeforeAll
    static void setup() throws IOException {
        final ResourceLoader<EnhancedKeyStoreLoaderTest> loader = new ResourceLoader<>(EnhancedKeyStoreLoaderTest.class);
        final Path tempDir = loader.loadDirectory("com/swirlds/platform/crypto/EnhancedKeyStoreLoader");

        Files.move(tempDir, testDataDirectory, REPLACE_EXISTING);
    }

    @Test
    @DisplayName("Validate Test Data")
    void validateTestDataDirectory() {
        assertThat(testDataDirectory).exists().isDirectory().isReadable();
        assertThat(testDataDirectory.resolve("legacy-valid")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("legacy-invalid-case-1")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("legacy-invalid-case-2")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("hybrid-valid")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("hybrid-invalid-case-1")).exists().isEmptyDirectory();
        assertThat(testDataDirectory.resolve("hybrid-invalid-case-2")).exists().isEmptyDirectory();
        assertThat(testDataDirectory.resolve("enhanced-valid")).exists().isEmptyDirectory();
        assertThat(testDataDirectory.resolve("enhanced-invalid")).exists().isEmptyDirectory();
        assertThat(testDataDirectory.resolve("legacy-valid").resolve("public.pfx")).exists().isNotEmptyFile();
        assertThat(testDataDirectory.resolve("config.txt")).exists().isNotEmptyFile();
        assertThat(testDataDirectory.resolve("settings.txt")).exists().isNotEmptyFile();
    }

    private Configuration testConfiguration(final Path keyDirectory) {

    }
}
