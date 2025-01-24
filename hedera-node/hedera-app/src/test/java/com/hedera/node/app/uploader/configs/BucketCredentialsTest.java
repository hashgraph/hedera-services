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

package com.hedera.node.app.uploader.configs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BucketCredentialsTest {

    private BucketCredentials awsBucketCredentials;
    private BucketCredentials gcsBucketCredentials;

    @BeforeEach
    void setUp() {
        awsBucketCredentials = new BucketCredentials("awsAccessKey", "awsSecretKey".toCharArray());
        gcsBucketCredentials = new BucketCredentials("gcsAccessKey", "gcsSecretKey".toCharArray());
    }

    @Test
    void testConstructor_NullAccessKey() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> new BucketCredentials(null, "secretKey".toCharArray()));
        assertEquals("access key cannot be null", exception.getMessage());
    }

    @Test
    void testConstructor_NullSecretKey() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> new BucketCredentials("accessKey", null));
        assertEquals("secret key cannot be null", exception.getMessage());
    }

    @Test
    void testEquals_EqualObjects() {
        BucketCredentials sameAsAws = new BucketCredentials("awsAccessKey", "awsSecretKey".toCharArray());
        assertTrue(awsBucketCredentials.equals(sameAsAws));
    }

    @Test
    void testEquals_DifferentAccessKey() {
        BucketCredentials different = new BucketCredentials("differentKey", "awsSecretKey".toCharArray());
        assertFalse(awsBucketCredentials.equals(different));
    }

    @Test
    void testEquals_DifferentSecretKey() {
        BucketCredentials different = new BucketCredentials("awsAccessKey", "differentSecret".toCharArray());
        assertFalse(awsBucketCredentials.equals(different));
    }

    @Test
    void testCredentials() {
        assertFalse(awsBucketCredentials.equals(null));
        assertFalse(awsBucketCredentials.equals("someString"));
        assertNotEquals(awsBucketCredentials.hashCode(), gcsBucketCredentials.hashCode());
        BucketCredentials sameAsAws = new BucketCredentials("awsAccessKey", "awsSecretKey".toCharArray());
        assertEquals(awsBucketCredentials.hashCode(), sameAsAws.hashCode());
        BucketCredentials emptySecret = new BucketCredentials("accessKey", new char[0]);
        BucketCredentials emptySecret2 = new BucketCredentials("accessKey", new char[0]);
        assertTrue(emptySecret.equals(emptySecret2));
    }
}
