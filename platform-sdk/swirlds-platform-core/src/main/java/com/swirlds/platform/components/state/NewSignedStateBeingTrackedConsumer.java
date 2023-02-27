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

package com.swirlds.platform.components.state;

import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;

/**
 * Invoked when a new {@link SignedState} is being tracked by the
 * {@link com.swirlds.platform.state.signed.SignedStateManager SignedStateManager}. State is guaranteed to hold a
 * reservation until the end of execution.
 */
@FunctionalInterface
public interface NewSignedStateBeingTrackedConsumer {

    /**
     * A new signed state is being tracked by the {@link com.swirlds.platform.state.signed.SignedStateManager}.
     *
     * @param signedState the signed state now being tracked
     * @param source      the source of the signed state
     */
    void newStateBeingTracked(SignedState signedState, SourceOfSignedState source);
}
