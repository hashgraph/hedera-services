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

package com.hedera.node.app.throttle.impl;

import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.HandleThrottleAccumulator;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

public class NetworkUtilizationManagerImpl implements NetworkUtilizationManager {
    private final HandleThrottleAccumulator handleThrottling;

    @Inject
    public NetworkUtilizationManagerImpl(@NonNull final HandleThrottleAccumulator handleThrottling) {
        this.handleThrottling = handleThrottling;
    }

    @Override
    public void trackTxn(@NonNull final TransactionInfo txnInfo, Instant consensusTime, HederaState state) {
        handleThrottling.shouldThrottle(txnInfo, consensusTime, state);
        // TODO:       multiplierSources.updateMultiplier(accessor, now);
    }

    @Override
    public boolean wasLastTxnGasThrottled() {
        return handleThrottling.wasLastTxnGasThrottled();
    }
}
