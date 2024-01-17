/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.base.time.Time;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.creation.tipset.Tipset;
import com.swirlds.platform.event.creation.tipset.TipsetTracker;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the tipset of the latest self event. Must be thread safe, gossip will access it from multiple threads.
 */
public class LatestEventTipsetTracker {

    private final TipsetTracker tipsetTracker;
    private final NodeId selfId;
    private final AddressBook addressBook;
    private Tipset latestSelfEventTipset;

    /**
     * Constructor.
     *
     * @param time        provides wall clock time
     * @param addressBook the current address book
     * @param selfId      the ID of this node
     * @param ancientMode the {@link AncientMode} to use.
     */
    public LatestEventTipsetTracker(
            @NonNull final Time time,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final AncientMode ancientMode) {

        Objects.requireNonNull(time);
        Objects.requireNonNull(addressBook);

        this.tipsetTracker = new TipsetTracker(time, addressBook, ancientMode);
        this.selfId = Objects.requireNonNull(selfId);
        this.addressBook = addressBook;
    }

    /**
     * Update the non-ancient event window
     *
     * @param nonAncientEventWindow the non-ancient event window
     */
    public synchronized void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        tipsetTracker.setNonAncientEventWindow(nonAncientEventWindow);
    }

    /**
     * Get the tipset of the latest self event, or null if there have been no self events.
     *
     * @return the tipset of the latest self event, or null if there have been no self events
     */
    @Nullable
    public synchronized Tipset getLatestSelfEventTipset() {
        return latestSelfEventTipset;
    }

    /**
     * The event to add. Used to update tipsets.
     *
     * @param event The event to insert.
     */
    public synchronized void addEvent(final EventImpl event) {
        if (!addressBook.contains(event.getCreatorId())) {
            // Ignore this event. Possible in scenarios where a node is removed or a state is moved from a
            // network with a different address book.
            return;
        }

        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (event.getSelfParent() != null) {
            parentDescriptors.add(event.getSelfParent().getBaseEvent().getDescriptor());
        }
        if (event.getOtherParent() != null) {
            parentDescriptors.add(event.getOtherParent().getBaseEvent().getDescriptor());
        }
        final Tipset tipset = tipsetTracker.addEvent(event.getBaseEvent().getDescriptor(), parentDescriptors);
        if (event.getCreatorId().equals(selfId)) {
            latestSelfEventTipset = tipset;
        }
    }
}
