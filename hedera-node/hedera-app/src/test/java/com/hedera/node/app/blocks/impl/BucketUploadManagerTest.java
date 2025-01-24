/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hedera.node.app.uploader.HashMismatchException;
import com.hedera.node.app.uploader.MinioBucketUploader;
import com.hedera.node.app.uploader.configs.CompleteBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.types.BucketProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BucketUploadManagerTest {
    private ExecutorService executorService;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private VersionedConfiguration versionedConfiguration;

    @Mock
    private BlockStreamConfig blockStreamConfig;

    @TempDir
    Path tempDir;

    private Path uploadedDir;
    private Path hashMismatchDir;
    private Path blockPath;
    private BucketUploadManager bucketUploadManager;

    @BeforeEach
    void setUp() throws IOException {
        // Create a fixed thread pool executor
        executorService = Executors.newFixedThreadPool(2);

        // Setup configuration mocks
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(versionedConfiguration.getConfigData(BlockStreamConfig.class)).thenReturn(blockStreamConfig);
        when(blockStreamConfig.blockFileDir()).thenReturn(tempDir.toString());

        // Create test directories
        uploadedDir = tempDir.resolve("uploaded");
        hashMismatchDir = tempDir.resolve("hashmismatch");

        // Create a test block file
        blockPath = tempDir.resolve("test.blk");
        Files.write(blockPath, "test block content".getBytes());

        // Create the manager instance with real filesystem
        bucketUploadManager = new BucketUploadManager(executorService, configProvider, tempDir.getFileSystem());

        // Verify directories were created
        assertThat(Files.exists(uploadedDir)).isTrue();
        assertThat(Files.exists(hashMismatchDir)).isTrue();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Shutdown executor service and wait for tasks to complete
        executorService.shutdown();
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    private void waitForPendingUploads() throws Exception {
        // Get the pendingUploads field from BucketUploadManager
        var pendingUploadsField = BucketUploadManager.class.getDeclaredField("pendingUploads");
        pendingUploadsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, CompletableFuture<Void>> pendingUploads =
                (ConcurrentHashMap<Path, CompletableFuture<Void>>) pendingUploadsField.get(bucketUploadManager);

        // Wait for all pending uploads to complete
        for (CompletableFuture<Void> future : pendingUploads.values()) {
            future.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testOnBucketConfigurationsUpdated() {
        // Create test configurations
        CompleteBucketConfig config1 = mock(CompleteBucketConfig.class);
        CompleteBucketConfig config2 = mock(CompleteBucketConfig.class);
        when(config1.bucketName()).thenReturn("bucket1");
        when(config1.enabled()).thenReturn(true);
        when(config2.bucketName()).thenReturn("bucket2");
        when(config2.enabled()).thenReturn(true);

        try (MockedConstruction<MinioBucketUploader> mocked = mockConstruction(MinioBucketUploader.class)) {
            // Update configurations
            bucketUploadManager.onBucketConfigurationsUpdated(List.of(config1, config2));

            // Verify that two uploaders were created
            assertThat(mocked.constructed()).hasSize(2);
        }
    }

    @Test
    void testOnBlockClosedSuccess() throws Exception {
        // Setup mock uploader
        CompleteBucketConfig config = mock(CompleteBucketConfig.class);
        when(config.bucketName()).thenReturn("test-bucket");
        when(config.enabled()).thenReturn(true);

        try (MockedConstruction<MinioBucketUploader> mocked = mockConstruction(MinioBucketUploader.class)) {
            // Update configurations to add the mock uploader
            bucketUploadManager.onBucketConfigurationsUpdated(List.of(config));

            // Get the constructed mock
            MinioBucketUploader mockUploader = mocked.constructed().get(0);

            // Trigger block closure
            bucketUploadManager.onBlockClosed(blockPath);

            // Wait for pending uploads to complete
            waitForPendingUploads();

            // Verify uploader was used
            verify(mockUploader).uploadBlock(blockPath);

            // Verify the block was processed and moved to uploaded directory
            assertThat(Files.exists(uploadedDir.resolve("test.blk"))).isTrue();
            assertThat(Files.exists(blockPath)).isFalse();
        }
    }

    @Test
    void testOnBlockClosedWithHashMismatch() throws Exception {
        // Setup mock uploader configuration
        CompleteBucketConfig config = mock(CompleteBucketConfig.class);
        when(config.bucketName()).thenReturn("test-bucket");
        when(config.enabled()).thenReturn(true);

        try (MockedConstruction<MinioBucketUploader> mocked =
                mockConstruction(MinioBucketUploader.class, (mock, context) -> {
                    // Mock the provider first
                    when(mock.getProvider()).thenReturn(BucketProvider.AWS);

                    // Then set up the exception
                    doThrow(new HashMismatchException("test", "AWS", "bucket"))
                            .when(mock)
                            .uploadBlock(any(Path.class));
                })) {
            // Update configurations
            bucketUploadManager.onBucketConfigurationsUpdated(List.of(config));

            // Get the constructed mock
            MinioBucketUploader mockUploader = mocked.constructed().get(0);

            // Trigger block closure
            bucketUploadManager.onBlockClosed(blockPath);

            // Wait for pending uploads to complete and verify exception
            try {
                waitForPendingUploads();
            } catch (Exception e) {
                // Expected - ignore the exception
            }

            // Verify uploader was used
            verify(mockUploader).uploadBlock(blockPath);

            // Give a small delay for file operations to complete
            Thread.sleep(500);

            // Debug output
            System.out.println("Block path exists: " + Files.exists(blockPath));
            System.out.println("Hash mismatch path exists: " + Files.exists(hashMismatchDir.resolve("test.blk")));
            if (Files.exists(hashMismatchDir)) {
                System.out.println("Contents of hashmismatch directory: "
                        + Files.list(hashMismatchDir)
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .toList());
            }

            // Verify the block was processed and moved to hashmismatch directory
            assertThat(Files.exists(hashMismatchDir.resolve("test.blk"))).isTrue();
            assertThat(Files.exists(blockPath)).isFalse();
        }
    }

    @Test
    void testOnBlockClosedWithNoUploaders() throws Exception {
        // Trigger block closure with no uploaders configured
        bucketUploadManager.onBlockClosed(blockPath);

        // Wait for pending uploads to complete
        waitForPendingUploads();

        // Verify the block was processed but no uploads attempted and file remains in place
        assertThat(Files.exists(blockPath)).isTrue();
        assertThat(Files.exists(uploadedDir.resolve("test.blk"))).isFalse();
        assertThat(Files.exists(hashMismatchDir.resolve("test.blk"))).isFalse();
    }

    @Test
    void testOnBlockClosedWithInvalidPath() throws Exception {
        // Create a non-existent path
        Path invalidPath = tempDir.resolve("nonexistent.blk");

        // Trigger block closure with invalid path
        bucketUploadManager.onBlockClosed(invalidPath);

        // Wait for pending uploads to complete
        waitForPendingUploads();

        // Verify no processing occurred
        assertThat(Files.exists(uploadedDir.resolve("nonexistent.blk"))).isFalse();
        assertThat(Files.exists(hashMismatchDir.resolve("nonexistent.blk"))).isFalse();
    }
}
