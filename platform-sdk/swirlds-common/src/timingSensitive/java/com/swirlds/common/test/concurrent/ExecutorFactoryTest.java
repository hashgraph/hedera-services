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

package com.swirlds.common.test.concurrent;

import com.swirlds.base.context.Context;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.concurrent.internal.DefaultExecutorFactory;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import com.swirlds.logging.test.fixtures.WithLoggingMirror;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@WithLoggingMirror
public class ExecutorFactoryTest {

    private static final Logger log = Loggers.getLogger(ExecutorFactoryTest.class);

    @Test
    @Disabled("This test needs to be investigated")
    void test(LoggingMirror mirror) {
        // given
        final String platformId = "platform-123";
        final CountDownLatch latch = new CountDownLatch(3);
        final ExecutorFactory factory = getFactoryForPlatform(platformId);

        // when
        final ExecutorService executor = factory.createExecutorService(4);
        executor.execute(() -> {
            log.info("message 1");
            latch.countDown();
        });
        executor.execute(() -> {
            try {
                throw new RuntimeException("This is a sample exception");
            } finally {
                latch.countDown();
            }
        });

        factory.createThread(() -> {
                    log.info("message 2");
                    latch.countDown();
                })
                .start();

        // then
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Assertions.fail("Interrupted while waiting for latch", e);
        }

        Assertions.assertEquals(3, mirror.getEventCount());
        Assertions.assertEquals(
                1,
                mirror.filter(event -> event.message().getMessage().equals("message 1"))
                        .getEventCount());
        Assertions.assertEquals(
                1,
                mirror.filter(event -> event.message().getMessage().equals("message 2"))
                        .getEventCount());
        Assertions.assertEquals(
                1, mirror.filter(event -> event.throwable() != null).getEventCount());
        Assertions.assertEquals(
                3,
                mirror.filter(event -> event.context().containsKey("platformId"))
                        .getEventCount());
        Assertions.assertEquals(
                3,
                mirror.filter(event -> event.context().get("platformId").equals("platform-123"))
                        .getEventCount());
    }

    public static ExecutorFactory getFactoryForPlatform(final String platformId) {
        final String groupName = "platform-group";
        final Runnable onStartup = () -> {
            Context.getThreadLocalContext().add("platformId", platformId);
        };
        final UncaughtExceptionHandler handler = (t, e) -> {
            log.withContext("platformId", platformId).error("Uncaught exception in thread: " + t.getName(), e);
        };
        return DefaultExecutorFactory.create(groupName, onStartup, handler);
    }
}
