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
package com.hedera.services.fees.calculation.crypto.queries;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetAccountDetailsResourceUsage implements QueryResourceUsageEstimator {
    private final CryptoOpsUsage cryptoOpsUsage;
    private final AliasManager aliasManager;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public GetAccountDetailsResourceUsage(
            final CryptoOpsUsage cryptoOpsUsage,
            final AliasManager aliasManager,
            final GlobalDynamicProperties dynamicProperties) {
        this.cryptoOpsUsage = cryptoOpsUsage;
        this.aliasManager = aliasManager;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasAccountDetails();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, final Map<String, Object> ignoreCtx) {
        final var op = query.getAccountDetails();

        final var account = op.getAccountId();
        final var accountDetails =
                view.accountDetails(
                        account, aliasManager, dynamicProperties.maxTokensRelsPerInfoQuery());
        /* Given the test in {@code GetAccountDetailsAnswer.checkValidity}, this can only be empty
         * under the extraordinary circumstance that the desired account expired during the query
         * answer flow (which will now fail downstream with an appropriate status code); so
         * just return the default {@code FeeData} here. */
        if (accountDetails.isEmpty()) {
            return FeeData.getDefaultInstance();
        }
        final var details = accountDetails.get();
        final var ctx =
                ExtantCryptoContext.newBuilder()
                        .setCurrentKey(details.getKey())
                        .setCurrentMemo(details.getMemo())
                        .setCurrentExpiry(details.getExpirationTime().getSeconds())
                        .setCurrentlyHasProxy(details.hasProxyAccountId())
                        .setCurrentNumTokenRels(details.getTokenRelationshipsCount())
                        .setCurrentMaxAutomaticAssociations(
                                details.getMaxAutomaticTokenAssociations())
                        .setCurrentCryptoAllowances(details.getGrantedCryptoAllowancesList())
                        .setCurrentTokenAllowances(details.getGrantedTokenAllowancesList())
                        .setCurrentApproveForAllNftAllowances(details.getGrantedNftAllowancesList())
                        .build();
        return cryptoOpsUsage.accountDetailsUsage(query, ctx);
    }
}
