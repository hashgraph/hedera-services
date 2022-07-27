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
package com.hedera.services.fees.calculation.crypto;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountInfoResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountRecordsResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoDeleteResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.Set;

@Module
public final class CryptoFeesModule {
    @Provides
    @ElementsIntoSet
    public static Set<QueryResourceUsageEstimator> provideCryptoQueryEstimators(
            final GetAccountInfoResourceUsage getAccountInfoResourceUsage,
            final GetAccountRecordsResourceUsage getAccountRecordsResourceUsage) {
        return Set.of(getAccountInfoResourceUsage, getAccountRecordsResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(CryptoDelete)
    public static List<TxnResourceUsageEstimator> provideCryptoDeleteEstimator(
            final CryptoDeleteResourceUsage cryptoDeleteResourceUsage) {
        return List.of(cryptoDeleteResourceUsage);
    }

    private CryptoFeesModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
