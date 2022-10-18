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
package com.hedera.services.fees.calculation.token.txns;

import static com.hedera.services.fees.calculation.token.queries.GetTokenInfoResourceUsage.ifPresent;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.token.TokenUpdateUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenUpdateResourceUsage extends AbstractTokenResourceUsage
        implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenUpdateUsage> factory =
            TokenUpdateUsage::newEstimate;

    @Inject
    public TokenUpdateResourceUsage(EstimatorFactory estimatorFactory) {
        super(estimatorFactory);
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasTokenUpdate();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view)
            throws InvalidTxBodyException {
        var op = txn.getTokenUpdate();
        var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        var optionalInfo = view.infoForToken(op.getToken());
        if (optionalInfo.isPresent()) {
            var info = optionalInfo.get();
            var estimate =
                    factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS))
                            .givenCurrentExpiry(info.getExpiry().getSeconds())
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
                            .givenCurrentFeeScheduleKey(
                                    ifPresent(
                                            info,
                                            TokenInfo::hasFeeScheduleKey,
                                            TokenInfo::getFeeScheduleKey))
                            .givenCurrentPauseKey(
                                    ifPresent(info, TokenInfo::hasPauseKey, TokenInfo::getPauseKey))
                            .givenCurrentMemo(info.getMemo())
                            .givenCurrentName(info.getName())
                            .givenCurrentSymbol(info.getSymbol());
            if (info.hasAutoRenewAccount()) {
                estimate.givenCurrentlyUsingAutoRenewAccount();
            }
            return estimate.get();
        } else {
            return FeeData.getDefaultInstance();
        }
    }
}
