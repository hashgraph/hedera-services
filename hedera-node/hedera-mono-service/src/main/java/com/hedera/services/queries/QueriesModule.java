/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.queries.answering.QueryHeaderValidity;
import com.hedera.services.queries.answering.StakeAwareAnswerFlow;
import com.hedera.services.queries.answering.StakedAnswerFlow;
import com.hedera.services.queries.answering.ZeroStakeAnswerFlow;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.txns.submission.TransactionPrecheck;
import dagger.Module;
import dagger.Provides;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public final class QueriesModule {

    @Provides
    @Singleton
    public static AnswerFlow provideAnswerFlow(
            final NodeInfo nodeInfo,
            final FeeCalculator fees,
            final AccountNumbers accountNums,
            final QueryFeeCheck queryFeeCheck,
            final HapiOpPermissions hapiOpPermissions,
            final Supplier<StateView> stateViews,
            final UsagePricesProvider usagePrices,
            final QueryHeaderValidity queryHeaderValidity,
            final TransactionPrecheck transactionPrecheck,
            final PlatformSubmissionManager submissionManager,
            @HapiThrottle final FunctionalityThrottling hapiThrottling) {
        final var stakedFlow =
                new StakedAnswerFlow(
                        fees,
                        accountNums,
                        stateViews,
                        usagePrices,
                        hapiThrottling,
                        submissionManager,
                        queryHeaderValidity,
                        transactionPrecheck,
                        hapiOpPermissions,
                        queryFeeCheck);

        final var zeroStakeFlow =
                new ZeroStakeAnswerFlow(queryHeaderValidity, stateViews, hapiThrottling);

        return new StakeAwareAnswerFlow(nodeInfo, stakedFlow, zeroStakeFlow);
    }

    private QueriesModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
