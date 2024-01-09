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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static com.hedera.node.app.service.contract.impl.hevm.HevmPropagatedCallFailure.NONE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.hevm.HevmPropagatedCallFailure;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wrapper that holds a reference to a {@link HevmPropagatedCallFailure} value. Added to the
 * {@link org.hyperledger.besu.evm.frame.MessageFrame} context so that such failures can be
 * propagated up the call stack.
 */
public class PropagatedCallFailureRef {
    private HevmPropagatedCallFailure failure = NONE;

    /**
     * Sets the failure value to the given value.
     *
     * @param failure the failure value to set
     */
    public void set(@NonNull final HevmPropagatedCallFailure failure) {
        this.failure = requireNonNull(failure);
    }

    /**
     * Returns the current failure value, if any, and ensures the reference is reset to {@link HevmPropagatedCallFailure#NONE}.
     *
     * @return the current failure value
     */
    public @NonNull HevmPropagatedCallFailure getAndClear() {
        final var maybeFailure = failure;
        failure = NONE;
        return maybeFailure;
    }
}
