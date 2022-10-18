/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts;

import static com.hedera.services.contracts.ContractsModule.*;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.txns.util.PrngLogic;
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
    @V_0_32
    EVM evmV_0_32();

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
        Builder livePricesSource(LivePricesSource livePricesSource);

        @BindsInstance
        Builder transactionContext(TransactionContext transactionContext);

        @BindsInstance
        Builder entityCreator(EntityCreator entityCreator);

        ContractsTestComponent build();
    }
}
