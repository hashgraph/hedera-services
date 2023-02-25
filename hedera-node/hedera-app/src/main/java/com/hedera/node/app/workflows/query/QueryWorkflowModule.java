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

package com.hedera.node.app.workflows.query;

import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.MonoFeeAccumulator;
import com.hedera.node.app.service.consensus.impl.components.ConsensusComponent;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.MonoThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import javax.inject.Singleton;

@Module
public interface QueryWorkflowModule {
    @Binds
    @Singleton
    QueryWorkflow bindQueryWorkflow(QueryWorkflowImpl queryWorkflow);

    @Binds
    @Singleton
    ThrottleAccumulator bindThrottleAccumulator(MonoThrottleAccumulator throttleAccumulator);

    @Binds
    @Singleton
    FeeAccumulator bindFeeAccumulator(MonoFeeAccumulator feeAccumulator);

    @Provides
    @Singleton
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Function<ResponseType, AutoCloseableWrapper<HederaState>> provideStateAccess(
            @NonNull final Platform platform) {
        // Always return the latest immutable state until we support state proofs
        return responseType -> (AutoCloseableWrapper) platform.getLatestImmutableState();
    }

    @Provides
    static QueryHandlers provideQueryHandlers(
            @NonNull ConsensusComponent consensusComponent,
            @NonNull FileComponent fileComponent,
            @NonNull NetworkComponent networkComponent,
            @NonNull ContractComponent contractComponent,
            @NonNull ScheduleComponent scheduleComponent,
            @NonNull TokenComponent tokenComponent) {
        return new QueryHandlers(
                consensusComponent.consensusGetTopicInfoHandler(),
                contractComponent.contractGetBySolidityIDHandler(),
                contractComponent.contractCallLocalHandler(),
                contractComponent.contractGetInfoHandler(),
                contractComponent.contractGetBytecodeHandler(),
                contractComponent.contractGetRecordsHandler(),
                tokenComponent.cryptoGetAccountBalanceHandler(),
                tokenComponent.cryptoGetAccountInfoHandler(),
                tokenComponent.cryptoGetAccountRecordsHandler(),
                tokenComponent.cryptoGetLiveHashHandler(),
                tokenComponent.cryptoGetStakersHandler(),
                fileComponent.fileGetContentsHandler(),
                fileComponent.fileGetInfoHandler(),
                networkComponent.networkGetAccountDetailsHandler(),
                networkComponent.networkGetByKeyHandler(),
                networkComponent.networkGetExecutionTimeHandler(),
                networkComponent.networkGetVersionInfoHandler(),
                networkComponent.networkTransactionGetReceiptHandler(),
                networkComponent.networkTransactionGetRecordHandler(),
                scheduleComponent.scheduleGetInfoHandler(),
                tokenComponent.tokenGetInfoHandler(),
                tokenComponent.tokenGetAccountNftInfosHandler(),
                tokenComponent.tokenGetNftInfoHandler(),
                tokenComponent.tokenGetNftInfosHandler());
    }
}
