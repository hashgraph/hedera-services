/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.hedera.node.app.uploader.CloudBucketUploader;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.BlockStreamConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockReuploadManagerTest {

    private BlockReuploadManager blockReuploadManager;
    private BucketUploadManager mockBucketUploadManager;
    private ConfigProvider mockConfigProvider;
    private CloudBucketUploader mockCloudBucketUploader;

    @TempDir
    Path tempDir;

    //    @BeforeEach
    //    void setUp() {
    //        mockBucketUploadManager = mock(BucketUploadManager.class);
    //        mockConfigProvider = mock(ConfigProvider.class);
    //        mockCloudBucketUploader = mock(CloudBucketUploader.class);
    //
    //        BlockStreamConfig mockBlockStreamConfig = mock(BlockStreamConfig.class);
    //        when(mockBlockStreamConfig.blockFileDir()).thenReturn(tempDir.toString());
    //        when(mockBlockStreamConfig.uploadRetryAttempts()).thenReturn(3);
    //
    //        VersionedConfiguration mockVersionedConfig = mock(VersionedConfiguration.class);
    //        when(mockVersionedConfig.getConfigData(BlockStreamConfig.class)).thenReturn(mockBlockStreamConfig);
    //        when(mockConfigProvider.getConfiguration()).thenReturn(mockVersionedConfig);
    //
    //        // Create a real BucketUploadManager with a real executor
    //        ExecutorService executorService = Executors.newSingleThreadExecutor();
    //        mockBucketUploadManager = new BucketUploadManager(executorService, mockConfigProvider,
    // tempDir.getFileSystem());
    //
    //        blockReuploadManager = new BlockReuploadManager(
    //                mockBucketUploadManager,
    //                mockConfigProvider,
    //                tempDir.getFileSystem(),
    //                mockCloudBucketUploader);
    //    }

    @BeforeEach
    void setUp() throws IOException {
        mockConfigProvider = mock(ConfigProvider.class);
        mockCloudBucketUploader = mock(CloudBucketUploader.class);

        BlockStreamConfig mockBlockStreamConfig = mock(BlockStreamConfig.class);
        when(mockBlockStreamConfig.blockFileDir()).thenReturn(tempDir.toString());
        when(mockBlockStreamConfig.uploadRetryAttempts()).thenReturn(3);

        VersionedConfiguration mockVersionedConfig = mock(VersionedConfiguration.class);
        when(mockVersionedConfig.getConfigData(BlockStreamConfig.class)).thenReturn(mockBlockStreamConfig);
        when(mockConfigProvider.getConfiguration()).thenReturn(mockVersionedConfig);

        Files.createDirectories(tempDir); // Ensure tempDir exists

        mockBucketUploadManager = new BucketUploadManager(
                Executors.newSingleThreadExecutor(), mockConfigProvider, tempDir.getFileSystem());

        blockReuploadManager = new BlockReuploadManager(
                mockBucketUploadManager, mockConfigProvider, tempDir.getFileSystem(), mockCloudBucketUploader);
    }

    @Test
    void testScanAndProcessFailedUploads_NoFiles() {
        blockReuploadManager.scanAndProcessFailedUploads();
        verifyNoInteractions(mockBucketUploadManager, mockCloudBucketUploader);
    }

    @Test
    void testScanAndProcessFailedUploads_WithFiles() throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("block1.dat"));
        Path file2 = Files.createFile(tempDir.resolve("block2.dat"));
        ConcurrentHashMap<Path, Integer> failedUploads = new ConcurrentHashMap<>();
        failedUploads.put(file1, 1);
        failedUploads.put(file2, 2);
        blockReuploadManager.failedUploads.putAll(failedUploads);

        blockReuploadManager.scanAndProcessFailedUploads();
        verify(mockBucketUploadManager, times(1)).uploadToProvider(eq(file1), eq(mockCloudBucketUploader));
        verifyNoInteractions(mockCloudBucketUploader);
    }

    @Test
    void testInitiateRetryUpload_Success() {
        Path filePath = tempDir.resolve("block1.dat");
        blockReuploadManager.initiateRetryUpload(filePath);
        verify(mockBucketUploadManager, times(1)).uploadToProvider(eq(filePath), eq(mockCloudBucketUploader));
    }

    @Test
    void testInitiateRetryUpload_Failure() {
        assertNotNull(tempDir);
        assertTrue(Files.exists(tempDir));
        Path testPath = tempDir.resolve("test-block");
        //        failedUploads.put(testPath, 1); // Simulate a failed upload

        doThrow(new RuntimeException("Upload failed"))
                .when(mockBucketUploadManager)
                .uploadToProvider(testPath, mockCloudBucketUploader);

        blockReuploadManager.initiateRetryUpload(testPath);

        verify(mockBucketUploadManager).uploadToProvider(testPath, mockCloudBucketUploader);
    }

    @Test
    void testOnNodeRestart() throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("block1.dat"));
        Path file2 = Files.createFile(tempDir.resolve("block2.dat"));
        ConcurrentHashMap<Path, Integer> failedUploads = new ConcurrentHashMap<>();
        failedUploads.put(file1, 1);
        failedUploads.put(file2, 2);
        blockReuploadManager.failedUploads.putAll(failedUploads);
        blockReuploadManager.onNodeRestart();

        verify(mockBucketUploadManager, times(1)).uploadToProvider(eq(file1), eq(mockCloudBucketUploader));
        verifyNoInteractions(mockCloudBucketUploader);
    }
}
