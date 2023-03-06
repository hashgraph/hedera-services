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
import java.util.List;

public interface SyncManager {
    boolean shouldAcceptSync();

    boolean shouldInitiateSync();

    List<Long> getNeighborsToCall();

    boolean shouldCreateEvent(NodeId otherId, boolean oneNodeFallenBehind, int eventsRead, int eventsWritten);

    boolean shouldCreateEvent(final SyncResult info);

    void successfulSync();
}
