/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import com.swirlds.common.test.RandomUtils;
import org.junit.jupiter.api.Test;

class HashCalculatorTest {
    static final int PAY_LOAD_SIZE_4 = 4;
    private static final ObjectForTestStream object = ObjectForTestStream.getRandomObjectForTestStream(PAY_LOAD_SIZE_4);

    @Test
    void nextStreamTest() throws InterruptedException {
        LinkedObjectStream<ObjectForTestStream> queueThread = mock(QueueThreadObjectStream.class);
        HashCalculatorForStream<ObjectForTestStream> hashCalculator = new HashCalculatorForStream<>(queueThread);
        Hash hash = RandomUtils.randomHash();
        hashCalculator.setRunningHash(hash);
        verify(queueThread).setRunningHash(hash);

        hashCalculator.addObject(object);
        verify(queueThread).addObject(object);

        hashCalculator.clear();
        verify(queueThread).clear();

        hashCalculator.close();
        verify(queueThread).close();
    }

    @Test
    void calculateHashTest() throws InterruptedException {
        HashCalculatorForStream<ObjectForTestStream> hashCalculator = new HashCalculatorForStream<>();
        assertNull(object.getHash(), "the object's Hash should be null after initialization");
        // calculate expected Hash
        Hash expected = CryptographyHolder.get().digestSync((SelfSerializable) object);
        assertNotNull(expected, "the object's expected Hash should not be null");
        assertNull(object.getHash(), "the object's Hash should be null after calculated expected Hash");
        // hashCalculator calculates and set Hash for this object
        hashCalculator.addObject(object);
        assertEquals(
                expected,
                object.getHash(),
                "the object's Hash should match expected Hash after hashCalculator processes it");
    }

    @Test
    void addNullObjectTest() throws InterruptedException {
        LinkedObjectStream<ObjectForTestStream> queueThread = mock(QueueThreadObjectStream.class);
        HashCalculatorForStream<ObjectForTestStream> hashCalculator = new HashCalculatorForStream<>(queueThread);
        assertThrows(
                NullPointerException.class,
                () -> hashCalculator.addObject(null),
                "should throw exception when adding null to hashCalculator");

        // add(null) object should not be called on its nextStream
        verify(queueThread, never()).addObject(null);
    }
}
