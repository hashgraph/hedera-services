/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.creation.tipset;

import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Misc tipset utilities.
 */
public final class TipsetUtils {

    private TipsetUtils() {}

    /**
     * Build a descriptor from an EventImpl.
     *
     * @param event the event
     * @return the descriptor
     */
    public static EventDescriptor buildDescriptor(@NonNull final EventImpl event) {
        if (event.getBaseHash() == null) {
            throw new IllegalStateException("event is not hashed");
        }
        return new EventDescriptor(event.getBaseHash(), event.getCreatorId(), event.getGeneration());
    }

    /**
     * Build a descriptor from a GossipEvent.
     *
     * @param event the event
     * @return the descriptor
     */
    public static EventDescriptor buildDescriptor(@NonNull final GossipEvent event) {
        event.buildDescriptor();
        return event.getDescriptor();
    }

    /**
     * Get the descriptors of an event's parents.
     *
     * @param event the event to get parent descriptors for
     */
    @NonNull
    public static List<EventDescriptor> getParentDescriptors(@NonNull final EventImpl event) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (event.getSelfParent() != null) {
            parentDescriptors.add(buildDescriptor(event.getSelfParent()));
        }
        if (event.getOtherParent() != null) {
            parentDescriptors.add(buildDescriptor(event.getOtherParent()));
        }
        return parentDescriptors;
    }

    /**
     * Get the descriptors of an event's parents.
     *
     * @param event the event to the parent descriptors for
     * @return a list of parent descriptors
     */
    @NonNull
    public static List<EventDescriptor> getParentDescriptors(@NonNull final GossipEvent event) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);

        if (event.getHashedData().getSelfParentHash() != null) {
            final EventDescriptor parent = new EventDescriptor(
                    event.getHashedData().getSelfParentHash(),
                    event.getHashedData().getCreatorId(),
                    event.getHashedData().getSelfParentGen());
            parentDescriptors.add(parent);
        }
        if (event.getHashedData().getOtherParentHash() != null) {
            final EventDescriptor parent = new EventDescriptor(
                    event.getHashedData().getOtherParentHash(),
                    event.getUnhashedData().getOtherId(),
                    event.getHashedData().getOtherParentGen());
            parentDescriptors.add(parent);
        }

        return parentDescriptors;
    }
}
