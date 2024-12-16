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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockRetentionManagerTest {
    @TempDir
    private Path uploadedDir;

    @Mock
    private BlockStreamBucketMetrics blockStreamBucketMetrics;

    private BlockRetentionManager blockRetentionManager;

    @Test
    void testCleanupShouldNotRunUntilScheduleTime() throws Exception {
        final long retentionPeriodMs = 10;
        final long cleanupPeriodMs = 100;
        blockRetentionManager = new BlockRetentionManager(
                uploadedDir,
                Duration.ofMillis(retentionPeriodMs),
                Duration.ofMillis(cleanupPeriodMs),
                4,
                blockStreamBucketMetrics);
        final Runnable cleanupTask = () -> blockRetentionManager.startCleanup();
        blockRetentionManager.scheduleRepeating(cleanupTask);

        final var blockFileName = "file1" + BlockRetentionManager.BLOCK_FILE_EXTENSION;

        createTestFile(blockFileName);

        Thread.sleep(retentionPeriodMs * 2);
        assertTrue(Files.exists(uploadedDir.resolve(blockFileName)), blockFileName + " should not have been deleted");
    }

    @Test
    void testScheduleCleanup() throws Exception {
        final long retentionPeriodMs = 20;
        final long cleanupPeriodMs = 10;
        blockRetentionManager = new BlockRetentionManager(
                uploadedDir,
                Duration.ofMillis(retentionPeriodMs),
                Duration.ofMillis(cleanupPeriodMs),
                4,
                blockStreamBucketMetrics);
        final Runnable cleanupTask = () -> blockRetentionManager.startCleanup();
        blockRetentionManager.scheduleRepeating(cleanupTask);

        final var blockFileName = "file1" + BlockRetentionManager.BLOCK_FILE_EXTENSION;
        final var nonBlockFileName = "file2";
        final var compressedBlockFileName = "file3" + BlockRetentionManager.BLOCK_FILE_EXTENSION_GZ;

        createTestFile(blockFileName);
        createTestFile(nonBlockFileName);
        createTestFile(compressedBlockFileName);

        Thread.sleep(retentionPeriodMs * 2);
        assertFalse(Files.exists(uploadedDir.resolve(blockFileName)), blockFileName + " should have been deleted");
        assertTrue(
                Files.exists(uploadedDir.resolve(nonBlockFileName)),
                nonBlockFileName + " should not have been deleted");
        assertFalse(
                Files.exists(uploadedDir.resolve(compressedBlockFileName)),
                compressedBlockFileName + " should have been deleted");

        final var anotherBlockFileName = "file4" + BlockRetentionManager.BLOCK_FILE_EXTENSION;
        createTestFile(anotherBlockFileName);

        Thread.sleep(retentionPeriodMs * 2);
        assertFalse(
                Files.exists(uploadedDir.resolve(anotherBlockFileName)),
                anotherBlockFileName + " should have been deleted");
    }

    private void createTestFile(final String fileName) throws IOException {
        uploadedDir.resolve(fileName).toFile().createNewFile();
    }
}
