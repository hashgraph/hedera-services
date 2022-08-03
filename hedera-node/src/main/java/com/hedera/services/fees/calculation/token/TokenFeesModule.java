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
package com.hedera.services.fees.calculation.token;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.token.queries.GetTokenInfoResourceUsage;
import com.hedera.services.fees.calculation.token.queries.GetTokenNftInfoResourceUsage;
import com.hedera.services.fees.calculation.token.txns.*;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.TxnUsageEstimator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.Set;

@Module
public final class TokenFeesModule {
    @Provides
    @ElementsIntoSet
    public static Set<QueryResourceUsageEstimator> provideTokenQueryEstimators(
            final GetTokenInfoResourceUsage getTokenInfoResourceUsage,
            final GetTokenNftInfoResourceUsage getTokenNftInfoResourceUsage) {
        return Set.of(getTokenInfoResourceUsage, getTokenNftInfoResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenCreate)
    public static List<TxnResourceUsageEstimator> provideTokenCreateEstimator(
            final TokenCreateResourceUsage tokenCreateResourceUsage) {
        return List.of(tokenCreateResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenUpdate)
    public static List<TxnResourceUsageEstimator> provideTokenUpdateEstimator(
            final TokenUpdateResourceUsage tokenUpdateResourceUsage) {
        return List.of(tokenUpdateResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenGrantKycToAccount)
    public static List<TxnResourceUsageEstimator> provideTokenGrantKycEstimator(
            final TokenGrantKycResourceUsage tokenGrantKycResourceUsage) {
        return List.of(tokenGrantKycResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenRevokeKycFromAccount)
    public static List<TxnResourceUsageEstimator> provideTokenRevokeKycEstimator(
            final TokenRevokeKycResourceUsage tokenRevokeKycResourceUsage) {
        return List.of(tokenRevokeKycResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenDelete)
    public static List<TxnResourceUsageEstimator> provideTokenDelete(
            final TokenDeleteResourceUsage tokenDeleteResourceUsage) {
        return List.of(tokenDeleteResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenAssociateToAccount)
    public static List<TxnResourceUsageEstimator> provideTokenAssociate(
            final TokenAssociateResourceUsage tokenAssociateResourceUsage) {
        return List.of(tokenAssociateResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(TokenDissociateFromAccount)
    public static List<TxnResourceUsageEstimator> provideTokenDissociate(
            final TokenDissociateResourceUsage tokenDissociateResourceUsage) {
        return List.of(tokenDissociateResourceUsage);
    }

    @Provides
    public static EstimatorFactory provideEstimatorFactory() {
        return TxnUsageEstimator::new;
    }

    private TokenFeesModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
