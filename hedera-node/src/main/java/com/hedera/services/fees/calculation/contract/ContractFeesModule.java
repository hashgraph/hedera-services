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
package com.hedera.services.fees.calculation.contract;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.contract.queries.ContractCallLocalResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetBytecodeResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetContractInfoResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCallResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCreateResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractDeleteResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractUpdateResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.Set;

@Module
public final class ContractFeesModule {
    @Provides
    @ElementsIntoSet
    public static Set<QueryResourceUsageEstimator> provideContractQueryEstimators(
            final GetBytecodeResourceUsage getBytecodeResourceUsage,
            final GetContractInfoResourceUsage getContractInfoResourceUsage,
            final ContractCallLocalResourceUsage contractCallLocalResourceUsage) {
        return Set.of(
                getBytecodeResourceUsage,
                getContractInfoResourceUsage,
                contractCallLocalResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractCreate)
    public static List<TxnResourceUsageEstimator> provideContractCreateEstimator(
            final ContractCreateResourceUsage contractCreateResourceUsage) {
        return List.of(contractCreateResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractDelete)
    public static List<TxnResourceUsageEstimator> provideContractDeleteEstimator(
            final ContractDeleteResourceUsage contractDeleteResourceUsage) {
        return List.of(contractDeleteResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractUpdate)
    public static List<TxnResourceUsageEstimator> provideContractUpdateEstimator(
            final ContractUpdateResourceUsage contractUpdateResourceUsage) {
        return List.of(contractUpdateResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ContractCall)
    public static List<TxnResourceUsageEstimator> provideContractCallEstimator(
            final ContractCallResourceUsage contractCallResourceUsage) {
        return List.of(contractCallResourceUsage);
    }

    private ContractFeesModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
