package com.swirlds.base.utility;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.swirlds.base.utility.FileSystem.waitForPathPresence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Validates the behavior of the {@link FileSystem} utility class.
 */
class FileSystemTest {
    @TempDir(cleanup = CleanupMode.ALWAYS)
    Path tempDir;

    private static ExecutorService executorService;

    @BeforeAll
    static void setup() {
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterAll
    static void teardown() {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Test
    void delayedFileCreationResolves() {
        final CountDownLatch latch = new CountDownLatch(1);
        final Path targetFile = tempDir.resolve("test.txt");

        final Future<?> filePresence = executorService.submit(() -> {
            await().atMost(20, TimeUnit.SECONDS).until(() -> waitForPathPresence(targetFile, 10, 2_000));
        });

        final Future<?> fileCreation = executorService.submit(() -> {
            await().until(() -> {
                latch.await();
                TimeUnit.SECONDS.sleep(1);
                Files.write(targetFile, new byte[]{0x01, 0x02, 0x03});
                return Files.exists(targetFile);
            });
        });

        latch.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> fileCreation.isDone() && filePresence.isDone());
    }

    @Test
    void missingFileTimeout() {
        final Path targetFile = tempDir.resolve("test.txt");
        assertThat(waitForPathPresence(targetFile, 5, 0)).isFalse();
    }

    @Test
    void zeroLengthFileTimeout() throws IOException {
        final Path targetFile = tempDir.resolve("test.txt");
        Files.createFile(targetFile);

        assertThat(waitForPathPresence(targetFile, 5, 0)).isFalse();
    }

    @Test
    void nullPathShouldThrow() {
        assertThatThrownBy(() -> waitForPathPresence(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("path must not be null");
    }

    @Test
    void zeroMaxAttemptsShouldThrow() {
        assertThatThrownBy(() -> waitForPathPresence(tempDir, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maximum number of attempts must be greater than zero (0)");
    }

    @Test
    void negativeDelayShouldThrow() {
        assertThatThrownBy(() -> waitForPathPresence(tempDir, 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The delay must be greater than or equal to zero (0)");
    }

    @Test
    void delayedDirectoryCreationResolves() {
        final CountDownLatch latch = new CountDownLatch(1);
        final Path targetDir = tempDir.resolve("test-dir");

        final Future<?> filePresence = executorService.submit(() -> {
            await().atMost(20, TimeUnit.SECONDS).until(() -> waitForPathPresence(targetDir, 10, 2_000));
        });

        final Future<?> fileCreation = executorService.submit(() -> {
            await().until(() -> {
                latch.await();
                TimeUnit.SECONDS.sleep(1);
                Files.createDirectory(targetDir);
                return Files.exists(targetDir);
            });
        });

        latch.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> fileCreation.isDone() && filePresence.isDone());
    }

}
