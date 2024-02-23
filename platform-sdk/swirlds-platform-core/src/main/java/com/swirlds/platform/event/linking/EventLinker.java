/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.linking;

import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;

/**
 * Responsible for linking {@link GossipEvent}s to their parents and creating an {@link EventImpl}
 */
public interface EventLinker extends Clearable, LoadableFromSignedState {
    /**
     * Submit an event that needs to be linked
     *
     * @param event
     * 		the event that needs linking
     */
    void linkEvent(GossipEvent event);

    /**
     * Update the generations used to determine what is considered an ancient event
     *
     * @param generations
     * 		the new generations
     */
    void updateGenerations(GraphGenerations generations);

    /**
     * @return true if there are any linked events available
     */
    boolean hasLinkedEvents();

    /**
     * Returns a previously submitted {@link GossipEvent} as a linked event. Should only be called if {@link
     * #hasLinkedEvents()} returns true.
     *
     * @return a linked event
     */
    EventImpl pollLinkedEvent();

    @Override
    default void clear() {}

    @Override
    default void loadFromSignedState(final SignedState signedState) {}
}
