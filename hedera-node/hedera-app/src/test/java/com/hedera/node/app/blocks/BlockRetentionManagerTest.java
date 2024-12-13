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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockRetentionManagerTest {
    @TempDir
    private Path uploadedDir;

    private BlockRetentionManager blockRetentionManager;

    @BeforeEach
    void setUp() throws IOException {
        createTestFile("file1.blk");
        createTestFile("file2");
        createTestFile("file3.blk.gz");
    }

    @Test
    void testScheduleCleanup() throws Exception {
        final long retentionPeriodMs = 10;
        blockRetentionManager = new BlockRetentionManager(uploadedDir, Duration.ofMillis(retentionPeriodMs));
        final Runnable cleanupTask = () -> blockRetentionManager.startCleanup();
        blockRetentionManager.scheduleRepeating(cleanupTask);

        Thread.sleep(retentionPeriodMs * 2);
        assertFalse(Files.exists(uploadedDir.resolve("file1")), "File1 should have been deleted");
        assertTrue(Files.exists(uploadedDir.resolve("file2")), "File2 should not have been deleted");
        assertFalse(Files.exists(uploadedDir.resolve("file3")), "File3 should have been deleted");

        createTestFile("file4.blk");

        Thread.sleep(retentionPeriodMs * 2);
        assertFalse(Files.exists(uploadedDir.resolve("file4")), "File4 should have been deleted");
    }

    private void createTestFile(String fileName) throws IOException {
        uploadedDir.resolve(fileName).toFile().createNewFile();
    }
}
