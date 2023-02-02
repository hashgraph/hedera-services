/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;

import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenUpdate}.
 *
 * <p><b>NOTE:</b> this class intentionally changes the following error response codes relative to
 * SigRequirements:
 *
 * <ol>
 *   <li>When a missing account is used as a token treasury, fails with {@code INVALID_ACCOUNT_ID}
 *       rather than {@code ACCOUNT_ID_DOES_NOT_EXIST}.
 * </ol>
 *
 * * EET expectations may need to be updated accordingly
 */
public class TokenUpdateHandler implements TransactionHandler {

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param txn the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup keyLookup,
            @NonNull final ReadableTokenStore tokenStore) {
        final var op = txn.getTokenUpdate();
        final var tokenId = op.getToken();
        final var meta =
                new SigTransactionMetadataBuilder(keyLookup).payerKeyFor(payer).txnBody(txn);
        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        if (tokenMeta.failed()) {
            return meta.status(tokenMeta.failureReason()).build();
        }
        final var tokenMetadata = tokenMeta.metadata();
        final var adminKey = tokenMetadata.adminKey();
        adminKey.ifPresent(meta::addToReqNonPayerKeys);
        if (op.hasAutoRenewAccount()) {
            meta.addNonPayerKey(op.getAutoRenewAccount(), INVALID_AUTORENEW_ACCOUNT);
        }
        if (op.hasTreasury()) {
            meta.addNonPayerKey(op.getTreasury());
        }
        if (op.hasAdminKey()) {
            final var newAdminKey = asHederaKey(op.getAdminKey());
            newAdminKey.ifPresent(meta::addToReqNonPayerKeys);
        }
        return meta.build();
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
