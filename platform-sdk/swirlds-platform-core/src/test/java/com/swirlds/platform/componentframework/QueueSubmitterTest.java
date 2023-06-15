/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.componentframework;

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.platform.componentframework.internal.QueueSubmitter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QueueSubmitterTest {
    /**
     * A simple test, call methods on the {@link LongProcessor} and check that the queue contains the expected values.
     */
    @Test
    @SuppressWarnings("unchecked")
    void basicTest() throws InterruptedException {
        final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();
        final LongProcessor submitter =
                QueueSubmitter.create(LongProcessor.class, (BlockingQueue<Object>) (BlockingQueue<?>) queue);
        submitter.processLong(123);
        assertEquals(123, queue.poll());
        assertTrue(queue.isEmpty());
    }

    /**
     * Validates that the default methods on {@link QueueSubmitter} do not throw exceptions
     */
    @Test
    void defaultMethods() {
        final LongProcessor submitter = QueueSubmitter.create(LongProcessor.class, new LinkedBlockingQueue<>());

        assertDoesNotThrow(submitter::hashCode);
        assertDoesNotThrow(submitter::toString);
        assertDoesNotThrow(() -> submitter.equals(Mockito.mock(LongProcessor.class)));
        assertDoesNotThrow(submitter::getClass);
    }
}
