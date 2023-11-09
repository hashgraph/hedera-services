/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.gossip.FallenBehindManager;
import java.util.List;

public class TestingSyncManager implements FallenBehindManager {
    /** whether we have fallen behind or not */
    private boolean fallenBehind = false;

    @Override
    public void reportFallenBehind(NodeId id) {
        // for testing, we conclude we have fallen behind even if just 1 node says so
        fallenBehind = true;
    }

    @Override
    public void resetFallenBehind() {
        fallenBehind = false;
    }

    @Override
    public boolean hasFallenBehind() {
        return fallenBehind;
    }

    @Override
    public List<NodeId> getNeighborsForReconnect() {
        return null;
    }

    @Override
    public List<NodeId> getNeededForFallenBehind() {
        return null;
    }

    @Override
    public int numReportedFallenBehind() {
        return 0;
    }

    @Override
    public boolean shouldReconnectFrom(final NodeId peerId) {
        return false;
    }
}
