/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.state.signed.SignedStateUtilities.newSignedStateWrapper;

import com.swirlds.common.utility.AutoCloseableWrapper;

/**
 * Similar to an {@link java.util.concurrent.atomic.AtomicReference AtomicReference&lt;SignedState&gt;}, but with the
 * additional functionality of holding a reservation on the contained state.
 */
public class SignedStateReference {

    private SignedState signedState;

    /**
     * Create a new signed state reference with a null initial state.
     */
    public SignedStateReference() {}

    /**
     * Create a new signed state reference with an initial state.
     *
     * @param signedState a signed state
     */
    public SignedStateReference(final SignedState signedState) {
        set(signedState);
    }

    /**
     * Set a signed state, replacing the existing state.
     *
     * @param signedState a signed state, may be null
     */
    public synchronized void set(final SignedState signedState) {
        if (signedState == this.signedState) {
            // Same object, no action required
            return;
        }

        if (this.signedState != null) {
            this.signedState.release();
        }
        if (signedState != null) {
            signedState.reserve();
        }
        this.signedState = signedState;
    }

    /**
     * Check if the current value referenced by this object is null.
     *
     * @return true if the referenced state is null
     */
    public synchronized boolean isNull() {
        return signedState == null;
    }

    /**
     * Get the round of the signed state contained by this reference.
     *
     * @return the signed state's round, or -1 if this object references a null object
     */
    public synchronized long getRound() {
        return signedState == null ? -1 : signedState.getRound();
    }

    /**
     * Get the signed state and take a reservation on it.
     *
     * @return an autocloseable wrapper with the state, when closed the state is released
     */
    public AutoCloseableWrapper<SignedState> get() {
        final SignedState stateToReturn = signedState;

        return newSignedStateWrapper(stateToReturn);
    }
}
