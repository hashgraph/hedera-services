/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.token.queries;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;
import static com.hedera.node.app.service.mono.utils.MiscUtils.putIfNotNull;
import static com.hedera.node.app.spi.fees.Fees.CONSTANT_FEE_DATA;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.hapi.fees.usage.token.TokenGetInfoUsage;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenInfo;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetTokenInfoResourceUsage implements QueryResourceUsageEstimator {
    private static final Function<Query, TokenGetInfoUsage> factory = TokenGetInfoUsage::newEstimate;

    @Inject
    public GetTokenInfoResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasTokenGetInfo();
    }

    @Override
    public FeeData usageGiven(final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getTokenGetInfo();
        final var optionalInfo = view.infoForToken(op.getToken());
        if (optionalInfo.isPresent()) {
            final var info = optionalInfo.get();
            putIfNotNull(queryCtx, TOKEN_INFO_CTX_KEY, info);
            final var estimate = factory.apply(query)
                    .givenCurrentAdminKey(ifPresent(info, TokenInfo::hasAdminKey, TokenInfo::getAdminKey))
                    .givenCurrentFreezeKey(ifPresent(info, TokenInfo::hasFreezeKey, TokenInfo::getFreezeKey))
                    .givenCurrentWipeKey(ifPresent(info, TokenInfo::hasWipeKey, TokenInfo::getWipeKey))
                    .givenCurrentSupplyKey(ifPresent(info, TokenInfo::hasSupplyKey, TokenInfo::getSupplyKey))
                    .givenCurrentKycKey(ifPresent(info, TokenInfo::hasKycKey, TokenInfo::getKycKey))
                    .givenCurrentPauseKey(ifPresent(info, TokenInfo::hasPauseKey, TokenInfo::getPauseKey))
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

    /**
     * This method is used to calculate the fee for the {@code GetTokenInfo}
     * only in modularized code, until new fee logic is implemented.
     * @param query query to be processed
     * @param token token whose info to be retrieved
     * @return fee data
     */
    public FeeData usageGiven(final Query query, final Token token) {
        if (token != null) {
            final var estimate = factory.apply(query)
                    .givenCurrentAdminKey(
                            token.hasAdminKey() ? Optional.of(fromPbj(token.adminKey())) : Optional.empty())
                    .givenCurrentFreezeKey(
                            token.hasFreezeKey() ? Optional.of(fromPbj(token.freezeKey())) : Optional.empty())
                    .givenCurrentWipeKey(token.hasWipeKey() ? Optional.of(fromPbj(token.wipeKey())) : Optional.empty())
                    .givenCurrentSupplyKey(
                            token.hasSupplyKey() ? Optional.of(fromPbj(token.supplyKey())) : Optional.empty())
                    .givenCurrentKycKey(token.hasKycKey() ? Optional.of(fromPbj(token.kycKey())) : Optional.empty())
                    .givenCurrentPauseKey(
                            token.hasPauseKey() ? Optional.of(fromPbj(token.pauseKey())) : Optional.empty())
                    .givenCurrentMetadataKey(
                            token.hasMetadataKey() ? Optional.of(fromPbj(token.metadataKey())) : Optional.empty())
                    .givenCurrentName(token.name())
                    .givenCurrentMemo(token.memo())
                    .givenCurrentSymbol(token.symbol());
            if (token.hasAutoRenewAccountId()) {
                estimate.givenCurrentlyUsingAutoRenewAccount();
            }
            return estimate.get();
        } else {
            return CONSTANT_FEE_DATA;
        }
    }

    public static Optional<Key> ifPresent(
            final TokenInfo info, final Predicate<TokenInfo> check, final Function<TokenInfo, Key> getter) {
        return check.test(info) ? Optional.of(getter.apply(info)) : Optional.empty();
    }
}