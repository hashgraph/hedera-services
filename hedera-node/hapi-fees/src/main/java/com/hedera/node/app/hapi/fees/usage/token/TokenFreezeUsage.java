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

import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenFreezeUsage extends TokenTxnUsage<TokenFreezeUsage> {
    private TokenFreezeUsage(TransactionBody tokenFreezeOp, TxnUsageEstimator usageEstimator) {
        super(tokenFreezeOp, usageEstimator);
    }

    public static TokenFreezeUsage newEstimate(
            TransactionBody tokenFreezeOp, TxnUsageEstimator usageEstimator) {
        return new TokenFreezeUsage(tokenFreezeOp, usageEstimator);
    }

    @Override
    TokenFreezeUsage self() {
        return this;
    }

    public FeeData get() {
        addEntityBpt();
        addEntityBpt();
        return usageEstimator.get();
    }
}
