/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.blocks;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockRetentionManagerTest {
    @TempDir
    private Path uploadedDir;

    private BlockRetentionManager blockRetentionManager;

    private static final int AWAIT_SECONDS_TIMEOUT = 60;

    @Test
    void testCleanupShouldNotRunUntilScheduleTime() throws IOException {
        final long retentionPeriodMs = 10;
        final long cleanupPeriodMs = 100;
        blockRetentionManager = new BlockRetentionManager(
                uploadedDir, Duration.ofMillis(retentionPeriodMs), Duration.ofMillis(cleanupPeriodMs), 4);
        final Runnable cleanupTask = () -> blockRetentionManager.startCleanup();
        blockRetentionManager.scheduleRepeating(cleanupTask);

        final var blockFileName = "file1" + BlockRetentionManager.BLOCK_FILE_EXTENSION;

        createTestFile(blockFileName);

        await().atLeast(2 * retentionPeriodMs, TimeUnit.MILLISECONDS);
        assertTrue(Files.exists(uploadedDir.resolve(blockFileName)), blockFileName + " should not have been deleted");

        await().atMost(AWAIT_SECONDS_TIMEOUT, TimeUnit.SECONDS)
                .until(() -> !Files.exists(uploadedDir.resolve(blockFileName)));
        assertFalse(Files.exists(uploadedDir.resolve(blockFileName)), blockFileName + " should have been deleted");
    }

    @Test
    void testScheduleCleanup() throws IOException {
        final long retentionPeriodMs = 20;
        final long cleanupPeriodMs = 10;
        blockRetentionManager = new BlockRetentionManager(
                uploadedDir, Duration.ofMillis(retentionPeriodMs), Duration.ofMillis(cleanupPeriodMs), 4);
        final Runnable cleanupTask = () -> blockRetentionManager.startCleanup();
        blockRetentionManager.scheduleRepeating(cleanupTask);

        final var blockFileName = "file1" + BlockRetentionManager.BLOCK_FILE_EXTENSION;
        final var nonBlockFileName = "file2";
        final var compressedBlockFileName = "file3" + BlockRetentionManager.BLOCK_FILE_EXTENSION_GZ;

        createTestFile(blockFileName);
        createTestFile(nonBlockFileName);
        createTestFile(compressedBlockFileName);

        await().atMost(AWAIT_SECONDS_TIMEOUT, TimeUnit.SECONDS)
                .until(() -> !Files.exists(uploadedDir.resolve(blockFileName))
                        && !Files.exists(uploadedDir.resolve(compressedBlockFileName)));

        assertFalse(Files.exists(uploadedDir.resolve(blockFileName)), blockFileName + " should have been deleted");
        assertTrue(
                Files.exists(uploadedDir.resolve(nonBlockFileName)),
                nonBlockFileName + " should not have been deleted");
        assertFalse(
                Files.exists(uploadedDir.resolve(compressedBlockFileName)),
                compressedBlockFileName + " should have been deleted");

        final var anotherBlockFileName = "file4" + BlockRetentionManager.BLOCK_FILE_EXTENSION;
        createTestFile(anotherBlockFileName);

        await().atMost(AWAIT_SECONDS_TIMEOUT, TimeUnit.SECONDS)
                .until(() -> !Files.exists(uploadedDir.resolve(anotherBlockFileName)));

        assertFalse(
                Files.exists(uploadedDir.resolve(anotherBlockFileName)),
                anotherBlockFileName + " should have been deleted");
    }

    @Test
    void testShutdown() {
        final long retentionPeriodMs = 20;
        final long cleanupPeriodMs = 10;
        blockRetentionManager = new BlockRetentionManager(
                uploadedDir, Duration.ofMillis(retentionPeriodMs), Duration.ofMillis(cleanupPeriodMs), 4);

        blockRetentionManager.shutdown();

        assertThrows(RejectedExecutionException.class, () -> blockRetentionManager.scheduleRepeating(() -> {}));
        assertThrows(RejectedExecutionException.class, () -> blockRetentionManager.startCleanup());
    }

    private void createTestFile(final String fileName) throws IOException {
        uploadedDir.resolve(fileName).toFile().createNewFile();
    }
}
