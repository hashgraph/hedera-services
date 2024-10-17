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

package com.hedera.services.bdd.junit.hedera.subprocess;

import static com.swirlds.common.PlatformStatus.*;

import com.swirlds.common.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A record of an attempt to look up the status of a Hedera node.
 *
 * @param source the source of the status lookup attempt
 * @param status if non-null, the status of the node
 * @param failureReason if non-null, the reason the status lookup failed
 */
public record StatusLookupAttempt(
        @NonNull Source source, @Nullable PlatformStatus status, @Nullable String failureReason) {

    /**
     * The source of the status lookup attempt.
     */
    public enum Source {
        PROMETHEUS,
        APPLICATION_LOG
    }

    /**
     * Creates a new Prometheus-based {@link StatusLookupAttempt} from the given status and failure reason.
     *
     * @param status the status of the node
     * @param failureReason the reason the status lookup failed
     * @return a new {@link StatusLookupAttempt}
     */
    public static StatusLookupAttempt newPrometheusAttempt(@Nullable String status, @Nullable String failureReason) {
        return new StatusLookupAttempt(Source.PROMETHEUS, status == null ? null : valueOf(status), failureReason);
    }

    /**
     * Creates a new log-based {@link StatusLookupAttempt} from the given status and failure reason.
     *
     * @param status the status of the node
     * @param failureReason the reason the status lookup failed
     * @return a new {@link StatusLookupAttempt}
     */
    public static StatusLookupAttempt newLogAttempt(@Nullable String status, @Nullable String failureReason) {
        return new StatusLookupAttempt(Source.APPLICATION_LOG, status == null ? null : valueOf(status), failureReason);
    }
}
