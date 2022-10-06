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
package com.hedera.services.fees.calculation.token.queries;

import static com.hedera.services.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;
import static com.hedera.services.utils.MiscUtils.putIfNotNull;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.token.TokenGetInfoUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenInfo;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetTokenInfoResourceUsage implements QueryResourceUsageEstimator {
    private static final Function<Query, TokenGetInfoUsage> factory =
            TokenGetInfoUsage::newEstimate;

    @Inject
    public GetTokenInfoResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasTokenGetInfo();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getTokenGetInfo();
        final var optionalInfo = view.infoForToken(op.getToken());
        if (optionalInfo.isPresent()) {
            final var info = optionalInfo.get();
            putIfNotNull(queryCtx, TOKEN_INFO_CTX_KEY, info);
            final var estimate =
                    factory.apply(query)
                            .givenCurrentAdminKey(
                                    ifPresent(info, TokenInfo::hasAdminKey, TokenInfo::getAdminKey))
                            .givenCurrentFreezeKey(
                                    ifPresent(
                                            info, TokenInfo::hasFreezeKey, TokenInfo::getFreezeKey))
                            .givenCurrentWipeKey(
                                    ifPresent(info, TokenInfo::hasWipeKey, TokenInfo::getWipeKey))
                            .givenCurrentSupplyKey(
                                    ifPresent(
                                            info, TokenInfo::hasSupplyKey, TokenInfo::getSupplyKey))
                            .givenCurrentKycKey(
                                    ifPresent(info, TokenInfo::hasKycKey, TokenInfo::getKycKey))
                            .givenCurrentPauseKey(
                                    ifPresent(info, TokenInfo::hasPauseKey, TokenInfo::getPauseKey))
                            .givenCurrentName(info.getName())
                            .givenCurrentMemo(info.getMemo())
                            .givenCurrentSymbol(info.getSymbol());
            if (info.hasAutoRenewAccount()) {
                estimate.givenCurrentlyUsingAutoRenewAccount();
            }
            return estimate.get();
        } else {
            return FeeData.getDefaultInstance();
        }
    }

    public static Optional<Key> ifPresent(
            final TokenInfo info,
            final Predicate<TokenInfo> check,
            final Function<TokenInfo, Key> getter) {
        return check.test(info) ? Optional.of(getter.apply(info)) : Optional.empty();
    }
}
