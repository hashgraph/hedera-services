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

package com.swirlds.platform.event.deduplication;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Deduplicates events.
 * <p>
 * A duplicate event is defined as an event with an identical descriptor and identical signature to an event that has
 * already been observed.
 * <p>
 * It is necessary to consider the signature bytes when determining if an event is a duplicate, not just the descriptor
 * or hash. This guards against a malicious node gossiping the same event with different signatures, or a node gossiping
 * another node's event with a modified signature. If we went only off the descriptor or hash, we might discard the
 * correct version of an event as a duplicate, because a malicious version has already been received. Instead, the
 * deduplicator lets all versions of the event through that have a unique descriptor/signature pair, and the signature
 * validator further along the pipeline will handle discarding bad versions.
 */
public interface EventDeduplicator {

    /**
     * Handle a potentially duplicate event
     * <p>
     * Ancient events are ignored. If the input event has not already been observed by this deduplicator, it is
     * returned.
     *
     * @param event the event to handle
     * @return the event if it is not a duplicate, or null if it is a duplicate
     */
    @Nullable
    @InputWireLabel("non-deduplicated events")
    PlatformEvent handleEvent(@NonNull PlatformEvent event);

    /**
     * Set the EventWindow, defines the minimum threshold for an event to be non-ancient.
     *
     * @param eventWindow the event window
     */
    @InputWireLabel("event window")
    void setEventWindow(@NonNull EventWindow eventWindow);

    /**
     * Clear the internal state of this deduplicator.
     *
     * @param ignored ignored trigger object
     */
    void clear(@NonNull final NoInput ignored);
}
