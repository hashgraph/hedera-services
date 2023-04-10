/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.notification;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.notification.internal.Dispatcher;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

class DispatcherTests {

    @Test
    @Tags({@Tag(TestTypeTags.FUNCTIONAL), @Tag(TestComponentTags.NOTIFICATION)})
    @DisplayName("Notification Engine: Dispatcher Sync Exception Handling")
    void validateSyncExceptionHandling() throws InterruptedException, TimeoutException {
        final Dispatcher<SyncOrderedIntegerListener> syncDispatcher =
                new Dispatcher<>(getStaticThreadManager(), SyncOrderedIntegerListener.class);

        syncDispatcher.addListener((n) -> {
            throw new RuntimeException(n.toString());
        });

        final IntegerNotification notification = new IntegerNotification(1);

        final StandardFuture<NotificationResult<IntegerNotification>> future = new StandardFuture<>();
        syncDispatcher.notifySync(notification, future::complete);

        final NotificationResult<IntegerNotification> result = future.getAndRethrow(5, TimeUnit.SECONDS);

        assertNotNull(result, "The notification result was null but expected not null");
        assertEquals(
                1,
                result.getFailureCount(),
                String.format(
                        "The result should contain 1 failure but instead contained %d failures",
                        result.getFailureCount()));
        assertEquals(
                1,
                result.getExceptions().size(),
                String.format(
                        "The result should contain 1 exception but instead contained %d exceptions",
                        result.getExceptions().size()));
        assertEquals(
                RuntimeException.class,
                result.getExceptions().get(0).getClass(),
                String.format(
                        "The result should contain a single RuntimeException but instead contained a %s",
                        result.getExceptions().get(0).getClass().getSimpleName()));
    }

    @Test
    @Tags({@Tag(TestTypeTags.FUNCTIONAL), @Tag(TestComponentTags.NOTIFICATION)})
    @DisplayName("Notification Engine: Dispatcher ASync Exception Handling")
    void validateASyncExceptionHandling() throws InterruptedException, TimeoutException {
        final Dispatcher<AsyncOrderedIntegerListener> asyncDispatcher =
                new Dispatcher<>(getStaticThreadManager(), AsyncOrderedIntegerListener.class);

        asyncDispatcher.addListener((n) -> {
            throw new RuntimeException(n.toString());
        });

        final StandardFuture<NotificationResult<IntegerNotification>> future = new StandardFuture<>();
        asyncDispatcher.notifyAsync(new IntegerNotification(1), future::complete);

        final NotificationResult<IntegerNotification> result = future.getAndRethrow(5, TimeUnit.SECONDS);

        assertNotNull(result, "The notification result was null but expected not null");
        assertEquals(
                1,
                result.getFailureCount(),
                String.format(
                        "The result should contain 1 failure but instead contained %d failures",
                        result.getFailureCount()));
        assertEquals(
                1,
                result.getExceptions().size(),
                String.format(
                        "The result should contain 1 exception but instead contained %d exceptions",
                        result.getExceptions().size()));
        assertEquals(
                RuntimeException.class,
                result.getExceptions().get(0).getClass(),
                String.format(
                        "The result should contain a single RuntimeException but instead contained a %s",
                        result.getExceptions().get(0).getClass().getSimpleName()));
    }
}
