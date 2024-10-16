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

package com.hedera.services.bdd.spec.utilops.streams;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.utilops.streams.assertions.AssertionResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EventualAssertionResult {
    private final boolean hasPassedIfNothingFailed;
    private final Duration timeout;

    private final CountDownLatch ready = new CountDownLatch(1);

    private AssertionResult result;

    public EventualAssertionResult(final boolean hasPassedIfNothingFailed, @NonNull final Duration timeout) {
        this.hasPassedIfNothingFailed = hasPassedIfNothingFailed;
        this.timeout = requireNonNull(timeout);
    }

    public AssertionResult get() throws InterruptedException {
        if (!ready.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            if (hasPassedIfNothingFailed && result == null) {
                return AssertionResult.success();
            } else {
                return AssertionResult.timeout(timeout);
            }
        }
        return result;
    }

    public void pass() {
        this.result = AssertionResult.success();
        ready.countDown();
    }

    public void fail(final String reason) {
        this.result = AssertionResult.failure(reason);
        ready.countDown();
    }
}
