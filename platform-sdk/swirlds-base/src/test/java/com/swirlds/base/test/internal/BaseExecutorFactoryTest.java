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

package com.swirlds.base.test.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.swirlds.base.internal.BaseExecutorFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class BaseExecutorFactoryTest {

    @Test
    void testSubmitRunnable() {
        // given
        final BaseExecutorFactory baseExecutorFactory = BaseExecutorFactory.getInstance();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        // when
        final Future<Void> future = baseExecutorFactory.submit(() -> countDownLatch.countDown());

        // then
        assertThatNoException().isThrownBy(() -> countDownLatch.await(5, TimeUnit.SECONDS));
        assertThatNoException().isThrownBy(() -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitCallable() {
        // given
        final BaseExecutorFactory baseExecutorFactory = BaseExecutorFactory.getInstance();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        // when
        final Future<Void> future = baseExecutorFactory.submit(() -> {
            countDownLatch.countDown();
            return null;
        });

        // then
        assertThatNoException().isThrownBy(() -> countDownLatch.await(5, TimeUnit.SECONDS));
        assertThatNoException().isThrownBy(() -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testSchedule() {
        // given
        final BaseExecutorFactory baseExecutorFactory = BaseExecutorFactory.getInstance();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        // when
        final ScheduledFuture<Void> future =
                baseExecutorFactory.schedule(() -> countDownLatch.countDown(), 10, TimeUnit.MILLISECONDS);

        // then
        assertThatNoException().isThrownBy(() -> countDownLatch.await(5, TimeUnit.SECONDS));
        assertThatNoException().isThrownBy(() -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testScheduleAtFixedRate() {
        // given
        final BaseExecutorFactory baseExecutorFactory = BaseExecutorFactory.getInstance();
        final CountDownLatch countDownLatch = new CountDownLatch(10);

        // when
        final ScheduledFuture<Void> future = baseExecutorFactory.scheduleAtFixedRate(
                () -> countDownLatch.countDown(), 10, 10, TimeUnit.MILLISECONDS);

        // then
        assertThatNoException().isThrownBy(() -> countDownLatch.await(5, TimeUnit.SECONDS));
        assertThatNoException().isThrownBy(() -> future.cancel(true));
        assertThat(future.isCancelled()).isTrue();
    }
}
