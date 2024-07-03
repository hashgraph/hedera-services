/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.node.app.spi.fees.Fees.CONSTANT_FEE_DATA;

import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenAssociateUsage;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hederahashgraph.api.proto.java.FeeData;

public class TokenAssociateToAccountFeeCalculator {

    private TokenAssociateToAccountFeeCalculator() {
        // Util class
    }

    public static Fees calculate(TransactionBody body, FeeContext feeContext) {
        final var op = body.tokenAssociateOrThrow();

        final var accountId = op.accountOrThrow();
        final var readableAccountStore = feeContext.readableStore(ReadableAccountStore.class);
        final var account = readableAccountStore.getAccountById(accountId);

        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(sigValueObj -> usageGiven(CommonPbjConverters.fromPbj(body), sigValueObj, account));
    }

    private static FeeData usageGiven(
            final com.hederahashgraph.api.proto.java.TransactionBody txn,
            final SigValueObj svo,
            final Account account) {
        if (account == null) {
            return CONSTANT_FEE_DATA;
        } else {
            final var sigUsage =
                    new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
            final var estimate = new TokenAssociateUsage(txn, new TxnUsageEstimator(sigUsage, txn, ESTIMATOR_UTILS));
            return estimate.givenCurrentExpiry(account.expirationSecond()).get();
        }
    }
}
