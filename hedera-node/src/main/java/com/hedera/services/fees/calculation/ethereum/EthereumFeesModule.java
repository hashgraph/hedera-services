/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation.ethereum;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.ethereum.txns.EthereumTransactionResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;

@Module
public final class EthereumFeesModule {

    @Provides
    @IntoMap
    @FunctionKey(EthereumTransaction)
    public static List<TxnResourceUsageEstimator> provideEthereumTransactionResourceUsage(
            final EthereumTransactionResourceUsage ethereumTransactionResourceUsage) {
        return List.of(ethereumTransactionResourceUsage);
    }

    private EthereumFeesModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
