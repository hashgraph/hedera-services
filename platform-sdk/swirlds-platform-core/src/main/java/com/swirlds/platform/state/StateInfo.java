/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import com.swirlds.common.Releasable;
import com.swirlds.platform.internal.EventImpl;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Holds a {@link State} and the last consensus event applied to it.
 */
public class StateInfo implements Releasable {
    /** the SwirldState root that this object describes */
    private final AtomicReference<State> state = new AtomicReference<>();

    /**
     * lastCons is a consensus event whose transactions have already been handled by state, as have all
     * consensus events before it in the consensus order.
     */
    private volatile EventImpl lastCons;

    /**
     * pass in everything that is stored in the StateInfo
     *
     * @param state
     * 		the state to feed transactions to
     * @param lastCons
     * 		the last consensus event, in consensus order
     */
    public StateInfo(final State state, final EventImpl lastCons) {
        this.initializeState(state);
        this.setLastCons(lastCons);
    }

    private StateInfo(final StateInfo stateToCopy) {
        this.initializeState(stateToCopy.getState().copy());
        this.setLastCons(stateToCopy.getLastCons());
    }

    /**
     * make a copy of this StateInfo, where it also makes a new copy of the state
     *
     * @return a copy of this StateInfo
     */
    public StateInfo copy() {
        return new StateInfo(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean release() {
        return state.get().release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDestroyed() {
        return state.get().isDestroyed();
    }

    /**
     * Returns the current state.
     */
    public State getState() {
        return state.get();
    }

    private void initializeState(final State newState) {
        final State currState = state.get();
        if (currState != null) {
            currState.release();
        }
        newState.reserve();
        this.state.set(newState);
    }

    /**
     * Sets the current state. Should only be invoked after a fast copy has been made.
     *
     * @param state
     * 		the new state
     */
    protected void setState(final State state) {
        this.state.set(state);
    }

    /**
     * Returns the last consensus event applied to this state.
     */
    public EventImpl getLastCons() {
        return lastCons;
    }

    public void setLastCons(final EventImpl lastCons) {
        this.lastCons = lastCons;
    }

    /**
     * Updates the state object using the given {@code updateFunction}. This method uses {@link
     * AtomicReference#getAndUpdate(UnaryOperator)} to update the state, so the update function must not have any side
     * effects.
     *
     * @param updateFunction
     * 		the side effect free update function to apply to the state
     */
    protected void updateState(final UnaryOperator<State> updateFunction) {
        state.getAndUpdate(updateFunction);
    }
}
