/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.token;

import static com.hedera.node.app.hapi.fees.usage.EstimatorUtils.MAX_ENTITY_LIFETIME;
import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.api.proto.java.SubType.*;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenCreateUsage extends TokenTxnUsage<TokenCreateUsage> {
    private static final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();

    public TokenCreateUsage(TransactionBody tokenCreationOp, TxnUsageEstimator usageEstimator) {
        super(tokenCreationOp, usageEstimator);
    }

    public static TokenCreateUsage newEstimate(
            TransactionBody tokenCreationOp, TxnUsageEstimator usageEstimator) {
        return new TokenCreateUsage(tokenCreationOp, usageEstimator);
    }

    @Override
    TokenCreateUsage self() {
        return this;
    }

    public FeeData get() {
        int baseSize = TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS.getTokenTxnBaseSize(op);
        var opTokenCreation = this.op.getTokenCreation();

        var lifetime =
                opTokenCreation.hasAutoRenewAccount()
                        ? opTokenCreation.getAutoRenewPeriod().getSeconds()
                        : ESTIMATOR_UTILS.relativeLifetime(
                                this.op, opTokenCreation.getExpiry().getSeconds());
        lifetime = Math.min(lifetime, MAX_ENTITY_LIFETIME);

        usageEstimator.addBpt(baseSize);
        final var feeSchedulesSize =
                opTokenCreation.getCustomFeesCount() > 0
                        ? tokenOpsUsage.bytesNeededToRepr(opTokenCreation.getCustomFeesList())
                        : 0;
        usageEstimator.addRbs((baseSize + feeSchedulesSize) * lifetime);
        addNetworkRecordRb(BASIC_ENTITY_ID_SIZE);
        addTokenTransfersRecordRb(1, opTokenCreation.getInitialSupply() > 0 ? 1 : 0, 0);

        SubType chosenType;
        final var usesCustomFees =
                opTokenCreation.hasFeeScheduleKey() || opTokenCreation.getCustomFeesCount() > 0;
        if (opTokenCreation.getTokenType() == NON_FUNGIBLE_UNIQUE) {
            chosenType =
                    usesCustomFees
                            ? TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES
                            : TOKEN_NON_FUNGIBLE_UNIQUE;
        } else {
            chosenType =
                    usesCustomFees ? TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES : TOKEN_FUNGIBLE_COMMON;
        }
        return usageEstimator.get(chosenType);
    }
}
