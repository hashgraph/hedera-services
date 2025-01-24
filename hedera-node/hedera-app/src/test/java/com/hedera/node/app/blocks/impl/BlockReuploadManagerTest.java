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

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class BlockReuploadManagerTest {

    private BlockReuploadManager blockReuploadManager;
    private BucketUploadManager bucketUploadManager;
    private ConfigProvider mockConfigProvider;
    private CloudBucketUploader mockCloudBucketUploader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        mockConfigProvider = mock(ConfigProvider.class);
        mockCloudBucketUploader = mock(CloudBucketUploader.class);

        BlockStreamConfig mockBlockStreamConfig = mock(BlockStreamConfig.class);
        when(mockBlockStreamConfig.blockFileDir()).thenReturn(tempDir.toString());
        when(mockBlockStreamConfig.uploadRetryAttempts()).thenReturn(3);
        when(mockBlockStreamConfig.credentialsPath())
                .thenReturn(tempDir.resolve("dummy-credentials").toString());

        VersionedConfiguration mockVersionedConfig = mock(VersionedConfiguration.class);
        when(mockVersionedConfig.getConfigData(BlockStreamConfig.class)).thenReturn(mockBlockStreamConfig);
        when(mockConfigProvider.getConfiguration()).thenReturn(mockVersionedConfig);

        Files.createDirectories(tempDir); // Ensure tempDir exists

        BucketUploadManager realBucketUploadManager = new BucketUploadManager(
                Executors.newSingleThreadExecutor(), mockConfigProvider, tempDir.getFileSystem());
        bucketUploadManager = spy(realBucketUploadManager);

        blockReuploadManager = new BlockReuploadManager(
                bucketUploadManager, mockConfigProvider, tempDir.getFileSystem(), mockCloudBucketUploader);
    }

    @Test
    void testScanAndProcessFailedUploads_NoFiles() throws IOException {
        // Ensure tempDir exists but has no files
        Files.createDirectories(tempDir);
        blockReuploadManager.scanAndProcessFailedUploads();

        // Verify no interactions with the spy
        verify(bucketUploadManager, never()).uploadToProvider(any(), any());
    }

    @Test
    void testProcessFailedUpload_MaxRetryAttemptsReached() {
        Path filePath = tempDir.resolve("block1.dat");
        blockReuploadManager.failedUploads.put(filePath, 3); // Simulate max retries reached

        blockReuploadManager.scanAndProcessFailedUploads();

        // Verify no upload attempt was made
        verify(bucketUploadManager, never()).uploadToProvider(eq(filePath), eq(mockCloudBucketUploader));
    }

    @Test
    void testConcurrentFailedUploadsModification() throws InterruptedException {
        Path filePath1 = tempDir.resolve("block1.dat");
        Path filePath2 = tempDir.resolve("block2.dat");
        blockReuploadManager.failedUploads.put(filePath1, 1);
        blockReuploadManager.failedUploads.put(filePath2, 1);

        Thread thread1 = new Thread(() -> blockReuploadManager.initiateRetryUpload(filePath1));
        Thread thread2 = new Thread(() -> blockReuploadManager.initiateRetryUpload(filePath2));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Verify both files were processed
        verify(bucketUploadManager, times(2)).uploadToProvider(any(), eq(mockCloudBucketUploader));
    }

    @Test
    void testInitiateRetryUpload_RemovesFromFailedUploadsOnSuccess() throws InterruptedException {
        Path filePath = tempDir.resolve("block1.dat");
        blockReuploadManager.failedUploads.put(filePath, 1);

        doAnswer(invocation -> null).when(bucketUploadManager).uploadToProvider(filePath, mockCloudBucketUploader);

        blockReuploadManager.initiateRetryUpload(filePath);
        Thread.sleep(500); // Wait for async task to complete
        // Verify the file was removed from failedUploads
        assertThat(blockReuploadManager.failedUploads).doesNotContainKey(filePath);
    }

    @Test
    void testInitiateRetryUpload_Success() {
        Path filePath = tempDir.resolve("block1.dat");
        blockReuploadManager.initiateRetryUpload(filePath);

        // Use timeout to wait for the asynchronous interaction
        verify(bucketUploadManager, timeout(500).times(1)).uploadToProvider(eq(filePath), eq(mockCloudBucketUploader));
    }

    @Test
    void testInitiateRetryUpload_Failure() throws InterruptedException {
        // Ensure the tempDir exists
        assertThat(tempDir).isNotNull();
        assertThat(Files.exists(tempDir)).isTrue();

        // Simulate a file to upload
        Path testPath = tempDir.resolve("test-block");
        blockReuploadManager.failedUploads.put(testPath, 1); // Simulate a failed upload with 1 retry already

        // Simulate an upload failure
        doThrow(new RuntimeException("Upload failed"))
                .when(bucketUploadManager)
                .uploadToProvider(testPath, mockCloudBucketUploader);

        blockReuploadManager.initiateRetryUpload(testPath);
        Thread.sleep(500); // Wait for async task to complete

        // Verify the upload was attempted
        verify(bucketUploadManager).uploadToProvider(testPath, mockCloudBucketUploader);

        // Verify the failedUploads map was updated with the incremented retry count
        assertThat(blockReuploadManager.failedUploads).containsKey(testPath);
        assertThat(blockReuploadManager.failedUploads.get(testPath)).isEqualTo(2);
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

        // Verify both files were processed in order
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(bucketUploadManager, times(2)).uploadToProvider(pathCaptor.capture(), eq(mockCloudBucketUploader));

        // Verify the captured paths
        assertThat(pathCaptor.getAllValues()).contains(file1, file2);
    }

    @AfterEach
    void tearDown() {
        blockReuploadManager.failedUploads.clear();
    }
}
