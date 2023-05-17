/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.clock;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public class TimeSlotCalculator {

    // TODO: Read from consensus.handle.maxPrecedingRecords
    private static final long MAX_PRECEEDINGS = 3;

    // TODO: Read from consensus.handle.maxFollowingRecords=50
    private static final long MAX_CHILDREN = 50;

    private final Instant baseConsensusTime;

    private long precedings;
    private long children;

    public TimeSlotCalculator(Instant baseConsensusTime) {
        this.baseConsensusTime = requireNonNull(baseConsensusTime, "baseConsensusTime");
    }

    public Instant getNextAvailablePrecedingSlot() {
        if (precedings >= MAX_PRECEEDINGS) {
            throw new IllegalStateException("No more preceding slots available");
        }
        return baseConsensusTime.minusNanos(3 - precedings++);
    }

    public Instant getNextAvailableChildSlot() {
        if (children >= MAX_CHILDREN) {
            throw new IllegalStateException("No more child slots available");
        }
        return baseConsensusTime.plusNanos(++children);
    }
}
