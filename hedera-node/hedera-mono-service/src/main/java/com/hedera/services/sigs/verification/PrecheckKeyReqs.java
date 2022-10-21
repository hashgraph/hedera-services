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
package com.hedera.services.sigs.verification;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.sigs.annotations.WorkingStateSigReqs;
import com.hedera.services.sigs.order.SigRequirements;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Encapsulates logic to determine which Hedera keys need to have valid signatures for a transaction
 * to pass precheck.
 */
@Singleton
public class PrecheckKeyReqs {
    private static final Set<ResponseCodeEnum> INVALID_ACCOUNT_STATUSES =
            EnumSet.of(INVALID_ACCOUNT_ID, INVALID_AUTORENEW_ACCOUNT, ACCOUNT_ID_DOES_NOT_EXIST);

    private final SigRequirements sigReqs;
    private final Predicate<TransactionBody> isQueryPayment;

    @Inject
    public PrecheckKeyReqs(
            final @WorkingStateSigReqs SigRequirements sigReqs,
            final Predicate<TransactionBody> isQueryPayment) {
        this.sigReqs = sigReqs;
        this.isQueryPayment = isQueryPayment;
    }

    /**
     * Returns a list of Hedera keys which must have valid signatures for the given {@link
     * TransactionBody} to pass precheck.
     *
     * @param txn a gRPC txn.
     * @return a list of keys precheck requires to have active signatures.
     * @throws Exception if the txn does not reference valid keys.
     */
    public List<JKey> getRequiredKeys(TransactionBody txn) throws Exception {
        List<JKey> keys = new ArrayList<>();

        addPayerKeys(txn, keys);
        if (isQueryPayment.test(txn)) {
            addQueryPaymentKeys(txn, keys);
        }

        return keys;
    }

    private void addPayerKeys(TransactionBody txn, List<JKey> keys) throws Exception {
        final var payerResult = sigReqs.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY);
        if (payerResult.hasErrorReport()) {
            throw new InvalidPayerAccountException();
        }
        keys.addAll(payerResult.getOrderedKeys());
    }

    private void addQueryPaymentKeys(TransactionBody txn, List<JKey> keys) throws Exception {
        final var otherResult = sigReqs.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY);
        if (otherResult.hasErrorReport()) {
            final var errorStatus = otherResult.getErrorReport();
            if (INVALID_ACCOUNT_STATUSES.contains(errorStatus)) {
                throw new InvalidAccountIDException();
            } else {
                throw new Exception();
            }
        }

        for (var nonPayerKey : otherResult.getOrderedKeys()) {
            if (!nonPayerKey.equals(keys.get(0))) {
                keys.add(nonPayerKey);
            }
        }
    }
}
