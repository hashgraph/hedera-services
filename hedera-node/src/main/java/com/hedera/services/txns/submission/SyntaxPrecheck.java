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
package com.hedera.services.txns.submission;

import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.services.txns.validation.PureValidation.asCoercedInstant;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Clock;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Confirms that the parsed {@code TransactionBody} has all necessary fields set, including a
 * feasible valid start time and duration; and has a {@code TransactionID} that is believed to be
 * unique.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public class SyntaxPrecheck {
    private final RecordCache recordCache;
    private final OptionValidator validator;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public SyntaxPrecheck(
            RecordCache recordCache,
            OptionValidator validator,
            GlobalDynamicProperties dynamicProperties) {
        this.validator = validator;
        this.recordCache = recordCache;
        this.dynamicProperties = dynamicProperties;
    }

    public ResponseCodeEnum validate(TransactionBody txn) {
        if (!txn.hasTransactionID()) {
            return INVALID_TRANSACTION_ID;
        }
        var txnId = txn.getTransactionID();
        if (txnId.getScheduled() || txnId.getNonce() != USER_TRANSACTION_NONCE) {
            return TRANSACTION_ID_FIELD_NOT_ALLOWED;
        }
        if (recordCache.isReceiptPresent(txnId)) {
            return DUPLICATE_TRANSACTION;
        }
        if (!validator.isPlausibleTxnFee(txn.getTransactionFee())) {
            return INSUFFICIENT_TX_FEE;
        }
        if (!validator.isPlausibleAccount(txn.getTransactionID().getAccountID())) {
            return PAYER_ACCOUNT_NOT_FOUND;
        }
        if (!validator.isThisNodeAccount(txn.getNodeAccountID())) {
            return INVALID_NODE_ACCOUNT;
        }

        ResponseCodeEnum memoValidity = validator.memoCheck(txn.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }

        var validForSecs = txn.getTransactionValidDuration().getSeconds();
        if (!validator.isValidTxnDuration(validForSecs)) {
            return INVALID_TRANSACTION_DURATION;
        }

        return validator.chronologyStatusForTxn(
                asCoercedInstant(txn.getTransactionID().getTransactionValidStart()),
                validForSecs - dynamicProperties.minValidityBuffer(),
                Instant.now(Clock.systemUTC()));
    }
}
