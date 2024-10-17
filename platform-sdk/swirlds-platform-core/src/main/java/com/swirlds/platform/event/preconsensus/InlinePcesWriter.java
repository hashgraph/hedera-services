/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This object is responsible for writing preconsensus events to disk. It differs from {@link PcesWriter} in that it
 * writes events to disk and then outputs them once it ensures they are durable.
 */
public interface InlinePcesWriter {

    /**
     * Prior to this method being called, all events added to the preconsensus event stream are assumed to be events
     * read from the preconsensus event stream on disk. The events from the stream on disk are not re-written to the
     * disk, and are considered to be durable immediately upon ingest.
     */
    @InputWireLabel("done streaming pces")
    void beginStreamingNewEvents();

    /**
     * Write an event to the stream.
     *
     * @param event the event to be written
     * @return the event written
     */
    @InputWireLabel("events to write")
    @Nullable
    PlatformEvent writeEvent(@NonNull PlatformEvent event);

    /**
     * Inform the preconsensus event writer that a discontinuity has occurred in the preconsensus event stream.
     *
     * @param newOriginRound the round of the state that the new stream will be starting from
     */
    @InputWireLabel("discontinuity")
    void registerDiscontinuity(@NonNull Long newOriginRound);

    /**
     * Let the event writer know the current non-ancient event boundary. Ancient events will be ignored if added to the
     * event writer.
     *
     * @param nonAncientBoundary describes the boundary between ancient and non-ancient events
     */
    @InputWireLabel("event window")
    void updateNonAncientEventBoundary(@NonNull EventWindow nonAncientBoundary);

    /**
     * Set the minimum ancient indicator needed to be kept on disk.
     *
     * @param minimumAncientIdentifierToStore the minimum ancient indicator required to be stored on disk
     */
    @InputWireLabel("minimum identifier to store")
    void setMinimumAncientIdentifierToStore(@NonNull Long minimumAncientIdentifierToStore);
}
