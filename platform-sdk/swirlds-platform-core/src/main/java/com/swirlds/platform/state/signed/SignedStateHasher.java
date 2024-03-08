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

package com.swirlds.platform.state.signed;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Hashes signed states
 */
@FunctionalInterface
public interface SignedStateHasher {
    /**
     * Hashes a SignedState.
     *
     * @param stateAndRound the state and round, which contains the state to hash
     * @return the same state and round, with the state hashed
     */
    @InputWireLabel("unhashed state and round")
    @Nullable
    StateAndRound hashState(@NonNull StateAndRound stateAndRound);
}
