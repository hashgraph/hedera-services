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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OnDiskBucketConfigurationTest {

    @Test
    void testConstructorWithCredentials() {
        Map<String, BucketCredentials> credentials = new HashMap<>();
        credentials.put("bucket1", new BucketCredentials("accessKey1", "secretKey1".toCharArray()));
        OnDiskBucketConfig config = new OnDiskBucketConfig(credentials);
        assertEquals(credentials, config.credentials());
        // Test with empty credentials
        Map<String, BucketCredentials> emptyCredentials = Collections.emptyMap();
        config = new OnDiskBucketConfig(emptyCredentials);
        assertTrue(config.credentials().isEmpty());
    }

    @Test
    void testConstructorWithNullCredentialsThrowsException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new OnDiskBucketConfig(null));
        assertEquals("Credentials map cannot be null", exception.getMessage());
    }

    @Test
    void testEqualsAndHashCode() {
        Map<String, BucketCredentials> credentials1 = new HashMap<>();
        credentials1.put("bucket1", new BucketCredentials("accessKey1", "secretKey1".toCharArray()));
        Map<String, BucketCredentials> credentials2 = new HashMap<>();
        credentials2.put("bucket1", new BucketCredentials("accessKey1", "secretKey1".toCharArray()));
        Map<String, BucketCredentials> credentials3 = new HashMap<>();
        credentials3.put("bucket2", new BucketCredentials("accessKey2", "secretKey2".toCharArray()));

        OnDiskBucketConfig config1 = new OnDiskBucketConfig(credentials1);
        OnDiskBucketConfig config2 = new OnDiskBucketConfig(credentials2);
        OnDiskBucketConfig config3 = new OnDiskBucketConfig(credentials3);

        assertEquals(config1, config2); // Equal objects
        assertEquals(config1.hashCode(), config2.hashCode()); // Equal hash codes
        assertNotEquals(config1, config3); // Different objects
        assertNotEquals(config1.hashCode(), config3.hashCode()); // Different hash codes
    }
}
