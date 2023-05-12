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

package com.swirlds.demo.platform;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.Platform;
import com.swirlds.demo.platform.nft.NftLedgerStatistics;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class PlatformTestingToolStateTests {

    @AfterAll
    static void cleanup() {
        NftLedgerStatistics.unregister();
    }

    @Test
    @Tag(TIME_CONSUMING)
    void initStatistics() throws InterruptedException {
        final long delay = 16;
        final Platform platform = mock(Platform.class);
        PlatformTestingToolState.initStatistics(platform);
        final Optional<ThreadInfo> threadInfo = getThread(PlatformTestingToolState.STAT_TIMER_THREAD_NAME);
        TimeUnit.SECONDS.sleep(delay);
        assertTrue(threadInfo.isPresent(), "Thread shouldn't die due to NPE");
        final Thread.State state = threadInfo.get().getThreadState();
        final boolean isInExpectedState = Thread.State.TIMED_WAITING == state || Thread.State.RUNNABLE == state;
        assertTrue(isInExpectedState, "The timer task is either running or waiting to be run");
    }

    private static Optional<ThreadInfo> getThread(final String threadName) {
        final long[] threadIds = ManagementFactory.getThreadMXBean().getAllThreadIds();
        final ThreadInfo[] threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(threadIds);

        for (final ThreadInfo info : threadInfo) {
            if (info != null) {
                if (info.getThreadName().equals(threadName)) {
                    return Optional.of(info);
                }
            }
        }

        return Optional.empty();
    }
}
