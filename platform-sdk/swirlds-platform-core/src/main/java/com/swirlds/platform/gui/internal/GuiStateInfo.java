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

package com.swirlds.platform.gui.internal;

import com.swirlds.platform.state.signed.ReservedSignedState;

/**
 * A simple DTO for passing information about a signed state to the GUI.
 *
 * @param round      the round number
 * @param numSigs    the number of signatures
 * @param isComplete whether the state is complete
 */
record GuiStateInfo(long round, int numSigs, boolean isComplete) {
    /**
     * Creates a new {@link GuiStateInfo} from the given {@link ReservedSignedState}.
     *
     * @param state the state
     * @return the new {@link GuiStateInfo}
     */
    static GuiStateInfo from(final ReservedSignedState state) {
        return new GuiStateInfo(
                state.get().getRound(),
                state.get().getSigSet().size(),
                state.get().isComplete());
    }
}
