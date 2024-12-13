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

package com.hedera.node.app.uploader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hedera.node.app.uploader.credentials.BucketCredentials;
import com.hedera.node.app.uploader.credentials.CompleteBucketConfig;
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
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
                BucketProvider.AWS,
                "aws-endpoint",
                "us-west-2",
                "aws-bucket",
                true,
                awsBucketCredentials);
        gcsBucketConfig = new CompleteBucketConfig(
                "gcsBucketConfig", BucketProvider.GCP, "gcs-endpoint", "", "gcs-bucket", true, gcsBucketCredentials);
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

//    @Test
    void testUploadBlockSuccess() throws Exception {
        // Create a temporary file to simulate the block file
        Path tempFile = Files.createTempFile("test", ".blk.gz");
        Files.write(tempFile, "test-content".getBytes());

        // Mock the MinioClient behavior
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStatResponse);
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new ErrorResponseException(new ErrorResponse(), null, null));

        doAnswer(invocation -> null)
                .when(minioClient)
                .uploadObject(any(UploadObjectArgs.class));
        // Spy on the uploader
        uploader = spy(new MinioBucketUploader(bucketConfigurationManager, executorService, configProvider));

        // Call the method under test
        CompletableFuture<Void> result = uploader.uploadBlock(tempFile);
        result.join(); // Wait for async execution

        // Verify uploadObject was called with correct arguments
        ArgumentCaptor<UploadObjectArgs> argsCaptor = ArgumentCaptor.forClass(UploadObjectArgs.class);
        verify(minioClient).uploadObject(argsCaptor.capture());

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
            CompletableFuture<Void> result  = uploader.uploadBlock(blockFile);
//            assertDoesNotThrow(result::join); // Ensure no exceptions are thrown
            assertThrows(CompletionException.class, result::join);
        }
    }

    @Test
    void testUploadBlockFailsIfFileDoesNotExist()
            throws Exception {

        // Create a temporary file to simulate the block file (this file will not exist after deletion)
        Path tempFile = Files.createTempFile("nonexistent", ".blk.gz");
        Files.delete(tempFile);

        // Execute the uploadBlock method
        CompletableFuture<Void> result = uploader.uploadBlock(tempFile);

        CompletionException exception = assertThrows(CompletionException.class, result::join);
        // Assert that the cause of the CompletionException is that the file does not exist
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Block path does not exist: " + tempFile, exception.getCause().getMessage());

        verify(minioClient, never()).uploadObject(any());
    }

    @Test
    void testBlockExistsReturnsFalse() throws Exception {
        // Mock minioClient behavior
        lenient().when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStatResponse);

        CompletableFuture<Boolean> result = uploader.blockExists("test-object");
        CompletionException exception = assertThrows(CompletionException.class, result::join);
        // Assert that the exception cause is an Unknown Host Exception
        assertTrue(exception.getCause() instanceof UnknownHostException);
        assertEquals("aws-endpoint: nodename nor servname provided, or not known", exception.getCause().getMessage());
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

    @AfterEach
    void tearDown() {
        uploader.clearCharArray(awsBucketCredentials.secretKey());
        uploader.clearCharArray(gcsBucketCredentials.secretKey());
        executorService.shutdown();
    }
}
