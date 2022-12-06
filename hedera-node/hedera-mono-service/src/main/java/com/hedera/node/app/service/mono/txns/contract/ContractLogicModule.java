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
package com.hedera.node.app.service.mono.txns.contract;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;

import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.annotations.FunctionKey;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.StoresModule;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hedera.node.app.service.mono.txns.contract.helpers.UpdateCustomizerFactory;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.function.Supplier;

@Module(includes = {StoresModule.class})
public final class ContractLogicModule {
    @Provides
    @IntoMap
    @FunctionKey(ContractCreate)
    public static List<TransitionLogic> provideContractCreateLogic(
            final ContractCreateTransitionLogic contractCreateTransitionLogic) {
        return List.of(contractCreateTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractDelete)
    public static List<TransitionLogic> provideContractDeleteLogic(
            final ContractDeleteTransitionLogic contractDeleteTransitionLogic) {
        return List.of(contractDeleteTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractCall)
    public static List<TransitionLogic> provideContractCallLogic(
            final ContractCallTransitionLogic contractCallTransitionLogic) {
        return List.of(contractCallTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractUpdate)
    public static List<TransitionLogic> provideContractUpdateLogic(
            final HederaLedger ledger,
            final AliasManager aliasManager,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final Supplier<AccountStorageAdapter> accounts,
            final GlobalDynamicProperties properties,
            final NodeInfo nodeInfo) {
        final var contractUpdateTransitionLogic =
                new ContractUpdateTransitionLogic(
                        ledger,
                        aliasManager,
                        validator,
                        sigImpactHistorian,
                        txnCtx,
                        new UpdateCustomizerFactory(),
                        accounts,
                        properties,
                        nodeInfo);
        return List.of(contractUpdateTransitionLogic);
    }

    private ContractLogicModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
