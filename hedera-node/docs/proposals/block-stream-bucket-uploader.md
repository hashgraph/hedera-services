# Block Stream Bucket Upload Design Document
## Summary

In order for consensus nodes to upload block files to public cloud buckets, 
this proposal extends the existing block streaming capabilities to
support uploading blocks to cloud storage buckets (AWS S3 and GCP Cloud Storage)
while maintaining local copies for redundancy.

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
   1. [Extended BlockStreamWriterMode](#extended-blockstreamwritermode)
   2. [CloudBucketUploader Interface](#cloudbucketuploader-interface)
   3. [BlockMetadata](#blockmetadata)
   4. [AWS Implementation](#aws-implementation)
   5. [GCP Implementation](#gcp-implementation)
   6. [Cloud Upload Module](#cloud-upload-module)
   7. [BucketUploadManager](#bucketuploadmanager)
   8. [Dependency Injection](#dependency-injection)
   9. [BlockRetentionManager](#blockretentionmanager)
   10. [Metrics](#metrics)
5. [Flow Sequences](#flow-sequences)
   1. [Happy Path](#happy-path)
   2. [Error Handling](#error-handling)
   3. [Hash Mismatch](#hash-mismatch)
   4. [Integration](#integration)
6. [Testing Strategy](#testing-strategy)


### Context
The following image depicts how multiple consensus nodes will be attempting to upload
to the same bucket. Only the first node to upload the block will succeed, and the others
will check if the block is already available. If the block is available, the node will
check if the block is the same by comparing the MD5 hash. If the hashes are different,
the block will be kept on the local disk, and an alert will be triggered. If the upload
fails, the block will be kept on the local disk, and an alert will be triggered.

<img src="./Phase1.png" alt="Phase1"></img>

### Dependencies

Until other lightweight libraries are available/identified for use for GCP/AWS buckets, this proposal assumes the following 
dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
        <version>2.24.0</version>
    </dependency>
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-storage</artifactId>
        <version>2.32.1</version>
    </dependency>
</dependencies>
```

### Requirements
The following requirements have been identified for this proposal:
- We need a third BlockStreamWriterMode, which sends the blocks to one or more buckets.
- The buckets must be configurable.
- Both the writer mode and the addresses of the buckets must be configurable during runtime (i.e., the node must not be restarted to take the changes into effect).
- If an error occurs, we want to keep a copy of the block on the local disk. It probably makes sense to write the block to a local file initially and upload it once done (instead of writing directly to the bucket).
- We want to upload only one instance of a block. All consensus nodes will try to upload the same block to a bucket, and therefore only the first one can succeed.
- If a consensus node notices the block is already available, it should check if the blocks are the same (via MD5 hash). If they are different, the block should be kept on the local disk, and an alert must be triggered.
- If uploading a block fails, it should be kept on the local disk, and an alert needs to be triggered.
- If the upload was successful, the local copy can be removed after a few hours.

---

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

Network-wide properties (dynamically updatable via file 0.0.121):

```java
@ConfigData("blockStream")
public record BlockStreamConfig(
    ...
    @ConfigProperty(defaultValue = "FILE_AND_BUCKET") @NetworkProperty BlockStreamWriterMode writerMode,
    @ConfigProperty(defaultValue = "true") @NetworkProperty boolean enableBucketUploads,
    @ConfigProperty(defaultValue = "3") @NetworkProperty int uploadRetryAttempts,
    @ConfigProperty(defaultValue = "168") @NetworkProperty int localRetentionHours,
    @ConfigProperty(defaultValue = "TODO") @NetworkProperty List<String> bucketEndpoints,
    @ConfigProperty(defaultValue = "TODO") @NetworkProperty String awsRegion,
    @ConfigProperty(defaultValue = "TODO") @NetworkProperty String awsCredentialsPath,
    @ConfigProperty(defaultValue = "TODO") @NetworkProperty String gcpCredentialsPath
) {}
```

### 3. BlockMetadata
```java
/**
 * Immutable record containing metadata about a block file.
 */
public record BlockMetadata(
        long blockNumber,
        String md5Hash,
        Instant createdAt
) {
    /**
     * Creates BlockMetadata from a block file path.
     *
     * @param blockPath Path to the block file
     * @param blockNumber The block number
     * @return BlockMetadata instance
     * @throws IOException if file operations fail
     */
    public static BlockMetadata create(Path blockPath, long blockNumber) throws IOException {
        byte[] fileBytes = Files.readAllBytes(blockPath);
        String md5 = calculateMd5(fileBytes);
        
        return new BlockMetadata(
                blockNumber,
                md5,
                Files.getLastModifiedTime(blockPath).toInstant()
        );
    }
    
    /**
     * Calculates MD5 hash of the given bytes.
     *
     * @param bytes The bytes to hash
     * @return Base64 encoded MD5 hash
     */
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

### 4. BucketUploadListener Interface
```java
public interface BucketUploadListener {
    void onBlockClosed(Path blockPath, long blockNumber);
    String getProvider();  // Returns "aws" or "gcp"
}
```

### 5. FileBlockItemWriter Extension
Extends existing FileBlockItemWriter (reference to implementation):

```java
@Override
public void closeBlock() {
    if (state.ordinal() < State.OPEN.ordinal()) {
        throw new IllegalStateException("Cannot close a FileBlockItemWriter that is not open");
    } else if (state.ordinal() == State.CLOSED.ordinal()) {
        throw new IllegalStateException("Cannot close a FileBlockItemWriter that is already closed");
    }

    // Close the writableStreamingData.
    try {
        writableStreamingData.close();
        state = State.CLOSED;
    } catch (final IOException e) {
        logger.error("Error closing the FileBlockItemWriter output stream", e);
        throw new UncheckedIOException(e);
    }
}
```

Add listener support:
```java
public class FileBlockItemWriter implements BlockItemWriter {
    private final List<BucketUploadListener> listeners = new ArrayList<>();
    
    public void addListener(BucketUploadListener listener) {
        listeners.forEach(l -> logger.info("Adding bucket upload listener for provider: {}", l.getProvider()));
        listeners.add(listener);
    }
    
    @Override
    public void closeBlock() {
        // Existing close implementation
        ...
        
        // Notify listeners
        final var blockPath = getBlockFilePath(blockNumber);
        listeners.forEach(listener -> listener.onBlockClosed(blockPath, blockNumber));
    }
}
```

### 6. Cloud Storage Implementations

#### Base Interface
```java
public interface CloudBucketUploader {
    void uploadBlock(Path blockPath, BlockMetadata metadata) throws IOException;
    boolean blockExists(long blockNumber);
    String getBlockMd5(long blockNumber);
    String getProvider();
}
```

#### AWS Implementation
```java
@Singleton
public class AwsBucketUploader implements CloudBucketUploader {
    private static final Logger logger = LogManager.getLogger(AwsBucketUploader.class);
    private final S3Client s3Client;
    private final String bucketName;
    private final int maxRetries;
    
    @Inject
    public AwsBucketUploader(ConfigProvider configProvider) {
        var config = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        
        this.s3Client = S3Client.builder()
                .region(Region.of(config.awsRegion()))
                .credentialsProvider(ProfileCredentialsProvider.create(config.awsCredentialsPath()))
                .build();
        this.bucketName = extractBucketName(config.bucketEndpoints());
        this.maxRetries = config.uploadRetryAttempts();
    }
    
    @Override
    public void uploadBlock(Path blockPath, BlockMetadata metadata) throws IOException {
        String key = String.format("blocks/%d.blk", metadata.blockNumber());
        
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentMD5(metadata.md5Hash())
                .build();

        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                s3Client.putObject(request, RequestBody.fromFile(blockPath));
                return;
            } catch (S3Exception e) {
                attempts++;
                if (attempts == maxRetries) {
                    throw new IOException("Failed to upload block to S3 after " + maxRetries + " attempts", e);
                }
                logger.warn("S3 upload attempt {} failed, retrying...", attempts, e);
                Thread.sleep(exponentialBackoff(attempts));
            }
        }
    }
    
    @Override
    public boolean blockExists(long blockNumber) {
        String key = String.format("blocks/%d.blk", blockNumber);
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
    
    @Override
    public String getBlockMd5(long blockNumber) {
        String key = String.format("blocks/%d.blk", blockNumber);
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return response.metadata().get("Content-MD5");
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to get MD5 for block " + blockNumber, e);
        }
    }
    
    @Override
    public String getProvider() {
        return "aws";
    }
    
    private long exponentialBackoff(int attempt) {
        return Math.min(1000L * (long) Math.pow(2, attempt), 30000L);
    }
}
```

#### GCP Implementation
```java
@Singleton
public class GcpBucketUploader implements CloudBucketUploader {
    private static final Logger logger = LogManager.getLogger(GcpBucketUploader.class);
    private final Storage storage;
    private final String bucketName;
    private final int maxRetries;
    
    @Inject
    public GcpBucketUploader(ConfigProvider configProvider) throws IOException {
        var config = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        
        this.storage = StorageOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream(config.gcpCredentialsPath())))
                .build()
                .getService();
        this.bucketName = extractBucketName(config.bucketEndpoints());
        this.maxRetries = config.uploadRetryAttempts();
    }
    
    @Override
    public void uploadBlock(Path blockPath, BlockMetadata metadata) throws IOException {
        String blobName = String.format("blocks/%d.blk", metadata.blockNumber());
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("application/octet-stream")
                .setMd5(metadata.md5Hash())
                .build();

        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                storage.create(blobInfo, Files.readAllBytes(blockPath));
                return;
            } catch (StorageException e) {
                attempts++;
                if (attempts == maxRetries) {
                    throw new IOException("Failed to upload block to GCP after " + maxRetries + " attempts", e);
                }
                logger.warn("GCP upload attempt {} failed, retrying...", attempts, e);
                Thread.sleep(exponentialBackoff(attempts));
            }
        }
    }
    
    @Override
    public boolean blockExists(long blockNumber) {
        String blobName = String.format("blocks/%d.blk", blockNumber);
        return storage.get(BlobId.of(bucketName, blobName)) != null;
    }
    
    @Override
    public String getBlockMd5(long blockNumber) {
        String blobName = String.format("blocks/%d.blk", blockNumber);
        Blob blob = storage.get(BlobId.of(bucketName, blobName));
        if (blob == null) {
            throw new RuntimeException("Block " + blockNumber + " not found in GCP bucket");
        }
        return blob.getMd5();
    }
    
    @Override
    public String getProvider() {
        return "gcp";
    }
    
    private long exponentialBackoff(int attempt) {
        return Math.min(1000L * (long) Math.pow(2, attempt), 30000L);
    }
}
```

Both implementations feature:
1. Exponential backoff retry logic
2. MD5 hash verification
3. Consistent block path formatting
4. Provider-specific error handling
5. Configuration via the shared BlockStreamConfig
6. Singleton scoped instances
7. Proper resource cleanup and exception handling

The implementations can be registered in the dependency injection system like this:

```java
@Module
public class CloudUploadModule {
    @Provides
    @Singleton
    List<CloudBucketUploader> provideCloudUploaders(
            ConfigProvider configProvider,
            AwsBucketUploader awsUploader,
            GcpBucketUploader gcpUploader) {
        
        var config = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        if (!config.enableBucketUploads()) {
            return List.of();
        }
        
        return List.of(awsUploader, gcpUploader);
    }
}
```


### 7. BucketUploadManager
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
                    metricsManager.incrementSkippedUpload(uploader.getProvider());
                    return;
                }
                metricsManager.incrementHashMismatch(uploader.getProvider());
                throw new HashMismatchException(metadata.blockNumber(), uploader.getProvider());
            }
            
            uploader.uploadBlock(blockPath, metadata);
            metricsManager.recordUploadLatency(uploader.getProvider(), sample);
            metricsManager.incrementSuccessfulUpload(uploader.getProvider());
            
        } catch (Exception e) {
            metricsManager.incrementUploadFailure(uploader.getProvider());
            throw e;
        }
    }
}
```



### 8. Dependency Injection
```java
@Module
public class BlockStreamModule {
    @Provides
    @Singleton
    static BlockItemWriter provideBlockItemWriter(
            ConfigProvider configProvider,
            NodeInfo selfNodeInfo,
            FileSystem fileSystem,
            BucketUploadManager bucketUploadManager) {
        
        var writer = new FileBlockItemWriter(configProvider, selfNodeInfo, fileSystem);
        
        if (configProvider.getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .writerMode() == BlockStreamWriterMode.FILE_AND_BUCKET) {
            writer.addListener(bucketUploadManager);
        }
        
        return writer;
    }
}
```

### 9. BlockRetentionManager
The BlockRetentionManager is responsible for deleting blocks that are older than the retention period. This could be invoked in HandleWorkflow every x number of minutes in which it will scan the block directory and delete any blocks that are older than the retention period.
```java
/**
 * Manages retention of block files based on their age.
 */
@Singleton
public class BlockRetentionManager {

    private static final Logger logger = LogManager.getLogger(BlockRetentionManager.class);

    private final Duration retentionPeriod;
    private final Path blockDirectory;
    private final BlockMetricsManager metricsManager;

    @Inject
    public BlockRetentionManager(
            ConfigProvider configProvider,
            NodeInfo nodeInfo,
            FileSystem fileSystem,
            BlockMetricsManager metricsManager) {
        var config = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        this.retentionPeriod = config.blockRetentionPeriod();
        this.blockDirectory = fileSystem.getPath(config.blockDirectory(), nodeInfo.getNodeId());
        this.metricsManager = metricsManager;
    }

    /**
     * Checks if a block file should be deleted based on its age.
     * 
     * @param blockPath Path to the block file
     * @return true if the file was deleted, false otherwise
     */
    public boolean checkAndDeleteExpiredBlock(Path blockPath) {
        try {
            var lastModifiedTime = Files.getLastModifiedTime(blockPath);
            var age = Duration.between(lastModifiedTime.toInstant(), Instant.now());

            if (age.compareTo(retentionPeriod) > 0) {
                Files.delete(blockPath);
                logger.debug("Deleted expired block file: {}", blockPath);
                return true;
            }
            return false;

        } catch (IOException e) {
            logger.error("Error checking/deleting block file {}: {}", blockPath, e.getMessage());
            return false;
        }
    }

    /**
     * Scans the block directory and removes any expired block files.
     */
    public void cleanExpiredBlocks() {
        try (var files = Files.list(blockDirectory)) {
            files.filter(path -> path.toString().endsWith(".blk") || path.toString().endsWith(".blk.gz"))
                    .forEach(path -> {
                        if (checkAndDeleteExpiredBlock(path)) {
                            metricsManager.incrementDeletedBlocks();
                        }
                    });
        } catch (IOException e) {
            logger.error("Error scanning block directory for expired files: {}", e.getMessage());
        }
    }
}
```

### 10. Metrics
Here are some possible metrics that could be useful, but we should discuss and confirm what is needed.
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

### Integration
The system integrates with the existing block stream infrastructure through:
1. Extension of BlockStreamWriterMode
2. Listener pattern on FileBlockItemWriter
3. Dependency injection for bucket uploaders

## Testing Strategy
1. Unit tests for each component
2. Integration tests with mocked cloud services
3. End-to-end tests with real cloud services

The implementation maintains compatibility with existing systems while adding cloud storage capabilities in a way that ensures reliability and performance through parallel processing and proper error handling.