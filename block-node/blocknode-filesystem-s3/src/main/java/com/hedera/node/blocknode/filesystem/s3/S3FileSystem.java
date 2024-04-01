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

package com.hedera.node.blocknode.filesystem.s3;

import static java.util.Objects.requireNonNull;
import static software.amazon.awssdk.http.SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.config.data.BlockNodeFileSystemConfig;
import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.node.blocknode.util.BlockNodeUtil;
import com.hedera.services.stream.v7.proto.Block;
import com.hedera.services.stream.v7.proto.BlockItem;
import java.io.ByteArrayInputStream;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.utils.AttributeMap;

public class S3FileSystem implements FileSystemApi {

    // instance of the awssdk client
    private final S3Client client;

    // config provider
    private final BlockNodeFileSystemConfig fileSystemConfig;

    public S3FileSystem(ConfigProvider configProvider) {
        this.fileSystemConfig = configProvider.getConfiguration().getConfigData(BlockNodeFileSystemConfig.class);

        this.client = createS3ClientV2();
    }

    private S3Client createS3ClientV2() {
        return S3Client.builder()
                .region(Region.of(fileSystemConfig.s3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        fileSystemConfig.s3AccessKeyId(), fileSystemConfig.s3SecretAccessKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .endpointOverride(URI.create(fileSystemConfig.s3Uri()))
                .httpClient(UrlConnectionHttpClient.builder()
                        .buildWithDefaults(AttributeMap.builder()
                                .put(TRUST_ALL_CERTIFICATES, Boolean.TRUE)
                                .build()))
                .build();
    }

    @Override
    public void writeBlock(Block block) {
        byte[] serializedData = block.toByteArray();
        ByteArrayInputStream compressedInputStream = new ByteArrayInputStream(serializedData);
        RequestBody requestBody = RequestBody.fromInputStream(compressedInputStream, serializedData.length);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(fileSystemConfig.s3BucketName())
                .key(BlockNodeUtil.extractBlockFileNameFromBlock(block))
                .build();

        this.client.putObject(objectRequest, requestBody);
    }

    @Override
    public Block readBlock(long number) {
        return null;
    }
}
