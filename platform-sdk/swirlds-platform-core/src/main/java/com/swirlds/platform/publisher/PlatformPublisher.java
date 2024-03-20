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

package com.swirlds.platform.publisher;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This component is responsible for publishing internal platform data to external subscribers. By default this is not
 * enabled, and will only publish data if handler methods are registered with the platform at startup time.
 */
public interface PlatformPublisher {

    /**
     * Publish a preconsensus event.
     *
     * @param event the event to publish
     */
    @InputWireLabel("GossipEvent")
    void publishPreconsensusEvent(@NonNull final GossipEvent event);

    /**
     * Publish a consensus snapshot override (i.e. what happens when we start from a node state at restart/reconnect
     * boundaries).
     *
     * @param snapshot the snapshot to publish
     */
    @InputWireLabel("ConsensusSnapshot")
    void publishSnapshotOverride(@NonNull final ConsensusSnapshot snapshot);
}
