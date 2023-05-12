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

package com.swirlds.platform.health;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.completeBeforeTimeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OSHealthCheckUtilsTests {

    @Test
    void testTimeout() {
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final Supplier<Boolean> supplier = () -> {
            try {
                doneLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return true;
        };

        assertEventuallyEquals(
                null,
                () -> {
                    try {
                        return OSHealthCheckUtils.timeSupplier(supplier, 100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofMillis(200),
                "");

        doneLatch.countDown();
    }

    @Test
    void testCompletesInTime() throws InterruptedException {
        final Random r = RandomUtils.getRandom();
        final int randomInt = r.nextInt();
        final Supplier<Integer> supplier = () -> randomInt;

        final AtomicReference<OSHealthCheckUtils.SupplierResult<Integer>> checkResult = new AtomicReference<>();

        final InterruptableRunnable runCheck = () -> checkResult.set(OSHealthCheckUtils.timeSupplier(supplier, 100));

        completeBeforeTimeout(runCheck, Duration.ofMillis(300), "Check should have completed by now");

        assertNotNull(checkResult.get(), "Timout result (null) not excepted");
        assertEquals(randomInt, checkResult.get().result(), "Invalid result");
    }
}
