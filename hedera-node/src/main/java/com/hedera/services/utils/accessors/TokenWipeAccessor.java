/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.accessors;

import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hedera.services.txns.validation.ContextOptionValidator.batchSizeCheck;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;

/**
 * Specialized accessor for TokenWipe transaction. Uses the latest signed state view for looking up
 * alias in the ServicesState.
 */
public class TokenWipeAccessor extends SignedTxnAccessor {
    private final TokenWipeAccountTransactionBody body;
    private final boolean areNftsEnabled;
    private final int maxBatchSizeWipe;

    public TokenWipeAccessor(
            final byte[] signedTxnWrapperBytes,
            final Transaction txn,
            final GlobalDynamicProperties dynamicProperties)
            throws InvalidProtocolBufferException {
        super(signedTxnWrapperBytes, txn);
        this.body = getTxn().getTokenWipe();
        this.areNftsEnabled = dynamicProperties.areNftsEnabled();
        this.maxBatchSizeWipe = dynamicProperties.maxBatchSizeWipe();
        setTokenWipeUsageMeta();
    }

    public Id accountToWipe() {
        return unaliased(body.getAccount()).toId();
    }

    public Id targetToken() {
        return Id.fromGrpcToken(body.getToken());
    }

    public List<Long> serialNums() {
        return body.getSerialNumbersList();
    }

    public long amount() {
        return body.getAmount();
    }

    @Override
    public boolean supportsPrecheck() {
        return true;
    }

    @Override
    public ResponseCodeEnum doPrecheck() {
        return validateSyntax(body, areNftsEnabled, maxBatchSizeWipe);
    }

    public static ResponseCodeEnum validateSyntax(
            final TokenWipeAccountTransactionBody body,
            final boolean areNftsEnabled,
            final int maxBatchSizeWipe) {
        if (!body.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!body.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }
        return validateTokenOpsWith(
                body.getSerialNumbersCount(),
                body.getAmount(),
                areNftsEnabled,
                INVALID_WIPING_AMOUNT,
                body.getSerialNumbersList(),
                a -> batchSizeCheck(a, maxBatchSizeWipe));
    }

    private void setTokenWipeUsageMeta() {
        final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(body);
        getSpanMapAccessor().setTokenWipeMeta(this, tokenWipeMeta);
    }
}
