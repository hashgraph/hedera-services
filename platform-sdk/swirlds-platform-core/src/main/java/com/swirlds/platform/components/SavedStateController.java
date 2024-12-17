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

package com.swirlds.platform.components;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Controls which signed states should be written to disk based on input from other components.
 * <p>
 * This is intentionally split out of the component that actually saves the state. Saving the state can take a long
 * time, and we don't want the queue of states waiting to be handled by that component to grow large while we are
 * waiting to write a state. It is much better simply not to put states into that component's queue if we don't want to
 * write them to disk.
 */
public interface SavedStateController {
    /**
     * Determine if a signed state should be written to disk. If the state should be written, the state will be marked
     * and then written to disk outside the scope of this class.
     *
     * @param stateAndRound the state in question
     */
    @InputWireLabel("state to mark")
    @NonNull
    StateAndRound markSavedState(@NonNull StateAndRound stateAndRound);

    /**
     * Notifies the controller that a signed state was received from another node during reconnect. The controller saves
     * its timestamp and marks it to be written to disk.
     *
     * @param reservedSignedState the signed state that was received from another node during reconnect
     */
    @InputWireLabel("reconnect state")
    void reconnectStateReceived(@NonNull ReservedSignedState reservedSignedState);

    /**
     * This should be called at boot time when a signed state is read from the disk.
     *
     * @param signedState the signed state that was read from file at boot time
     */
    @InputWireLabel("state from disk")
    void registerSignedStateFromDisk(@NonNull SignedState signedState);
}
