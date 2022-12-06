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
package com.hedera.node.app.hapi.fees.usage.token;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenAssociateUsage extends TokenTxnUsage<TokenAssociateUsage> {
    private long currentExpiry;

    private TokenAssociateUsage(TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
        super(tokenOp, usageEstimator);
    }

    public static TokenAssociateUsage newEstimate(
            TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
        return new TokenAssociateUsage(tokenOp, usageEstimator);
    }

    @Override
    TokenAssociateUsage self() {
        return this;
    }

    public TokenAssociateUsage givenCurrentExpiry(long expiry) {
        this.currentExpiry = expiry;
        return this;
    }

    public FeeData get() {
        var op = this.op.getTokenAssociate();
        addEntityBpt();
        op.getTokensList().forEach(t -> addEntityBpt());
        novelRelsLasting(
                op.getTokensCount(), ESTIMATOR_UTILS.relativeLifetime(this.op, currentExpiry));
        return usageEstimator.get();
    }
}
