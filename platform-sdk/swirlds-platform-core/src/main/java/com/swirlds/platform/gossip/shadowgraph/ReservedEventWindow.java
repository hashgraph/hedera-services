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

package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.platform.consensus.NonAncientEventWindow;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * An event window that has been reserved in the shadowgraph. While this reservation is held, the shadowgraph will not
 * unlink any events that are non-expired with respect to the reserved window.
 */
public class ReservedEventWindow implements AutoCloseable {
    private final NonAncientEventWindow eventWindow;
    private final ShadowgraphReservation shadowgraphReservation;

    /**
     * Constructor.
     *
     * @param eventWindow            the event window
     * @param shadowgraphReservation the shadowgraph reservation
     */
    public ReservedEventWindow(
            @NonNull final NonAncientEventWindow eventWindow,
            @NonNull final ShadowgraphReservation shadowgraphReservation) {
        this.eventWindow = Objects.requireNonNull(eventWindow);
        this.shadowgraphReservation = Objects.requireNonNull(shadowgraphReservation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        shadowgraphReservation.close();
    }

    /**
     * Get the reserved event window. No events will be unlinked that are non-expired with respect to this window, as
     * long as this reservation is held.
     *
     * @return the reserved event window
     */
    @NonNull
    public NonAncientEventWindow getEventWindow() {
        return eventWindow;
    }

    /**
     * Get the number of reservations in the underlying shadowgraph reservation. Exposed for unit tests.
     *
     * @return the number of reservations
     */
    public long getReservationCount() {
        return shadowgraphReservation.getReservationCount();
    }
}
