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

package com.swirlds.platform.components.common.output;

import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;

/**
 * Invoked when a signed state needs to be loaded into the system. The signed state is guaranteed to hold a strong
 * reservation for the duration of this consumer's execution. If the consumer requires the state to be available after
 * execution, it must take out its own reservation.
 */
@FunctionalInterface
public interface SignedStateToLoadConsumer {

    /**
     * A signed state has been acquired and needs to be loaded into the system.
     * <p>
     * The signed state holds a reservation for the duration of this call. Implementers must not release this
     * reservation.
     *
     * @param signedState the signed state to load
     * @param source      the source of the signed state
     */
    void stateToLoad(final SignedState signedState, final SourceOfSignedState source);
}
