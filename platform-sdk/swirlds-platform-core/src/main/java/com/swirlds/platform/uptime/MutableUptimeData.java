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

package com.swirlds.platform.uptime;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.UptimeData;
import com.swirlds.platform.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Mutable version of {@link UptimeData}.
 */
public interface MutableUptimeData extends UptimeData {

    /**
     * Record data about the most recent event received by a node.
     *
     * @param event the event
     */
    void recordLastEvent(@NonNull final EventImpl event);

    /**
     * Record data about the most recent judge received by a node.
     *
     * @param event the judge
     */
    void recordLastJudge(@NonNull final EventImpl event);

    /**
     * Start tracking data for a new node.
     *
     * @param node the node ID
     */
    void addNode(@NonNull final NodeId node);

    /**
     * Stop tracking data for a node.
     *
     * @param node the node ID
     */
    void removeNode(@NonNull final NodeId node);
}
