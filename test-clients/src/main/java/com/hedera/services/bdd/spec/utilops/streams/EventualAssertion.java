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
package com.hedera.services.bdd.spec.utilops.streams;

import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.time.Duration;

public abstract class EventualAssertion extends UtilOp {
    // Default timeout just a bit longer than the record file period of
    // hedera.recordStream.logPeriod=2 seconds
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3L);

    protected final EventualAssertionResult result;

    protected EventualAssertion() {
        this(DEFAULT_TIMEOUT);
    }

    protected EventualAssertion(final Duration timeout) {
        result = new EventualAssertionResult(timeout);
    }
}
