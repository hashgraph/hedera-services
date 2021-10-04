package com.hedera.services.disruptor;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith({ MockitoExtension.class })
class LatchTest {
    @Test
    void awaitFirst() throws InterruptedException {
        Latch latch = new Latch();

        AtomicInteger i = new AtomicInteger();
        Runnable task2 = () -> {
            try {
                latch.await();
                i.incrementAndGet();
            } catch (InterruptedException e) {
            }
        };
        Thread t2 = new Thread(task2);
        t2.start();

        Runnable task1 = () -> { latch.countdown(); };
        Thread t1 = new Thread(task1);
        t1.start();

        t2.join();
        t1.join();

        assertEquals(1, i.get());
    }

    @Test
    void countdownBeforeAwait() throws InterruptedException {
        Latch latch = new Latch();

        Runnable task1 = () -> {
            latch.countdown();
        };
        Thread t1 = new Thread(task1);
        t1.start();

        AtomicInteger i = new AtomicInteger();
        Runnable task2 = () -> {
            try {
                latch.await();
                i.incrementAndGet();
            } catch (InterruptedException e) {
            }
        };
        Thread t2 = new Thread(task2);
        t2.start();

        t2.join();
        t1.join();

        assertEquals(1, i.get());
    }
}
