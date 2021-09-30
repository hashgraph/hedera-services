package com.hedera.services.disruptor;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.SwirldDualState;

import java.time.Instant;

/**
 * Class used to store data to process by pre-consensus and consensus event handlers. The disruptor
 * ring is pre-allocated with objects created from this class to eliminate need to instantiate this
 * class during runtime.
 */
public class TransactionEvent {
    long submittingMember;
    boolean isConsensus;
    Instant creationTime;
    Instant consensusTime;
    PlatformTxnAccessor accessor;
    SwirldDualState dualState;
    boolean errored = false;

    public long getSubmittingMember() {
        return submittingMember;
    }

    public void setSubmittingMember(long submittingMember) {
        this.submittingMember = submittingMember;
    }

    public boolean isConsensus() { return isConsensus; }

    public void setConsensus(boolean isConsensus) { this.isConsensus = isConsensus; }

    public Instant getCreationTime() { return creationTime; }

    public void setCreationTime(Instant creationTime) { this.creationTime = creationTime; }

    public Instant getConsensusTime() { return consensusTime; }

    public void setConsensusTime(Instant consensusTime) { this.consensusTime = consensusTime; }

    public PlatformTxnAccessor getAccessor() { return accessor; }

    public void setAccessor(PlatformTxnAccessor accessor) { this.accessor = accessor; }

    public SwirldDualState getDualState() { return dualState; }

    public void setDualState(SwirldDualState dualState) { this.dualState = dualState; }

    public boolean isErrored() { return errored; }

    public void setErrored(boolean errored) { this.errored = errored; }

    public void clear() {
        submittingMember = -1;
        isConsensus = false;
        creationTime = null;
        consensusTime = null;
        accessor = null;
        dualState = null;
        errored = false;
    }
}
