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

package com.hedera.node.app.uploader.credentials;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.config.types.BucketProvider;
import org.junit.jupiter.api.Test;

class CompleteBucketConfigTest {

    @Test
    void testConstructorAndAccessors() {
        String name = "testBucket";
        BucketProvider provider = BucketProvider.AWS;
        String endpoint = "https://s3.amazonaws.com";
        String region = "us-east-1";
        String bucketName = "my-bucket";
        boolean enabled = true;
        BucketCredentials credentials = new BucketCredentials("accessKey", "secretKey".toCharArray());

        CompleteBucketConfig config =
                new CompleteBucketConfig(name, provider, endpoint, region, bucketName, enabled, credentials);

        assertEquals(name, config.name());
        assertEquals(provider, config.provider());
        assertEquals(endpoint, config.endpoint());
        assertEquals(region, config.region());
        assertEquals(bucketName, config.bucketName());
        assertTrue(config.enabled());
        assertEquals(credentials, config.credentials());
    }

    @Test
    void testEqualsAndHashCode() {
        BucketCredentials credentials1 = new BucketCredentials("accessKey1", "secretKey1".toCharArray());
        BucketCredentials credentials2 = new BucketCredentials("accessKey2", "secretKey2".toCharArray());

        CompleteBucketConfig config1 = new CompleteBucketConfig("bucket1", BucketProvider.AWS,
                "https://endpoint1", "us-east-1", "bucket-name1", true, credentials1);

        CompleteBucketConfig config2 = new CompleteBucketConfig("bucket1", BucketProvider.AWS,
                "https://endpoint1", "us-east-1", "bucket-name1", true, credentials1);

        CompleteBucketConfig config3 = new CompleteBucketConfig("bucket2", BucketProvider.GCP,
                "https://endpoint2", "us-central1", "bucket-name2", false, credentials2);

        assertEquals(config1, config2); // Equal objects
        assertEquals(config1.hashCode(), config2.hashCode()); // Equal hash codes

        assertNotEquals(config1, config3); // Different objects
        assertNotEquals(config1.hashCode(), config3.hashCode()); // Different hash codes
    }

    @Test
    void testToString() {
        BucketCredentials credentials = new BucketCredentials("accessKey", "secretKey".toCharArray());
        CompleteBucketConfig config = new CompleteBucketConfig(
                "testBucket",
                BucketProvider.AWS,
                "https://s3.amazonaws.com",
                "us-east-1",
                "my-bucket",
                true,
                credentials);
        String result = config.toString();

        assertTrue(result.contains("testBucket"));
        assertTrue(result.contains("AWS"));
        assertTrue(result.contains("https://s3.amazonaws.com"));
        assertTrue(result.contains("us-east-1"));
        assertTrue(result.contains("my-bucket"));
        assertTrue(result.contains("true"));
        assertTrue(result.contains("accessKey"));
    }
}
