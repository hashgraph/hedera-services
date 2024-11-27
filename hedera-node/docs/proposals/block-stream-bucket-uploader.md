# Block Stream Bucket Upload Design Document

## Summary

In order for consensus nodes to upload block files to public cloud buckets, this proposal extends the existing block streaming capabilities to support uploading blocks to cloud storage buckets (AWS S3 and GCP Cloud Storage) while maintaining local copies for redundancy.

|      Metadata      | Entities    |
|--------------------|-------------|
| Designers          | Derek Riley |
| Functional Impacts | Services    |
| Related Proposals  | None        |
| HIPS               | None        |

---

## Table of Contents
1. [Summary](#summary)
2. [Context](#context)
3. [Dependencies](#dependencies)
4. [Core Components](#core-components)
   1. [Extended BlockStreamWriterMode](#1-extended-blockstreamwritermode)
   2. [Configuration](#2-configuration)
        - [Network Properties](#network-properties)
        - [Credentials Configuration](#credentials-configuration)
        - [Configuration Loading](#configuration-loading-and-updates)
   3. [BlockMetadata Handling](#3-blockmetadata-handling)
   4. [Cloud Storage Implementation](#4-cloud-storage-implementation)
   5. [BucketUploadManager](#5-bucketuploadmanager)
   6. [BlockRecoveryManager](#6-blockrecoverymanager)
   7. [BlockRetentionManager](#7-blockretentionmanager)
   8. [Metrics](#8-metrics)
5. [Flow Sequences](#flow-sequences)
   1. [Happy Path](#happy-path)
   2. [Error Handling](#error-handling)
   3. [Hash Mismatch](#hash-mismatch)
   4. [Recovery Handling](#recovery-handling)
6. [Testing Strategy](#testing-strategy)

### Context

The following image depicts how multiple consensus nodes will be attempting to upload to the same bucket. Only the first node to upload the block will succeed, and the others will check if the block is already available. If the block is available, the node will check if the block is the same by comparing the MD5 hash. If the hashes are different, the block will be kept on the local disk, and an alert will be triggered. If the upload fails, the block will be kept on the local disk, and an alert will be triggered.

<img src="./Phase1.png" alt="Phase1"></img>

### Dependencies

This proposal uses the MinIO Java SDK to provide a unified interface for cloud storage operations, supporting both AWS S3 and GCP Cloud Storage:

```xml
<dependencies>
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.9</version>
    </dependency>
</dependencies>
```

#### Rationale for Using MinIO

- **Unified API**: MinIO provides a single S3-compatible API that works with multiple cloud storage providers, simplifying the integration process.
- **Flexibility**: Supports both AWS S3 and GCP Cloud Storage, allowing for easy configuration and management of multiple cloud providers.
- **Security**: Credentials are managed locally, reducing the risk of exposure through network properties.
- **Scalability**: MinIO's client is designed to handle large-scale storage operations efficiently, making it suitable for high-throughput environments.
- **Community and Support**: MinIO is a widely used open-source project with a strong community and commercial support options.

By leveraging the MinIO SDK, we can streamline the process of uploading block files to cloud storage, ensuring compatibility and performance across different providers.

## Core Components

### 1. Extended BlockStreamWriterMode

```java
public enum BlockStreamWriterMode {
    GRPC,
    FILE,
    FILE_AND_BUCKET
}
```

### 2. Configuration

The configuration is split into two parts:
1. Network properties that can be updated at runtime
2. Secure credentials stored locally

#### Network Properties
Network-wide properties that can be dynamically updated via file 0.0.121:

```java
@ConfigData("blockStream")
public record BlockStreamConfig(
    // Basic configuration
    @ConfigProperty(defaultValue = "FILE_AND_BUCKET") @NetworkProperty BlockStreamWriterMode writerMode,
    @ConfigProperty(defaultValue = "3") @NetworkProperty int uploadRetryAttempts,
    @ConfigProperty(defaultValue = "168") @NetworkProperty int localRetentionHours,
    @ConfigProperty(defaultValue = "data/config/bucket-credentials.json") @NetworkProperty String credentialsPath,
    
    // Bucket configurations with default AWS and GCP public buckets
    @ConfigProperty(defaultValue = """
        [
            {
                "name": "default-aws-bucket",
                "provider": "aws",
                "endpoint": "https://s3.amazonaws.com",
                "region": "us-east-1",
                "bucketName": "hedera-mainnet-blocks"
            },
            {
                "name": "default-gcp-bucket",
                "provider": "gcp",
                "endpoint": "https://storage.googleapis.com",
                "region": "",
                "bucketName": "hedera-mainnet-blocks"
            }
        ]
        """) @NetworkProperty List<BucketNetworkConfig> buckets
) {}

public record BucketNetworkConfig(
    @ConfigProperty String name,
    @ConfigProperty String provider,  // "aws" or "gcp"
    @ConfigProperty String endpoint,
    @ConfigProperty String region,    // required for AWS only
    @ConfigProperty String bucketName
) {
    public BucketNetworkConfig {
        Objects.requireNonNull(name, "Bucket name cannot be null");
        Objects.requireNonNull(provider, "Provider cannot be null");
        Objects.requireNonNull(endpoint, "Endpoint cannot be null");
        Objects.requireNonNull(bucketName, "Bucket name cannot be null");
        
        if ("aws".equals(provider)) {
            Objects.requireNonNull(region, "Region is required for AWS buckets");
        }
    }
}
```

#### Credentials Configuration
The credentials are stored in a separate JSON file on disk for security:

```json
{
  "credentials": {
    "default-aws-bucket": {
      "accessKey": "YOUR_AWS_ACCESS_KEY",
      "secretKey": "YOUR_AWS_SECRET_KEY"
    },
    "default-gcp-bucket": {
      "accessKey": "YOUR_GCP_ACCESS_KEY",
      "secretKey": "YOUR_GCP_SECRET_KEY"
    }
  }
}
```

#### Credentials Records
```java
public record BucketCredentials(
    String accessKey,
    String secretKey
) {
    public BucketCredentials {
        Objects.requireNonNull(accessKey, "Access key cannot be null");
        Objects.requireNonNull(secretKey, "Secret key cannot be null");
    }
}

public record BucketCredentialsConfig(
    Map<String, BucketCredentials> credentials
) {
    public BucketCredentialsConfig {
        Objects.requireNonNull(credentials, "Credentials map cannot be null");
    }
}
```

#### Configuration Loading and Updates
The configuration system handles both network properties and local credentials:

```java
@Singleton
public class BucketConfigurationManager {
    private static final Logger logger = LogManager.getLogger(BucketConfigurationManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private final ConfigProvider configProvider;
    private final Path credentialsPath;
    private volatile BucketCredentialsConfig credentials;
    
    @Inject
    public BucketConfigurationManager(ConfigProvider configProvider) {
        this.configProvider = configProvider;
        this.credentialsPath = Path.of(configProvider.getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .credentialsPath());
        loadCredentials();
    }
    
    public List<CompleteBucketConfig> getCompleteBucketConfigs() {
        var networkConfig = configProvider.getConfiguration()
                .getConfigData(BlockStreamConfig.class);
                
        return networkConfig.buckets().stream()
                .map(bucket -> {
                    var creds = credentials.credentials().get(bucket.name());
                    if (creds == null) {
                        logger.error("No credentials found for bucket: {}", bucket.name());
                        return null;
                    }
                    return new CompleteBucketConfig(
                            bucket.name(),
                            bucket.provider(),
                            bucket.endpoint(),
                            bucket.region(),
                            bucket.bucketName(),
                            creds);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    private void loadCredentials() {
        try {
            credentials = mapper.readValue(credentialsPath.toFile(), BucketCredentialsConfig.class);
        } catch (IOException e) {
            logger.error("Failed to load bucket credentials from {}", credentialsPath, e);
            throw new RuntimeException("Failed to load bucket credentials", e);
        }
    }
}

public record CompleteBucketConfig(
    String name,
    String provider,
    String endpoint,
    String region,
    String bucketName,
    BucketCredentials credentials
) {}
```

#### MinIO Client Factory
```java
public class MinioClientFactory {
    public static MinioClient createClient(CompleteBucketConfig config) {
        return MinioClient.builder()
                .endpoint(config.endpoint())
                .credentials(
                    config.credentials().accessKey(),
                    config.credentials().secretKey())
                .region(config.region())  // Optional, only used for AWS
                .build();
    }
}
```

This configuration approach provides several benefits:

1. **Runtime Updates**: All non-sensitive configuration can be updated via network properties
2. **Security**: Sensitive credentials are kept separate and secure on disk
3. **Flexibility**: Support for multiple bucket configurations
4. **Type Safety**: Strong typing through Java records
5. **Validation**: Built-in validation through record constructors
6. **Separation of Concerns**: Clear separation between network config and credentials

The `BucketConfigurationManager` serves as the single point of access for complete bucket configurations, combining network properties with local credentials as needed.

### 3. BlockMetadata Handling

The `BlockMetadata` tracks both block information and upload status, enabling recovery scenarios after node restarts.

#### BlockMetadata Structure

```java
public record BlockMetadata(
    long blockNumber,
    String md5Hash,
    Instant createdAt,
    Map<String, Boolean> uploadStatus,  // Maps provider -> upload status
    boolean hashMismatch  // Indicates if a hash mismatch has occurred
) {
    public static BlockMetadata create(Path blockPath, long blockNumber) throws IOException {
        byte[] fileBytes = Files.readAllBytes(blockPath);
        String md5 = calculateMd5(fileBytes);
        
        return new BlockMetadata(
            blockNumber,
            md5,
            Files.getLastModifiedTime(blockPath).toInstant(),
            new ConcurrentHashMap<>(),  // Initially empty upload status
            false  // Initially no hash mismatch
        );
    }
    
    public void markUploaded(String provider) {
        uploadStatus.put(provider, true);
    }
    
    public boolean isUploadedTo(String provider) {
        return uploadStatus.getOrDefault(provider, false);
    }
    
    public boolean isFullyUploaded() {
        return !uploadStatus.containsValue(false) && !uploadStatus.isEmpty();
    }
    
    public void markHashMismatch() {
        this.hashMismatch = true;
    }
    
    private static String calculateMd5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
```

#### Writing BlockMetadata to Disk

When a block is closed, the `BlockMetadata` will be serialized and written to a file with the same name as the block file, but with a `.meta` extension. This will occur in the `closeBlock()` method of the `FileBlockItemWriter`.

```java
public class FileBlockItemWriter implements BlockItemWriter {
    private final List<BucketUploadListener> listeners = new ArrayList<>();
    
    public void addListener(BucketUploadListener listener) {
        listeners.forEach(l -> logger.info("Adding bucket upload listener for provider: {}", l.getProvider()));
        listeners.add(listener);
    }
    
    @Override
    public void closeBlock() {
        if (state.ordinal() < State.OPEN.ordinal()) {
            throw new IllegalStateException("Cannot close a FileBlockItemWriter that is not open");
        } else if (state.ordinal() == State.CLOSED.ordinal()) {
            throw new IllegalStateException("Cannot close a FileBlockItemWriter that is already closed");
        }

        try {
            writableStreamingData.close();
            state = State.CLOSED;

            // Write BlockMetadata to disk
            Path blockPath = getBlockFilePath(blockNumber);
            BlockMetadata metadata = BlockMetadata.create(blockPath, blockNumber);
            writeMetadataToFile(metadata, blockPath);

            // Notify listeners
            listeners.forEach(listener -> listener.onBlockClosed(blockPath, blockNumber));
        } catch (final IOException e) {
            logger.error("Error closing the FileBlockItemWriter output stream", e);
            throw new UncheckedIOException(e);
        }
    }

    private void writeMetadataToFile(BlockMetadata metadata, Path blockPath) throws IOException {
        Path metadataPath = Paths.get(blockPath.toString() + ".meta");
        try (BufferedWriter writer = Files.newBufferedWriter(metadataPath)) {
            writer.write(metadata.toString());
        }
    }
}
```

### 4. Cloud Storage Implementation

#### Base Interface
```java
public interface CloudBucketUploader {
    CompletableFuture<Void> uploadBlock(Path blockPath, BlockMetadata metadata);
    CompletableFuture<Boolean> blockExists(long blockNumber);
    CompletableFuture<String> getBlockMd5(long blockNumber);
    String getProvider();
}
```

#### MinIO Implementation
```java
@Singleton
public class MinioBucketUploader implements CloudBucketUploader {
    private static final Logger logger = LogManager.getLogger(MinioBucketUploader.class);
    private final MinioClient minioClient;
    private final String bucketName;
    private final String provider;
    private final ExecutorService uploadExecutor;
    private final int maxRetryAttempts;
    
    @Inject
    public MinioBucketUploader(
            BucketConfig config,
            @CommonExecutor ExecutorService executor,
            ConfigProvider configProvider) {
        this.uploadExecutor = executor;
        this.bucketName = config.name();
        this.provider = config.provider();
        this.maxRetryAttempts = configProvider.getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .uploadRetryAttempts();
        
        this.minioClient = MinioClientFactory.createClient(config);
    }

    @Override
    public CompletableFuture<Void> uploadBlock(Path blockPath, BlockMetadata metadata) {
        return CompletableFuture.runAsync(() -> {
            String objectKey = getObjectKey(metadata.blockNumber());
            
            try {
                // First check if object already exists
                if (blockExistsInternal(objectKey)) {
                    String existingMd5 = getBlockMd5Internal(objectKey);
                    if (existingMd5.equals(metadata.md5Hash())) {
                        logger.debug("Block {} already exists with matching MD5", metadata.blockNumber());
                        return;
                    }
                    throw new HashMismatchException(metadata.blockNumber(), provider);
                }

                // Upload with retry logic
                RetryUtils.withRetry(() -> {
                    minioClient.uploadObject(
                            UploadObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectKey)
                                    .filename(blockPath.toString())
                                    .contentType("application/octet-stream")
                                    .build());
                    return null;
                }, maxRetryAttempts);

            } catch (Exception e) {
                throw new CompletionException("Failed to upload block " + metadata.blockNumber(), e);
            }
        }, uploadExecutor);
    }

    @Override
    public CompletableFuture<Boolean> blockExists(long blockNumber) {
        return CompletableFuture.supplyAsync(
                () -> blockExistsInternal(getObjectKey(blockNumber)),
                uploadExecutor);
    }

    @Override
    public CompletableFuture<String> getBlockMd5(long blockNumber) {
        return CompletableFuture.supplyAsync(
                () -> getBlockMd5Internal(getObjectKey(blockNumber)),
                uploadExecutor);
    }

    @Override
    public String getProvider() {
        return provider;
    }

    private boolean blockExistsInternal(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw new CompletionException(e);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private String getBlockMd5Internal(String objectKey) {
        try {
            var stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            return stat.etag();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private String getObjectKey(long blockNumber) {
        return String.format("blocks/%d.blk", blockNumber);
    }
}
```

#### Retry Utility
```java
public class RetryUtils {
    private static final Logger logger = LogManager.getLogger(RetryUtils.class);

    public static <T> T withRetry(
            SupplierWithException<T> operation,
            int maxAttempts) throws Exception {
        
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                if (attempt == maxAttempts) {
                    break;
                }

                long backoffMillis = calculateBackoff(attempt);
                logger.warn("Attempt {} failed, retrying in {} ms", attempt, backoffMillis, e);
                Thread.sleep(backoffMillis);
            }
        }
        
        throw new RetryExhaustedException("Failed after " + maxAttempts + " attempts", lastException);
    }

    private static long calculateBackoff(int attempt) {
        // Exponential backoff with jitter: 2^n * 100ms + random(50ms)
        return (long) (Math.pow(2, attempt) * 100 + Math.random() * 50);
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
```

#### Exception Classes
```java
public class HashMismatchException extends RuntimeException {
    public HashMismatchException(long blockNumber, String provider) {
        super(String.format("Hash mismatch for block %d in provider %s", blockNumber, provider));
    }
}

public class RetryExhaustedException extends RuntimeException {
    public RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 5. BucketUploadManager

```java
@Singleton
public class BucketUploadManager implements BucketUploadListener {
    private final ExecutorService uploadExecutor;
    private final List<CloudBucketUploader> bucketUploaders;
    private final BlockMetricsManager metricsManager;
    private final BlockRetentionManager retentionManager;
    
    @Override
    public void onBlockClosed(Path blockPath, long blockNumber) {
        var metadata = BlockMetadata.create(blockPath, blockNumber);
        
        // Launch uploads without waiting
        bucketUploaders.forEach(uploader -> 
            CompletableFuture.runAsync(
                () -> handleBlockUpload(uploader, blockPath, metadata),
                uploadExecutor)
            .exceptionally(throwable -> {
                metricsManager.incrementFailedUploads();
                logger.error("Failed to upload block {}", blockNumber, throwable);
                return null;
            }));
    }

    private void handleBlockUpload(CloudBucketUploader uploader, Path blockPath, BlockMetadata metadata) {
        Timer.Sample sample = Timer.start();
        try {
            if (uploader.blockExists(metadata.blockNumber())) {
                var existingMd5 = uploader.getBlockMd5(metadata.blockNumber());
                if (existingMd5.equals(metadata.md5Hash())) {
                    metadata.markUploaded(uploader.getProvider());
                    updateMetadataFile(blockPath, metadata);
                    metricsManager.incrementSkippedUpload(uploader.getProvider());
                    return;
                }
                metricsManager.incrementHashMismatch(uploader.getProvider());
                metadata.markHashMismatch();  // Mark hash mismatch
                updateMetadataFile(blockPath, metadata);
                throw new HashMismatchException(metadata.blockNumber(), uploader.getProvider());
            }
            
            uploader.uploadBlock(blockPath, metadata);
            metadata.markUploaded(uploader.getProvider());
            updateMetadataFile(blockPath, metadata);
            metricsManager.recordUploadLatency(uploader.getProvider(), sample);
            metricsManager.incrementSuccessfulUpload(uploader.getProvider());
            
        } catch (Exception e) {
            metricsManager.incrementUploadFailure(uploader.getProvider());
            throw e;
        }
    }
    
    private void updateMetadataFile(Path blockPath, BlockMetadata metadata) throws IOException {
        Path metadataPath = Paths.get(blockPath.toString() + ".meta");
        try (BufferedWriter writer = Files.newBufferedWriter(metadataPath)) {
            objectMapper.writeValue(writer, metadata);
        }
    }
}
```
### 6. BlockRecoveryManager
The `BlockRecoveryManager` is responsible for identifying and attempting to upload blocks that were not attempted to be uploaded before a node restart. It ensures that any blocks left in an incomplete state are retried, except those with a hash mismatch.
```java
@Singleton
public class BlockRecoveryManager {
    private static final Logger logger = LogManager.getLogger(BlockRecoveryManager.class);
    private final Path blockDirectory;
    private final BucketUploadManager uploadManager;
    private final List<CloudBucketUploader> uploaders;
    
    @Inject
    public BlockRecoveryManager(
            ConfigProvider configProvider,
            NodeInfo nodeInfo,
            FileSystem fileSystem,
            BucketUploadManager uploadManager,
            List<CloudBucketUploader> uploaders) {
        this.blockDirectory = fileSystem.getPath(configProvider.getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .blockDirectory(), 
                nodeInfo.getNodeId());
        this.uploadManager = uploadManager;
        this.uploaders = uploaders;
    }
    
    public void recoverIncompleteUploads() {
        try (var files = Files.list(blockDirectory)) {
            files.filter(path -> path.toString().endsWith(".blk"))
                    .forEach(this::checkAndRecoverBlock);
        } catch (IOException e) {
            logger.error("Error scanning block directory during recovery: {}", e.getMessage());
        }
    }
    
    private void checkAndRecoverBlock(Path blockPath) {
        try {
            Path metadataPath = Paths.get(blockPath.toString() + ".meta");
            if (!Files.exists(metadataPath)) {
                logger.warn("Missing metadata file for block: {}", blockPath);
                return;
            }
            
            BlockMetadata metadata = readMetadata(metadataPath);
            if (!metadata.isFullyUploaded() && !metadata.hashMismatch()) {
                logger.info("Recovering incomplete upload for block: {}", blockPath);
                uploadManager.uploadBlock(blockPath, metadata);
            }
        } catch (IOException e) {
            logger.error("Error recovering block {}: {}", blockPath, e.getMessage());
        }
    }
    
    private BlockMetadata readMetadata(Path metadataPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(metadataPath)) {
            return objectMapper.readValue(reader, BlockMetadata.class);
        }
    }
}
```


### 7. BlockRetentionManager

```java
@Singleton
public class BlockRetentionManager {
    private static final Logger logger = LogManager.getLogger(BlockRetentionManager.class);
    private final Duration retentionPeriod;
    private final Path blockDirectory;
    private final BlockMetricsManager metricsManager;
    private final ExecutorService cleanupExecutor;

    @Inject
    public BlockRetentionManager(
            ConfigProvider configProvider,
            NodeInfo nodeInfo,
            FileSystem fileSystem,
            BlockMetricsManager metricsManager,
            @CommonExecutor ExecutorService executor) {
        var config = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        this.retentionPeriod = config.blockRetentionPeriod();
        this.blockDirectory = fileSystem.getPath(config.blockDirectory(), nodeInfo.getNodeId());
        this.metricsManager = metricsManager;
        this.cleanupExecutor = executor;
    }

    public CompletableFuture<Void> cleanExpiredBlocks() {
        return CompletableFuture.runAsync(() -> {
            try (var files = Files.list(blockDirectory)) {
                files.filter(path -> path.toString().endsWith(".blk") || path.toString().endsWith(".blk.gz"))
                        .map(path -> CompletableFuture.runAsync(() -> {
                            try {
                                if (isExpired(path)) {
                                    Files.delete(path);
                                    metricsManager.incrementDeletedBlocks();
                                    logger.debug("Deleted expired block file: {}", path);
                                }
                            } catch (IOException e) {
                                logger.error("Error deleting expired block {}: {}", path, e.getMessage());
                            }
                        }, cleanupExecutor))
                        .collect(Collectors.toList())
                        .forEach(CompletableFuture::join);
            } catch (IOException e) {
                logger.error("Error scanning block directory: {}", e.getMessage());
            }
        }, cleanupExecutor);
    }

    private boolean isExpired(Path blockPath) throws IOException {
        Path metadataPath = Paths.get(blockPath.toString() + ".meta");
        if (!Files.exists(metadataPath)) {
            return false;  // Cannot determine expiration without metadata
        }
        
        BlockMetadata metadata = readMetadata(metadataPath);
        if (metadata.hashMismatch()) {
            return false;  // Do not delete if there was a hash mismatch
        }
        
        var lastModifiedTime = Files.getLastModifiedTime(blockPath);
        var age = Duration.between(lastModifiedTime.toInstant(), Instant.now());
        return age.compareTo(retentionPeriod) > 0;
    }

    private BlockMetadata readMetadata(Path metadataPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(metadataPath)) {
            return objectMapper.readValue(reader, BlockMetadata.class);
        }
    }
}
```

### 8. Metrics

```java
@Singleton
public class BlockMetricsManager {
    private static final String METRIC_PREFIX = "hedera.blocks.bucket.";
    
    private final Counter uploadSuccessCounter;
    private final Counter uploadFailureCounter;
    private final Counter hashMismatchCounter;
    private final Counter skippedUploadCounter;
    private final Timer uploadLatencyTimer;
    private final Gauge retainedBlocksGauge;
    
    @Inject
    public BlockMetricsManager(MetricsRegistry registry) {
        this.uploadSuccessCounter = registry.counter(METRIC_PREFIX + "uploads.success");
        this.uploadFailureCounter = registry.counter(METRIC_PREFIX + "uploads.failure");
        this.hashMismatchCounter = registry.counter(METRIC_PREFIX + "hash.mismatch");
        this.skippedUploadCounter = registry.counter(METRIC_PREFIX + "uploads.skipped");
        this.uploadLatencyTimer = registry.timer(METRIC_PREFIX + "upload.latency");
        this.retainedBlocksGauge = registry.gauge(METRIC_PREFIX + "blocks.retained");
    }
}
```

## Flow Sequences

### Happy Path

1. Block is written to disk using FileBlockItemWriter
2. On closeBlock():
   - FileBlockItemWriter notifies registered BucketUploadListeners
   - BucketUploadManager launches parallel uploads for each configured bucket
   - After successful uploads, local file is scheduled for deletion

### Error Handling

- Upload Failure:
  - Retry with exponential backoff
  - Keep local copy indefinitely
  - Emit metrics and alerts
  - Log detailed error information

### Hash Mismatch

- When detected:
    - Keep local copy
    - Emit hash mismatch metric
    - Log detailed comparison 
    - Alert operators

### Recovery Handling
- On node startup, the BlockRecoveryManager checks for blocks that have not been atempted to be uploaded. If the block has a hash mismatch, it will not be uploaded again.

## Testing Strategy

1. Unit tests for each component
2. Integration tests with mocked cloud services
3. End-to-end tests with real cloud services
4. Credential loading and error handling tests
5. Parallel upload and deletion tests

The implementation maintains compatibility with existing systems while adding cloud storage capabilities in a way that ensures reliability and performance through parallel processing and proper error handling.