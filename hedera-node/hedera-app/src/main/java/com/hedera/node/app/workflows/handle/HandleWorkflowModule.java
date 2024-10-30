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

package com.hedera.node.app.workflows.handle;

import com.hedera.node.app.service.addressbook.impl.handlers.AddressBookHandlers;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusHandlers;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.handlers.EthereumTransactionHandler;
import com.hedera.node.app.service.file.impl.handlers.FileHandlers;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkAdminHandlers;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleHandlers;
import com.hedera.node.app.service.token.impl.handlers.TokenHandlers;
import com.hedera.node.app.service.util.impl.handlers.UtilHandlers;
import com.hedera.node.app.spi.metrics.ServiceMetricsFactory;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.store.ServiceMetricsFactoryImpl;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.workflows.dispatcher.TransactionHandlers;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.CacheConfig;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.state.State;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Singleton;

@Module
public interface HandleWorkflowModule {
    @Binds
    ServiceMetricsFactory bindServiceMetricsFactory(@NonNull ServiceMetricsFactoryImpl serviceMetricsFactory);

    @Provides
    @Singleton
    static Supplier<ContractHandlers> provideContractHandlers(@NonNull final ContractServiceImpl contractService) {
        return contractService::handlers;
    }

    @Provides
    @Singleton
    static Supplier<TssHandlers> provideTssHandlers(@NonNull final TssBaseService tssBaseService) {
        return tssBaseService::tssHandlers;
    }

    @Provides
    @Singleton
    static EthereumTransactionHandler provideEthereumTransactionHandler(
            @NonNull final ContractServiceImpl contractService) {
        return contractService.handlers().ethereumTransactionHandler();
    }

    Runnable NO_OP = () -> {};

    @Provides
    static Supplier<AutoCloseableWrapper<State>> provideStateSupplier(
            @NonNull final WorkingStateAccessor workingStateAccessor) {
        return () -> new AutoCloseableWrapper<>(workingStateAccessor.getState(), NO_OP);
    }

    @Provides
    @Named("CacheWarmer")
    static Executor provideCacheWarmerExecutor(@NonNull final ConfigProvider configProvider) {
        final var config = configProvider.getConfiguration();
        final int parallelism = config.getConfigData(CacheConfig.class).warmThreads();
        return new ForkJoinPool(parallelism);
    }

    @Provides
    @Named("FreezeService")
    static Executor provideFreezeServiceExecutor() {
        return new ForkJoinPool(
                1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, Thread.getDefaultUncaughtExceptionHandler(), true);
    }

    @Provides
    @Singleton
    static TransactionHandlers provideTransactionHandlers(
            @NonNull final NetworkAdminHandlers networkAdminHandlers,
            @NonNull final ConsensusHandlers consensusHandlers,
            @NonNull final FileHandlers fileHandlers,
            @NonNull final Supplier<ContractHandlers> contractHandlers,
            @NonNull final Supplier<TssHandlers> tssHandlers,
            @NonNull final ScheduleHandlers scheduleHandlers,
            @NonNull final TokenHandlers tokenHandlers,
            @NonNull final UtilHandlers utilHandlers,
            @NonNull final AddressBookHandlers addressBookHandlers) {
        return new TransactionHandlers(
                consensusHandlers.consensusCreateTopicHandler(),
                consensusHandlers.consensusUpdateTopicHandler(),
                consensusHandlers.consensusDeleteTopicHandler(),
                consensusHandlers.consensusSubmitMessageHandler(),
                contractHandlers.get().contractCreateHandler(),
                contractHandlers.get().contractUpdateHandler(),
                contractHandlers.get().contractCallHandler(),
                contractHandlers.get().contractDeleteHandler(),
                contractHandlers.get().contractSystemDeleteHandler(),
                contractHandlers.get().contractSystemUndeleteHandler(),
                contractHandlers.get().ethereumTransactionHandler(),
                tokenHandlers.cryptoCreateHandler(),
                tokenHandlers.cryptoUpdateHandler(),
                tokenHandlers.cryptoTransferHandler(),
                tokenHandlers.cryptoDeleteHandler(),
                tokenHandlers.cryptoApproveAllowanceHandler(),
                tokenHandlers.cryptoDeleteAllowanceHandler(),
                tokenHandlers.cryptoAddLiveHashHandler(),
                tokenHandlers.cryptoDeleteLiveHashHandler(),
                fileHandlers.fileCreateHandler(),
                fileHandlers.fileUpdateHandler(),
                fileHandlers.fileDeleteHandler(),
                fileHandlers.fileAppendHandler(),
                fileHandlers.fileSystemDeleteHandler(),
                fileHandlers.fileSystemUndeleteHandler(),
                networkAdminHandlers.freezeHandler(),
                networkAdminHandlers.networkUncheckedSubmitHandler(),
                scheduleHandlers.scheduleCreateHandler(),
                scheduleHandlers.scheduleSignHandler(),
                scheduleHandlers.scheduleDeleteHandler(),
                tokenHandlers.tokenCreateHandler(),
                tokenHandlers.tokenUpdateHandler(),
                tokenHandlers.tokenMintHandler(),
                tokenHandlers.tokenBurnHandler(),
                tokenHandlers.tokenDeleteHandler(),
                tokenHandlers.tokenAccountWipeHandler(),
                tokenHandlers.tokenFreezeAccountHandler(),
                tokenHandlers.tokenUnfreezeAccountHandler(),
                tokenHandlers.tokenGrantKycToAccountHandler(),
                tokenHandlers.tokenRevokeKycFromAccountHandler(),
                tokenHandlers.tokenAssociateToAccountHandler(),
                tokenHandlers.tokenDissociateFromAccountHandler(),
                tokenHandlers.tokenFeeScheduleUpdateHandler(),
                tokenHandlers.tokenPauseHandler(),
                tokenHandlers.tokenUnpauseHandler(),
                tokenHandlers.tokenUpdateNftsHandler(),
                tokenHandlers.tokenRejectHandler(),
                tokenHandlers.tokenAirdropsHandler(),
                tokenHandlers.tokenCancelAirdropHandler(),
                addressBookHandlers.nodeCreateHandler(),
                addressBookHandlers.nodeUpdateHandler(),
                addressBookHandlers.nodeDeleteHandler(),
                tokenHandlers.tokenClaimAirdropHandler(),
                utilHandlers.prngHandler(),
                tssHandlers.get().tssMessageHandler(),
                tssHandlers.get().tssVoteHandler());
    }
}
