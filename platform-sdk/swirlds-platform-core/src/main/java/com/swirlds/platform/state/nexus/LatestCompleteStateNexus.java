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

package com.swirlds.platform.state.nexus;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A nexus that holds the latest complete signed state.
 */
public interface LatestCompleteStateNexus extends SignedStateNexus {
    /**
     * Notify the nexus that a new signed state has been created. This is useful for the nexus to know when it should
     * clear the latest complete state. This is used so that we don't hold the latest complete state forever in case we
     * have trouble gathering signatures.
     *
     * @param newStateRound a new signed state round that is not yet complete
     */
    @InputWireLabel("incomplete state")
    void newIncompleteState(@NonNull final Long newStateRound);

    /**
     * Replace the current state with the given state if the given state is newer than the current state.
     *
     * @param reservedSignedState the new state
     */
    @InputWireLabel("complete state")
    void setStateIfNewer(@NonNull final ReservedSignedState reservedSignedState);
}
