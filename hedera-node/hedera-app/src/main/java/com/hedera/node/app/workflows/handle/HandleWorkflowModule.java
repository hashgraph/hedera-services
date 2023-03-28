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

package com.hedera.node.app.workflows.handle;

import com.hedera.node.app.meta.MonoHandleContext;
import com.hedera.node.app.service.admin.impl.components.AdminComponent;
import com.hedera.node.app.service.consensus.impl.components.ConsensusComponent;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.mono.sigs.Expansion;
import com.hedera.node.app.service.mono.sigs.PlatformSigOps;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.txns.TransactionLastStep;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
import com.hedera.node.app.service.util.impl.components.UtilComponent;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.TransactionHandlers;
import com.hedera.node.app.workflows.handle.validation.MonoExpiryValidator;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public interface HandleWorkflowModule {
    @Provides
    static Expansion.CryptoSigsCreation provideCryptoSigsCreation() {
        return PlatformSigOps::createCryptoSigsFrom;
    }

    @Provides
    static Function<SignatureMap, PubKeyToSigBytes> provideKeyToSigFactory() {
        return PojoSigMapPubKeyToSigBytes::new;
    }

    @Provides
    static Function<TxnAccessor, TxnScopedPlatformSigFactory> provideScopedFactoryProvider() {
        return ReusableBodySigningFactory::new;
    }

    @Binds
    @Singleton
    TransactionLastStep bindLastStep(AdaptedMonoTransitionRunner adaptedTransitionRunner);

    @Binds
    @Singleton
    HandleContext bindHandleContext(MonoHandleContext monoHandleContext);

    @Binds
    @Singleton
    ExpiryValidator bindEntityExpiryValidator(MonoExpiryValidator monoEntityExpiryValidator);

    @Provides
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Supplier<AutoCloseableWrapper<HederaState>> provideStateSupplier(@NonNull final Platform platform) {
        // Always return the latest immutable state until we support state proofs
        return () -> (AutoCloseableWrapper) platform.getLatestImmutableState();
    }

    @Provides
    @Singleton
    static TransactionHandlers provideTransactionHandlers(
            @NonNull final AdminComponent adminComponent,
            @NonNull final ConsensusComponent consensusComponent,
            @NonNull final FileCreateHandler fileCreateHandler,
            @NonNull final FileUpdateHandler fileUpdateHandler,
            @NonNull final FileDeleteHandler fileDeleteHandler,
            @NonNull final FileAppendHandler fileAppendHandler,
            @NonNull final FileSystemDeleteHandler fileSystemDeleteHandler,
            @NonNull final FileSystemUndeleteHandler fileSystemUndeleteHandler,
            @NonNull final NetworkComponent networkComponent,
            @NonNull final ContractComponent contractComponent,
            @NonNull final ScheduleComponent scheduleComponent,
            @NonNull final TokenComponent tokenComponent,
            @NonNull final UtilComponent utilComponent) {
        return new TransactionHandlers(
                consensusComponent.consensusCreateTopicHandler(),
                consensusComponent.consensusUpdateTopicHandler(),
                consensusComponent.consensusDeleteTopicHandler(),
                consensusComponent.consensusSubmitMessageHandler(),
                contractComponent.contractCreateHandler(),
                contractComponent.contractUpdateHandler(),
                contractComponent.contractCallHandler(),
                contractComponent.contractDeleteHandler(),
                contractComponent.contractSystemDeleteHandler(),
                contractComponent.contractSystemUndeleteHandler(),
                contractComponent.etherumTransactionHandler(),
                tokenComponent.cryptoCreateHandler(),
                tokenComponent.cryptoUpdateHandler(),
                tokenComponent.cryptoTransferHandler(),
                tokenComponent.cryptoDeleteHandler(),
                tokenComponent.cryptoApproveAllowanceHandler(),
                tokenComponent.cryptoDeleteAllowanceHandler(),
                tokenComponent.cryptoAddLiveHashHandler(),
                tokenComponent.cryptoDeleteLiveHashHandler(),
                fileCreateHandler,
                fileUpdateHandler,
                fileDeleteHandler,
                fileAppendHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler,
                adminComponent.freezeHandler(),
                networkComponent.networkUncheckedSubmitHandler(),
                scheduleComponent.scheduleCreateHandler(),
                scheduleComponent.scheduleSignHandler(),
                scheduleComponent.scheduleDeleteHandler(),
                tokenComponent.tokenCreateHandler(),
                tokenComponent.tokenUpdateHandler(),
                tokenComponent.tokenMintHandler(),
                tokenComponent.tokenBurnHandler(),
                tokenComponent.tokenDeleteHandler(),
                tokenComponent.tokenAccountWipeHandler(),
                tokenComponent.tokenFreezeAccountHandler(),
                tokenComponent.tokenUnfreezeAccountHandler(),
                tokenComponent.tokenGrantKycToAccountHandler(),
                tokenComponent.tokenRevokeKycFromAccountHandler(),
                tokenComponent.tokenAssociateToAccountHandler(),
                tokenComponent.tokenDissociateFromAccountHandler(),
                tokenComponent.tokenFeeScheduleUpdateHandler(),
                tokenComponent.tokenPauseHandler(),
                tokenComponent.tokenUnpauseHandler(),
                utilComponent.prngHandler());
    }
}
