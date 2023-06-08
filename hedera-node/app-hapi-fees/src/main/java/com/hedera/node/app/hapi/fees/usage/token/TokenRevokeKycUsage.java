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

import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenRevokeKycUsage extends TokenTxnUsage<TokenRevokeKycUsage> {
    private TokenRevokeKycUsage(TransactionBody tokenRevokeKycOp, TxnUsageEstimator usageEstimator) {
        super(tokenRevokeKycOp, usageEstimator);
    }

    public static TokenRevokeKycUsage newEstimate(TransactionBody tokenRevokeKycOp, TxnUsageEstimator usageEstimator) {
        return new TokenRevokeKycUsage(tokenRevokeKycOp, usageEstimator);
    }

    @Override
    TokenRevokeKycUsage self() {
        return this;
    }

    public FeeData get() {
        addEntityBpt();
        addEntityBpt();
        return usageEstimator.get();
    }
}
