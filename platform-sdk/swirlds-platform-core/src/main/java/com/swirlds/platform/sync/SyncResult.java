/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

import com.swirlds.common.system.NodeId;

/**
 * Information about a successful sync that just occurred
 */
public class SyncResult {
    private final boolean caller;
    private final NodeId otherId;
    private final int eventsRead;
    private final int eventsWritten;

    /**
     * @param caller
     * 		true if this node initiated the sync, false otherwise
     * @param otherId
     * 		the ID of the node we synced with
     * @param eventsRead
     * 		the number of events read during the sync
     * @param eventsWritten
     * 		the number of events written during the sync
     */
    public SyncResult(final boolean caller, final NodeId otherId, final int eventsRead, final int eventsWritten) {
        this.caller = caller;
        this.otherId = otherId;
        this.eventsRead = eventsRead;
        this.eventsWritten = eventsWritten;
    }

    /**
     * @return true if this node initiated the sync, false otherwise
     */
    public boolean isCaller() {
        return caller;
    }

    /**
     * @return the ID of the node we synced with
     */
    public NodeId getOtherId() {
        return otherId;
    }

    /**
     * @return the number of events read during the sync
     */
    public int getEventsRead() {
        return eventsRead;
    }

    /**
     * @return the number of events written during the sync
     */
    public int getEventsWritten() {
        return eventsWritten;
    }
}
