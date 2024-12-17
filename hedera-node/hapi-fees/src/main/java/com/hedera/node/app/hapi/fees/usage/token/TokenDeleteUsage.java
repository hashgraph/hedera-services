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

package com.hedera.node.app.hapi.fees.usage.token;

import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenDeleteUsage extends TokenTxnUsage<TokenDeleteUsage> {
    public TokenDeleteUsage(TransactionBody tokenDeletionOp, TxnUsageEstimator usageEstimator) {
        super(tokenDeletionOp, usageEstimator);
    }

    public static TokenDeleteUsage newEstimate(TransactionBody tokenDeletionOp, TxnUsageEstimator usageEstimator) {
        return new TokenDeleteUsage(tokenDeletionOp, usageEstimator);
    }

    @Override
    TokenDeleteUsage self() {
        return this;
    }

    public FeeData get() {
        addEntityBpt();
        return usageEstimator.get();
    }
}
