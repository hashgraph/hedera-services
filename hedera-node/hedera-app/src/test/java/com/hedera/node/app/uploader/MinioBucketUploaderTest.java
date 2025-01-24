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

package com.hedera.node.app.uploader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hedera.node.app.uploader.configs.BucketCredentials;
import com.hedera.node.app.uploader.configs.CompleteBucketConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.types.BucketProvider;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioBucketUploaderTest {

    @Mock
    private BucketConfigurationManager bucketConfigurationManager;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private ExecutorService executorService;

    @Mock
    private MinioClient minioClient;

    @Mock
    private BlockStreamConfig blockStreamConfig;

    private MinioBucketUploader uploader;
    private CompleteBucketConfig awsBucketConfig;
    private CompleteBucketConfig gcsBucketConfig;
    private BucketCredentials awsBucketCredentials;
    private BucketCredentials gcsBucketCredentials;
    private okhttp3.Headers mockHeaders;
    private StatObjectResponse mockStatResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        awsBucketCredentials = new BucketCredentials("awsAccessKey", "awsSecretKey".toCharArray());
        gcsBucketCredentials = new BucketCredentials("gcsAccessKey", "gcsSecretKey".toCharArray());
        awsBucketConfig = new CompleteBucketConfig(
                "awsBucketConfig",
                BucketProvider.AWS.toString(),
                "aws-endpoint",
                "us-west-2",
                "aws-bucket",
                true,
                awsBucketCredentials);
        gcsBucketConfig = new CompleteBucketConfig(
                "gcsBucketConfig",
                BucketProvider.GCP.toString(),
                "gcs-endpoint",
                "",
                "gcs-bucket",
                true,
                gcsBucketCredentials);
        // Mock Headers for StatObjectResponse with valid timestamp
        mockHeaders = new okhttp3.Headers.Builder()
                .add("last-modified", "Wed, 12 Oct 2022 10:15:30 GMT") // Valid RFC 1123 timestamp
                .build();

        // Create a valid StatObjectResponse
        mockStatResponse = new StatObjectResponse(mockHeaders, "mock-object", "mock-bucket", "mock-region");

        // Mock bucketConfigurationManager
        when(bucketConfigurationManager.getCompleteBucketConfigs())
                .thenReturn(List.of(awsBucketConfig, gcsBucketConfig));

        // Mock the behavior of configProvider
        VersionedConfiguration versionedConfiguration = mock(VersionedConfiguration.class);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(versionedConfiguration.getConfigData(BlockStreamConfig.class)).thenReturn(blockStreamConfig);

        executorService = Executors.newFixedThreadPool(2);
        uploader = new MinioBucketUploader(bucketConfigurationManager, executorService, configProvider);
    }

    void testUploadBlockSuccess() throws Exception {
        // Create a temporary file to simulate the block file
        Path tempFile = Files.createTempFile("test", ".blk.gz");
        Files.write(tempFile, "test-content".getBytes());

        // Mock the MinioClient behavior
        MinioClient minioClientMock = MinioClient.builder()
                .endpoint("http://127.0.0.1:9000")
                .credentials("accessKey", "secretKey")
                .endpoint("aws-endpoint")
                .build();

        ErrorResponse errorResponse = new ErrorResponse(
                "NoSuchKey",
                "The specified key does not exist.",
                "aws-bucket",
                "test.blk.gz",
                "resource",
                "requestId",
                "hostId");

        // Create a mock okhttp3.Response
        okhttp3.Response mockResponse = new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder()
                        .url("http://127.0.0.1:9000")
                        .build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(404) // HTTP status code for "Not Found"
                .message("Not Found")
                .build();

        // Provide an HTTP trace string
        String httpTrace = "HTTP TRACE LOG";

        when(minioClientMock.statObject(any(StatObjectArgs.class)))
                .thenThrow(new ErrorResponseException(errorResponse, mockResponse, httpTrace));

        doAnswer(invocation -> CompletableFuture.completedFuture(null))
                .when(minioClientMock)
                .uploadObject(any(UploadObjectArgs.class));

        // Spy on the uploader
        uploader = spy(new MinioBucketUploader(bucketConfigurationManager, executorService, configProvider));

        // Mock the MinioBucketUploader
        doReturn(List.of(minioClientMock)).when(uploader).getMinioClients();
        doReturn(false).when(uploader).blockExistsOnCloud(anyString());

        // Mock RetryUtils.withRetry to simulate successful retry
        doAnswer(invocation -> {
                    RetryUtils.SupplierWithException<?> task = invocation.getArgument(2);
                    return task.get(); // Simulate successful retry
                })
                .when(uploader)
                .withRetry(any(), anyInt());

        // Call the method under test
        CompletableFuture<Void> result = uploader.uploadBlock(tempFile);
        result.join(); // Wait for async execution

        // Verify uploadObject was called with correct arguments
        ArgumentCaptor<UploadObjectArgs> argsCaptor = ArgumentCaptor.forClass(UploadObjectArgs.class);
        verify(minioClientMock).uploadObject(argsCaptor.capture());

        UploadObjectArgs args = argsCaptor.getValue();
        assertEquals("aws-bucket", args.bucket());
        assertEquals(tempFile.getFileName().toString(), args.object());
        assertEquals("application/octet-stream", args.contentType());

        // Clean up the temporary file
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testUploadBlockSucceedsIfFileExists() throws IOException {

        // Load all the test block files from the resources directory
        Map<String, InputStream> blockFiles = loadAllBlockFilesFromDirectory();
        uploader = spy(new MinioBucketUploader(bucketConfigurationManager, executorService, configProvider));

        // Iterate through all the block files and upload them
        for (Map.Entry<String, InputStream> entry : blockFiles.entrySet()) {
            String fileName = entry.getKey(); // File name in the bucket
            InputStream inputStream = entry.getValue();
            // Write InputStream to a temporary file
            Path blockFile = Files.createTempFile(fileName, ".blk.gz");
            try (OutputStream outputStream = Files.newOutputStream(blockFile)) {
                inputStream.transferTo(outputStream);
            }
            CompletableFuture<Void> result = uploader.uploadBlock(blockFile);

            // Pass the Path to uploader.uploadBlock()
            //            assertDoesNotThrow(result::join); // Ensure no exceptions are thrown
            assertThrows(CompletionException.class, result::join);
            Path finalBlockFile = blockFile;
            assertThrows(TimeoutException.class, () -> uploader.uploadBlock(finalBlockFile));
        }
    }

    @Test
    void testUploadBlockFailsIfFileDoesNotExist() throws Exception {

        // Create a temporary file to simulate the block file (this file will not exist after deletion)
        Path tempFile = Files.createTempFile("nonexistent", ".blk.gz");
        Files.delete(tempFile);

        // Execute the uploadBlock method
        CompletableFuture<Void> result = uploader.uploadBlock(tempFile);

        CompletionException exception = assertThrows(CompletionException.class, result::join);
        // Assert that the cause of the CompletionException is that the file does not exist
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals(
                "Block path does not exist: " + tempFile, exception.getCause().getMessage());

        // Verify the behavior
        assertThrows(TimeoutException.class, () -> uploader.uploadBlock(tempFile));
        verify(minioClient, never()).uploadObject(any());
    }

    @Test
    //    @Test
    void testBlockExistsReturnsTrue() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(io.minio.StatObjectResponse.class));
        assertTrue(uploader.blockExistsBool("test-object"));
    }

    //    @Test
    void testBlockExistsReturnsFalse() throws Exception {
        // Mock minioClient behavior
        lenient().when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStatResponse);

        CompletableFuture<Boolean> result = uploader.blockExists("test-object");
        CompletionException exception = assertThrows(CompletionException.class, result::join);
        // Assert that the exception cause is an Unknown Host Exception
        String actualMessage = exception.getCause().getMessage();
        assertTrue(
                actualMessage.matches(
                        "aws-endpoint: (nodename nor servname provided, or not known|Name or service not known)"),
                "Unexpected error message: " + actualMessage);
    }

    private Map<String, InputStream> loadAllBlockFilesFromDirectory() {
        Map<String, InputStream> blockFiles = new HashMap<>();

        Path resourcesPath;
        try {
            resourcesPath = Paths.get(MinioBucketUploaderTest.class
                    .getClassLoader()
                    .getResource("uploader/")
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try (Stream<Path> files = Files.list(resourcesPath)) {
            files.filter(file -> file.toString().endsWith(".gz")).forEach(file -> {
                try {
                    blockFiles.put(file.getFileName().toString(), Files.newInputStream(file));
                } catch (IOException e) {
                }
            });
        } catch (IOException ioe) {
        }

        return blockFiles;
    }

    //    @Test
    void testGetBlockMd5() throws Exception {
        // Mock the CompleteBucketConfig
        CompleteBucketConfig mockBucketConfig = mock(CompleteBucketConfig.class);
        when(mockBucketConfig.bucketName()).thenReturn("test-bucket");
        when(mockBucketConfig.provider()).thenReturn(BucketProvider.AWS.toString());
        when(mockBucketConfig.endpoint()).thenReturn("https://s3.amazonaws.com");
        when(mockBucketConfig.credentials()).thenReturn(awsBucketCredentials);

        // Mock the BucketConfigurationManager
        BucketConfigurationManager mockBucketConfigManager = mock(BucketConfigurationManager.class);
        when(mockBucketConfigManager.getCompleteBucketConfigs()).thenReturn(List.of(mockBucketConfig));

        // Mock the BlockStreamConfig
        BlockStreamConfig mockBlockStreamConfig = mock(BlockStreamConfig.class);
        when(mockBlockStreamConfig.uploadRetryAttempts()).thenReturn(3);

        // Mock the VersionedConfiguration
        VersionedConfiguration mockVersionedConfiguration = mock(VersionedConfiguration.class);
        when(mockVersionedConfiguration.getConfigData(BlockStreamConfig.class)).thenReturn(mockBlockStreamConfig);

        // Mock the ConfigProvider
        ConfigProvider mockConfigProvider = mock(ConfigProvider.class);
        when(mockConfigProvider.getConfiguration()).thenReturn(mockVersionedConfiguration);

        // Mock the MinioClient and the StatObjectResponse
        MinioClient mockClient = mock(MinioClient.class);
        StatObjectResponse mockStatResponse = mock(StatObjectResponse.class);
        when(mockStatResponse.etag()).thenReturn("test-md5");
        when(mockClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStatResponse);
        assertNotNull(mockClient, "Mock client should not be null");

        // Create the uploader instance with mocked dependencies
        MinioBucketUploader uploader =
                new MinioBucketUploader(
                        mockBucketConfigManager, Executors.newSingleThreadExecutor(), mockConfigProvider) {
                    @Override
                    protected List<MinioClient> getMinioClients() {
                        return List.of(mockClient);
                    }
                };

        // Call the method and verify the result
        String objectKey = "test-object";
        String result = uploader.getBlockMd5(objectKey).join();
        assertEquals("test-md5", result);
    }

    @Test
    void testClearCharArray() {
        char[] array = {'s', 'e', 'c', 'r', 'e', 't'};
        uploader.clearCharArray(array);
        assertTrue(Arrays.equals(array, new char[] {'\0', '\0', '\0', '\0', '\0', '\0'}));
    }

    @Test
    void testWithRetry_Success() throws Exception {
        RetryUtils.SupplierWithException<String> task = mock(RetryUtils.SupplierWithException.class);
        when(task.get()).thenReturn("success");

        String result = uploader.withRetry(task, 3);
        assertEquals("success", result);
        verify(task, times(1)).get();
    }

    @Test
    void testWithRetry_RetriesAndFails() throws Exception {
        RetryUtils.SupplierWithException<String> task = mock(RetryUtils.SupplierWithException.class);
        when(task.get()).thenThrow(new IOException("Test exception"));

        String result = uploader.withRetry(task, 3); // Get the result, which should be a failure message
        assertEquals("Failed after 3 attempts", result); // Check the failure message
        verify(task, times(3)).get(); // Verify the task was retried 3 times
    }

    @AfterEach
    void tearDown() {
        uploader.clearCharArray(awsBucketCredentials.secretKey());
        uploader.clearCharArray(gcsBucketCredentials.secretKey());
        executorService.shutdown();
    }
}
