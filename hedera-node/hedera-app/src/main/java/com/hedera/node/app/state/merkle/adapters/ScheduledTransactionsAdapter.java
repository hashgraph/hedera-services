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

package com.hedera.node.app.state.merkle.adapters;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.logic.ScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;

public class ScheduledTransactionsAdapter implements ScheduledTransactions {
    private final MerkleScheduledTransactionsState state;
    private final MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> byId;
    private final MerkleMapLike<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirySec;
    private final MerkleMapLike<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality;

    public ScheduledTransactionsAdapter(
            MerkleScheduledTransactionsState state,
            MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> byId,
            MerkleMapLike<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirySec,
            MerkleMapLike<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality) {
        this.state = state;
        this.byId = byId;
        this.byExpirySec = byExpirySec;
        this.byEquality = byEquality;
    }

    @Override
    public void setCurrentMinSecond(final long currentMinSecond) {
        state.setCurrentMinSecond(currentMinSecond);
    }

    @Override
    public long getCurrentMinSecond() {
        return state.currentMinSecond();
    }

    @Override
    public long getNumSchedules() {
        return byId.size();
    }

    @Override
    public MerkleMapLike<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality() {
        return byEquality;
    }

    @Override
    public MerkleMapLike<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirationSecond() {
        return byExpirySec;
    }

    @Override
    public MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> byId() {
        return byId;
    }

    @Override
    public MerkleScheduledTransactionsState state() {
        return state;
    }
}
