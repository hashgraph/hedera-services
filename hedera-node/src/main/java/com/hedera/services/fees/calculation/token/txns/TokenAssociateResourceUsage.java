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

import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenAssociateUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class TokenAssociateResourceUsage implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, SigUsage, TokenAssociateUsage> factory =
            TokenAssociateUsage::newEstimate;

    @Inject
    public TokenAssociateResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasTokenAssociate();
    }

    @Override
    public FeeData usageGiven(
            final TransactionBody txn, final SigValueObj svo, final StateView view)
            throws InvalidTxBodyException {
        final var op = txn.getTokenAssociate();
        final var account = view.accounts().get(fromAccountId(op.getAccount()));
        if (account == null) {
            return FeeData.getDefaultInstance();
        } else {
            final var sigUsage =
                    new SigUsage(
                            svo.getTotalSigCount(),
                            svo.getSignatureSize(),
                            svo.getPayerAcctSigCount());
            final var estimate = factory.apply(txn, sigUsage);
            return estimate.givenCurrentExpiry(account.getExpiry()).get();
        }
    }
}
