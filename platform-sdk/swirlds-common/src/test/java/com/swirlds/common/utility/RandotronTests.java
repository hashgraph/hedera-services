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

package com.swirlds.common.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.common.test.fixtures.Randotron;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Randotron class
 */
public class RandotronTests {
    private Randotron random1;
    private Randotron random1Duplicate;
    private Randotron random2;

    @BeforeEach
    void setUp() {
        random1 = Randotron.create();
        random1Duplicate = Randotron.create(random1.getSeed());
        random2 = Randotron.create();
    }

    @Test
    @DisplayName("Test randomString with different seeds")
    void randomStringUnique() {
        assertNotEquals(random1.randomString(10), random2.randomString(10));
    }

    @Test
    @DisplayName("Test randomString with same seed")
    void randomStringSameSeed() {
        assertEquals(random1.randomString(10), random1Duplicate.randomString(10));
    }

    @Test
    @DisplayName("Test randomIp with different seeds")
    void randomIpUnique() {
        assertNotEquals(random1.randomIp(), random2.randomIp());
    }

    @Test
    @DisplayName("Test randomIp with same seed")
    void randomIpSameSeed() {
        assertEquals(random1.randomIp(), random1Duplicate.randomIp());
    }

    @Test
    @DisplayName("Test bounded randomPositiveLong with different seeds")
    void randomPositiveBoundedLongUnique() {
        assertNotEquals(random1.randomPositiveLong(10), random2.randomPositiveLong(10));
    }

    @Test
    @DisplayName("Test bounded randomPositiveLong(long) with same seed")
    void randomPositiveBoundedLongSameSeed() {
        assertEquals(random1.randomPositiveLong(10), random1Duplicate.randomPositiveLong(10));
    }

    @Test
    @DisplayName("Test randomPositiveLong with different seeds")
    void randomPositiveLongUnique() {
        assertNotEquals(random1.randomPositiveLong(), random2.randomPositiveLong());
    }

    @Test
    @DisplayName("Test randomPositiveLong with same seed")
    void randomPositiveLongSameSeed() {
        assertEquals(random1.randomPositiveLong(), random1Duplicate.randomPositiveLong());
    }

    @Test
    @DisplayName("Test randomHash with different seeds")
    void randomHashUnique() {
        assertNotEquals(random1.randomHash(), random2.randomHash());
    }

    @Test
    @DisplayName("Test randomHash with same seed")
    void randomHashSameSeed() {
        assertEquals(random1.randomHash(), random1Duplicate.randomHash());
    }

    @Test
    @DisplayName("Test randomHashBytes with different seeds")
    void randomHashBytesUnique() {
        assertNotEquals(random1.randomHashBytes(), random2.randomHashBytes());
    }

    @Test
    @DisplayName("Test randomHashBytes with same seed")
    void randomHashBytesSameSeed() {
        assertEquals(random1.randomHashBytes(), random1Duplicate.randomHashBytes());
    }

    @Test
    @DisplayName("Test randomSignature with different seeds")
    void randomSignatureUnique() {
        assertNotEquals(random1.randomSignature(), random2.randomSignature());
    }

    @Test
    @DisplayName("Test randomSignature with same seed")
    void randomSignatureSameSeed() {
        assertEquals(random1.randomSignature(), random1Duplicate.randomSignature());
    }

    @Test
    @DisplayName("Test randomSignatureBytes with different seeds")
    void randomSignatureBytesUnique() {
        assertNotEquals(random1.randomSignatureBytes(), random2.randomSignatureBytes());
    }

    @Test
    @DisplayName("Test randomSignatureBytes with same seed")
    void randomSignatureBytesSameSeed() {
        assertEquals(random1.randomSignatureBytes(), random1Duplicate.randomSignatureBytes());
    }

    @Test
    @DisplayName("Test randomByteArray with different seeds")
    void randomByteArrayUnique() {
        assertNotEquals(random1.randomByteArray(10), random2.randomByteArray(10));
    }

    @Test
    @DisplayName("Test randomByteArray with same seed")
    void randomByteArraySameSeed() {
        assertEquals(random1.randomByteArray(10), random1Duplicate.randomByteArray(10));
    }

    @Test
    @DisplayName("Test randomInstant with different seeds")
    void randomInstantUnique() {
        assertNotEquals(random1.randomInstant(), random2.randomInstant());
    }

    @Test
    @DisplayName("Test randomInstant with same seed")
    void randomInstantSameSeed() {
        assertEquals(random1.randomInstant(), random1Duplicate.randomInstant());
    }

    @Test
    @DisplayName("Test randomBooleanWithProbability with different seeds")
    void randomBooleanWithProbabilityUnique() {
        assertNotEquals(random1.randomBooleanWithProbability(0.5), random2.randomBooleanWithProbability(0.5));
    }

    @Test
    @DisplayName("Test randomBooleanWithProbability with same seed")
    void randomBooleanWithProbabilitySameSeed() {
        assertEquals(random1.randomBooleanWithProbability(0.5), random1Duplicate.randomBooleanWithProbability(0.5));
    }
}
