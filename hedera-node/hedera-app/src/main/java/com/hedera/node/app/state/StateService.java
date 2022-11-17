/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.swirlds.common.system.StateAccessor;

/**
 * Service that provides access to the state.
 *
 * <p>The main responsibility of this class is to encapsulate {@link
 * com.swirlds.common.system.Platform}-related classes.
 */
public class StateService {

    private final StateAccessor stateAccessor;

    /**
     * Constructor of {@code StateService}
     *
     * @param stateAccessor access to the platform-state
     */
    public StateService(final StateAccessor stateAccessor) {
        this.stateAccessor = requireNonNull(stateAccessor);
    }

    /**
     * Returns the latest immutable state. This is used in pre-handle transaction workflow and in
     * queries.
     *
     * @return the latest immutable {@link HederaState}
     */
    public HederaState getLatestImmutableState() {
        return new HederaState(stateAccessor.getLatestImmutableState());
    }

    /**
     * Returns the latest signed state. This is used in the handle transaction workflow.
     *
     * @return the latest signed {@link HederaState}
     */
    public HederaState getLatestSignedState() {
        return new HederaState(stateAccessor.getLatestSignedState());
    }
}
