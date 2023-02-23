/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts;

import static com.hedera.node.app.service.mono.contracts.ContractsModule.V_0_30;
import static com.hedera.node.app.service.mono.contracts.ContractsModule.V_0_34;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.txns.util.PrngLogic;
import dagger.BindsInstance;
import dagger.Component;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

@Singleton
@Component(modules = {ContractsModule.class})
public interface ContractsTestComponent {

    @Singleton
    Map<String, Provider<MessageCallProcessor>> messageCallProcessors();

    @Singleton
    Map<String, Provider<ContractCreationProcessor>> contractCreateProcessors();

    @Singleton
    @V_0_30
    EVM evmV_0_30();

    @Singleton
    @V_0_34
    EVM evmV_0_34();

    @Component.Builder
    interface Builder {

        @BindsInstance
        Builder globalDynamicProperties(GlobalDynamicProperties globalDynamicProperties);

        @BindsInstance
        Builder usagePricesProvider(UsagePricesProvider usagePricesProvider);

        @BindsInstance
        Builder hbarCentExchange(HbarCentExchange hbarCentExchange);

        @BindsInstance
        Builder evmSigsVerifier(EvmSigsVerifier evmSigsVerifier);

        @BindsInstance
        Builder recordsHistorian(RecordsHistorian recordsHistorian);

        @BindsInstance
        Builder impliedTransferMarshal(ImpliedTransfersMarshal impliedTransfersMarshal);

        @BindsInstance
        Builder feeCalculator(FeeCalculator feeCalculatorProvider);

        @BindsInstance
        Builder stateView(StateView stateView);

        @BindsInstance
        Builder autoCreationLogic(AutoCreationLogic autoCreationLogic);

        @BindsInstance
        Builder txnAwareSigsVerifier(TxnAwareEvmSigsVerifier txnAwareEvmSigsVerifier);

        @BindsInstance
        Builder ExpiringCreations(ExpiringCreations ExpiringCreations);

        @BindsInstance
        Builder InfrastructureFactory(InfrastructureFactory InfrastructureFactory);

        @BindsInstance
        Builder now(Supplier<Instant> now);

        @BindsInstance
        Builder prngLogic(PrngLogic prngLogic);

        @BindsInstance
        Builder feeResourcesLoader(FeeResourcesLoaderImpl feeResourcesLoader);

        @BindsInstance
        Builder transactionContext(TransactionContext transactionContext);

        @BindsInstance
        Builder entityCreator(EntityCreator entityCreator);

        ContractsTestComponent build();
    }
}
