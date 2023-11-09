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

package com.hedera.node.app.service.mono.fees.calculation.token.txns;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.hapi.fees.usage.EstimatorFactory;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenAssociateUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class TokenAssociateResourceUsage extends AbstractTokenResourceUsage implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenAssociateUsage> factory =
            TokenAssociateUsage::newEstimate;

    @Inject
    public TokenAssociateResourceUsage(final EstimatorFactory estimatorFactory) {
        super(estimatorFactory);
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasTokenAssociate();
    }

    @Override
    public FeeData usageGiven(final TransactionBody txn, final SigValueObj svo, final StateView view) {
        final var op = txn.getTokenAssociate();
        final var account = view.accounts().get(fromAccountId(op.getAccount()));
        if (account == null) {
            return FeeData.getDefaultInstance();
        } else {
            final var sigUsage =
                    new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
            final var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS));
            return estimate.givenCurrentExpiry(account.getExpiry()).get();
        }
    }

    /**
     * This method is used to calculate the fee for token associate transaction
     * only in modular code. This will be modified once fees is modularized.
     * @param txn transaction body
     * @param svo signature value object
     * @param account account object
     * @return fee data
     */
    public FeeData usageGiven(final TransactionBody txn, final SigValueObj svo, final Account account) {
        if (account == null) {
            return FeeData.getDefaultInstance();
        } else {
            final var sigUsage =
                    new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
            final var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS));
            return estimate.givenCurrentExpiry(account.expirationSecond()).get();
        }
    }
}
