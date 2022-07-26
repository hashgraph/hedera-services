/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.answering;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.AnswerService;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;

public class StakeAwareAnswerFlow implements AnswerFlow {
    private final NodeInfo nodeInfo;
    private final StakedAnswerFlow stakedAnswerFlow;
    private final ZeroStakeAnswerFlow zeroStakeAnswerFlow;

    public StakeAwareAnswerFlow(
            final NodeInfo nodeInfo,
            final StakedAnswerFlow stakedAnswerFlow,
            final ZeroStakeAnswerFlow zeroStakeAnswerFlow) {
        this.nodeInfo = nodeInfo;
        this.stakedAnswerFlow = stakedAnswerFlow;
        this.zeroStakeAnswerFlow = zeroStakeAnswerFlow;
    }

    @Override
    public Response satisfyUsing(final AnswerService service, final Query query) {
        return nodeInfo.isSelfZeroStake()
                ? zeroStakeAnswerFlow.satisfyUsing(service, query)
                : stakedAnswerFlow.satisfyUsing(service, query);
    }
}
