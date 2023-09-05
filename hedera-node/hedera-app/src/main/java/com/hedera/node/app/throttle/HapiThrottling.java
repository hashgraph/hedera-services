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

package com.hedera.node.app.throttle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

public class HapiThrottling {

    private final GeneralThrottleAccumulator generalThrottleAccumulator;

    @Inject
    public HapiThrottling(GeneralThrottleAccumulator generalThrottleAccumulator) {
        this.generalThrottleAccumulator =
                requireNonNull(generalThrottleAccumulator, "generalThrottleAccumulator must not be null");
    }

    public synchronized boolean shouldThrottle(@NonNull TransactionInfo txnInfo, HederaState state) {
        return generalThrottleAccumulator.shouldThrottle(txnInfo, Instant.now(), state);
    }

    public synchronized boolean shouldThrottleQuery(Query query, HederaFunctionality queryFunction) {
        return generalThrottleAccumulator.shouldThrottleQuery(queryFunction, Instant.now(), query);
    }
}
