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

import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.components.QueryInjectionComponent;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusHandlers;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.file.impl.handlers.FileHandlers;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkAdminHandlers;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleHandlers;
import com.hedera.node.app.service.token.impl.handlers.TokenHandlers;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Singleton;

/**
 * Module for Query processing.
 */
@Module(subcomponents = {QueryInjectionComponent.class})
public interface QueryWorkflowInjectionModule {
    @Binds
    @Singleton
    QueryWorkflow bindQueryWorkflow(QueryWorkflowImpl queryWorkflow);

    Runnable NO_OP = () -> {};

    @Provides
    @Singleton
    static Function<ResponseType, AutoCloseableWrapper<HederaState>> provideStateAccess(
            @NonNull final WorkingStateAccessor workingStateAccessor) {
        return responseType -> new AutoCloseableWrapper<>(workingStateAccessor.getHederaState(), NO_OP);
    }

    @Provides
    static QueryHandlers provideQueryHandlers(
            @NonNull final ConsensusHandlers consensusHandlers,
            @NonNull final FileHandlers fileHandlers,
            @NonNull final NetworkAdminHandlers networkHandlers,
            @NonNull final Supplier<ContractHandlers> contractHandlers,
            @NonNull final ScheduleHandlers scheduleHandlers,
            @NonNull final TokenHandlers tokenHandlers) {
        return new QueryHandlers(
                consensusHandlers.consensusGetTopicInfoHandler(),
                contractHandlers.get().contractGetBySolidityIDHandler(),
                contractHandlers.get().contractCallLocalHandler(),
                contractHandlers.get().contractGetInfoHandler(),
                contractHandlers.get().contractGetBytecodeHandler(),
                contractHandlers.get().contractGetRecordsHandler(),
                tokenHandlers.cryptoGetAccountBalanceHandler(),
                tokenHandlers.cryptoGetAccountInfoHandler(),
                tokenHandlers.cryptoGetAccountRecordsHandler(),
                tokenHandlers.cryptoGetLiveHashHandler(),
                tokenHandlers.cryptoGetStakersHandler(),
                fileHandlers.fileGetContentsHandler(),
                fileHandlers.fileGetInfoHandler(),
                networkHandlers.networkGetAccountDetailsHandler(),
                networkHandlers.networkGetByKeyHandler(),
                networkHandlers.networkGetExecutionTimeHandler(),
                networkHandlers.networkGetVersionInfoHandler(),
                networkHandlers.networkTransactionGetReceiptHandler(),
                networkHandlers.networkTransactionGetRecordHandler(),
                networkHandlers.networkTransactionGetFastRecordHandler(),
                scheduleHandlers.scheduleGetInfoHandler(),
                tokenHandlers.tokenGetInfoHandler(),
                tokenHandlers.tokenGetAccountNftInfosHandler(),
                tokenHandlers.tokenGetNftInfoHandler(),
                tokenHandlers.tokenGetNftInfosHandler());
    }

    @Provides
    static Codec<Query> provideQueryParser() {
        return Query.PROTOBUF;
    }
}
