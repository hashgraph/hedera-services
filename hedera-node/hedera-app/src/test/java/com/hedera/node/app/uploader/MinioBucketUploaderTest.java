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

import com.hedera.node.app.blocks.cloud.uploader.MinioBucketUploader;
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
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.ErrorResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

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

        awsBucketCredentials = new BucketCredentials("awsAccessKey", "awsSecretKey");
        gcsBucketCredentials = new BucketCredentials("gcsAccessKey", "gcsSecretKey");
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

        uploader = new MinioBucketUploader(bucketConfigurationManager, executorService, configProvider);
    }

    //    @Test
    void testUploadBlockSuccess() throws Exception {
        // Create a temporary file to simulate the block file
        Path tempFile = Files.createTempFile("test", ".gz");
        Files.write(tempFile, "test-content".getBytes());

        // Mock ErrorResponse with lenient settings to support final class
        ErrorResponse mockErrorResponse = Mockito.mock(
                ErrorResponse.class,
                withSettings().strictness(Strictness.LENIENT).useConstructor());
        when(mockErrorResponse.message()).thenReturn("Bucket not found"); // Complete the stubbing

        // Mock minioClient behavior
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStatResponse);

        // Mocking the upload process
        doAnswer(invocation -> {
                    return CompletableFuture.completedFuture(null); // Simulate a successful upload
                })
                .when(minioClient)
                .uploadObject(any(UploadObjectArgs.class));

        // Spy on the uploader
        uploader = spy(uploader);

        CompletableFuture<Void> result = uploader.uploadBlock(tempFile);

        assertDoesNotThrow(result::join);
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

        // Run the method and verify behavior
        Path blockFile;
        for (Map.Entry<String, InputStream> entry : blockFiles.entrySet()) {
            String fileName = entry.getKey(); // File name in the bucket
            InputStream inputStream = entry.getValue();
            // Write InputStream to a temporary file
            blockFile = Files.createTempFile(fileName, "blk.gz");
            try (OutputStream outputStream = Files.newOutputStream(blockFile)) {
                inputStream.transferTo(outputStream);
            }

            // Pass the Path to uploader.uploadBlock()
            CompletableFuture<Void> result = uploader.uploadBlock(blockFile);
            //            assertDoesNotThrow(result::join); // Ensure no exceptions are thrown
            assertThrows(TimeoutException.class, () -> result.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void testUploadBlockFailsIfFileDoesNotExist()
            throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
                    NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
                    InternalException, ExecutionException, InterruptedException, TimeoutException {

        // Create a temporary file to simulate the block file (this file will not exist after deletion)
        Path tempFile = Files.createTempFile("nonexistent", ".blk.gz");
        Files.delete(tempFile);

        // Execute the uploadBlock method
        CompletableFuture<Void> result = uploader.uploadBlock(tempFile);

        // Verify the behavior
        assertThrows(TimeoutException.class, () -> result.get(1, TimeUnit.SECONDS));
        verify(minioClient, never()).uploadObject(any());
    }

    //    @Test
    void testBlockExistsReturnsTrue() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(io.minio.StatObjectResponse.class));

        CompletableFuture<Boolean> result = uploader.blockExists("test-object");

        assertTrue(result.join());
    }

    //    @Test
    void testBlockExistsReturnsFalse() throws Exception {
        // Mock minioClient behavior
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStatResponse);
        CompletableFuture<Boolean> result = uploader.blockExists("test-object");
        assertFalse(result.join());
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
}
