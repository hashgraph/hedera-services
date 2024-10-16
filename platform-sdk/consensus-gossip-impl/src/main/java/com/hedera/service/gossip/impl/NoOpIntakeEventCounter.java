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

package com.hedera.service.gossip.impl;

import com.hedera.service.gossip.IntakeEventCounter;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A no-op implementation of {@link IntakeEventCounter}.
 */
public class NoOpIntakeEventCounter implements IntakeEventCounter {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnprocessedEvents(@NonNull NodeId peer) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventEnteredIntakePipeline(@NonNull NodeId peer) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventExitedIntakePipeline(@Nullable NodeId peer) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        // no-op
    }
}
