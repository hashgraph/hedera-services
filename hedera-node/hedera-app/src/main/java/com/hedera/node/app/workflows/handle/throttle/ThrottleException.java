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

package com.hedera.node.app.workflows.handle.throttle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.THROTTLED_AT_CONSENSUS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A custom exception to be thrown when a request is throttled at consensus.
 */
public class ThrottleException extends Exception {
    private final ResponseCodeEnum status;

    /**
     * Creates a new ThrottleException with status CONSENSUS_GAS_EXHAUSTED.
     * @return the new ThrottleException
     */
    public static ThrottleException newGasThrottleException() {
        return new ThrottleException(CONSENSUS_GAS_EXHAUSTED);
    }

    /**
     * Creates a new ThrottleException with the status THROTTLED_AT_CONSENSUS.
     * @return the new ThrottleException
     */
    public static ThrottleException newNativeThrottleException() {
        return new ThrottleException(THROTTLED_AT_CONSENSUS);
    }

    private ThrottleException(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status);
    }

    /**
     * Gets the status of the exception.
     *
     * @return the status
     */
    public ResponseCodeEnum getStatus() {
        return status;
    }
}
