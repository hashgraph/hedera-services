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

package com.hedera.node.app.state;

import com.swirlds.platform.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Validates a {@link HederaState}, checking to make sure the state is valid. This is used, for example, to verify that
 * no HBAR were lost during an upgrade. Implementations should execute quickly, because validation will delay restarts
 * and/or upgrades. Most validation will happen asynchronously. At the very least, validation should verify that no
 * HBARs were lost or gained.
 */
public interface LedgerValidator {
    /**
     * Performs some kind of validation on the {@link HederaState}.
     *
     * @param state The state to check
     * @throws IllegalStateException If the state is invalid.
     */
    void validate(@NonNull HederaState state) throws IllegalStateException;
}
