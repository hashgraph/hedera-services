/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.stream;

import static com.swirlds.common.stream.HashCalculatorTest.PAY_LOAD_SIZE_4;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.test.fixtures.stream.ObjectForTestStream;
import org.junit.jupiter.api.Test;

class RunningHashCalculatorTest {
    private static Cryptography cryptography = CryptographyHolder.get();

    @Test
    void runningHashTest() throws InterruptedException {
        final DigestType digestType = DigestType.SHA_384;
        final Hash initialHash = new ImmutableHash(new byte[digestType.digestLength()]);
        final RunningHashCalculatorForStream<ObjectForTestStream> runningHashCalculator =
                new RunningHashCalculatorForStream(cryptography);
        runningHashCalculator.setRunningHash(initialHash);

        Hash expected = initialHash;
        for (int i = 0; i < 100; i++) {
            ObjectForTestStream object = ObjectForTestStream.getRandomObjectForTestStream(PAY_LOAD_SIZE_4);
            runningHashCalculator.addObject(object);
            expected = cryptography.calcRunningHash(expected, object.getHash(), digestType);
            assertEquals(
                    expected,
                    runningHashCalculator.getRunningHash(),
                    "Actual runningHash doesn't match expected value");
        }
    }

    @Test
    void nullInitialHashTest() throws InterruptedException {
        final DigestType digestType = DigestType.SHA_384;
        final RunningHashCalculatorForStream<ObjectForTestStream> runningHashCalculator =
                new RunningHashCalculatorForStream(cryptography);
        runningHashCalculator.setRunningHash(null);

        Hash expected = null;
        for (int i = 0; i < 100; i++) {
            ObjectForTestStream object = ObjectForTestStream.getRandomObjectForTestStream(PAY_LOAD_SIZE_4);
            runningHashCalculator.addObject(object);
            expected = cryptography.calcRunningHash(expected, object.getHash(), digestType);
            assertEquals(
                    expected,
                    runningHashCalculator.getRunningHash(),
                    "Actual runningHash doesn't match expected value");
        }
    }

    @Test
    void newHashIsNullTest() {
        Exception exception = assertThrows(
                IllegalArgumentException.class,
                () -> cryptography.calcRunningHash(null, null, DigestType.SHA_384),
                "should throw IllegalArgumentException when newHashToAdd is null");
        assertTrue(
                exception.getMessage().contains("newHashToAdd is null"),
                "the exception should contain expected message");
    }
}
