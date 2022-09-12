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

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.token.TokenDissociateUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenDissociateResourceUsage extends AbstractTokenResourceUsage
        implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenDissociateUsage>
            factory = TokenDissociateUsage::newEstimate;

    @Inject
    public TokenDissociateResourceUsage(EstimatorFactory estimatorFactory) {
        super(estimatorFactory);
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasTokenDissociate();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view)
            throws InvalidTxBodyException {
        var op = txn.getTokenDissociate();
        var account = view.accounts().get(fromAccountId(op.getAccount()));
        if (account == null) {
            return FeeData.getDefaultInstance();
        } else {
            var sigUsage =
                    new SigUsage(
                            svo.getTotalSigCount(),
                            svo.getSignatureSize(),
                            svo.getPayerAcctSigCount());
            var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS));
            return estimate.get();
        }
    }
}
