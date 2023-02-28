/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect.emergency;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.SignedState;

@FunctionalInterface
public interface EmergencyStateFinder {

    /**
     * <p>
     * Finds a signed state for the given round number and hash even if it is not fully signed, or a later round that is
     * signed by more than half the network stake. If such a state is not available, return null.
     * </p>
     *
     * <p>
     * The returned state is guaranteed to hold a reservation, which should be released by the caller when finished.
     * </p>
     *
     * @param round the round of the state to find
     * @param hash  the hash of the state to find
     * @return the requested state, or a later state that is fully signed, or null if no such state is available
     */
    AutoCloseableWrapper<SignedState> find(final long round, final Hash hash);
}
