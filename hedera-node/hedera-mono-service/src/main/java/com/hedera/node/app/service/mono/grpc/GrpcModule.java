/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.grpc;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.CustomFeePayerExemptions;
import com.hedera.node.app.service.mono.fees.StandardCustomPayerExemptions;
import com.hedera.node.app.service.mono.grpc.controllers.ConsensusController;
import com.hedera.node.app.service.mono.grpc.controllers.ContractController;
import com.hedera.node.app.service.mono.grpc.controllers.CryptoController;
import com.hedera.node.app.service.mono.grpc.controllers.FileController;
import com.hedera.node.app.service.mono.grpc.controllers.FreezeController;
import com.hedera.node.app.service.mono.grpc.controllers.NetworkController;
import com.hedera.node.app.service.mono.grpc.controllers.ScheduleController;
import com.hedera.node.app.service.mono.grpc.controllers.TokenController;
import com.hedera.node.app.service.mono.grpc.controllers.UtilController;
import com.hedera.node.app.service.mono.grpc.marshalling.AdjustmentUtils;
import com.hedera.node.app.service.mono.grpc.marshalling.AliasResolver;
import com.hedera.node.app.service.mono.grpc.marshalling.BalanceChangeManager;
import com.hedera.node.app.service.mono.grpc.marshalling.CustomSchedulesManager;
import com.hedera.node.app.service.mono.grpc.marshalling.FeeAssessor;
import com.hedera.node.app.service.mono.grpc.marshalling.FixedFeeAssessor;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.grpc.marshalling.RoyaltyFeeAssessor;
import com.hedera.node.app.service.mono.ledger.PureTransferSemanticChecks;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.txns.customfees.CustomFeeSchedules;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import io.grpc.BindableService;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Singleton;

@Module
public interface GrpcModule {
    @Binds
    @Singleton
    GrpcServerManager bindGrpcServerManager(NettyGrpcServerManager nettyGrpcServerManager);

    @Provides
    @ElementsIntoSet
    static Set<BindableService> provideBindableServices(
            CryptoController cryptoController,
            FileController fileController,
            FreezeController freezeController,
            ContractController contractController,
            ConsensusController consensusController,
            NetworkController networkController,
            TokenController tokenController,
            ScheduleController scheduleController,
            UtilController utilController) {
        return Set.of(
                cryptoController,
                fileController,
                freezeController,
                contractController,
                consensusController,
                networkController,
                tokenController,
                scheduleController,
                utilController);
    }

    @Provides
    @Singleton
    static Consumer<Thread> provideHookAdder() {
        return Runtime.getRuntime()::addShutdownHook;
    }

    @Binds
    @Singleton
    CustomFeePayerExemptions bindCustomFeeExemptions(
            StandardCustomPayerExemptions standardCustomExemptions);

    @Provides
    @Singleton
    static RoyaltyFeeAssessor provideRoyaltyFeeAssessor(
            FixedFeeAssessor fixedFeeAssessor, CustomFeePayerExemptions customFeePayerExemptions) {
        return new RoyaltyFeeAssessor(
                fixedFeeAssessor, AdjustmentUtils::adjustedChange, customFeePayerExemptions);
    }

    @Provides
    @Singleton
    static ImpliedTransfersMarshal provideImpliedTransfersMarshal(
            FeeAssessor feeAssessor,
            AliasManager aliasManager,
            CustomFeeSchedules customFeeSchedules,
            GlobalDynamicProperties dynamicProperties,
            PureTransferSemanticChecks transferSemanticChecks) {
        return new ImpliedTransfersMarshal(
                feeAssessor,
                aliasManager,
                customFeeSchedules,
                AliasResolver::new,
                dynamicProperties,
                transferSemanticChecks,
                AliasResolver::usesAliases,
                BalanceChangeManager::new,
                CustomSchedulesManager::new);
    }
}
