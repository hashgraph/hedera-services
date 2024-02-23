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

package com.swirlds.platform.network.communication.states;

/**
 * An abstract {@link NegotiationState} that stores a variable with the latest transition description
 */
public abstract class NegotiationStateWithDescription implements NegotiationState {
    private String description = "NO TRANSITION";

    /**
     * Set the description that is returned by {@link #getLastTransitionDescription()}
     *
     * @param description
     * 		the description
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    @Override
    public String getLastTransitionDescription() {
        return description;
    }
}
