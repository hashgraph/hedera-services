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
package com.hedera.node.app.service.consensus.impl.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_CREATE_TOPIC}.
 */
public class ConsensusCreateTopicHandler implements TransactionHandler {

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * @param txBody the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param keyLookup the {@link AccountKeyLookup} to use for key lookups
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup keyLookup) {
        final var metaBuilder =
                new SigTransactionMetadataBuilder(keyLookup).txnBody(txBody).payerKeyFor(payer);

        final var op = txBody.getConsensusCreateTopic();
        final var adminKey = asHederaKey(op.getAdminKey());
        adminKey.ifPresent(metaBuilder::addToReqNonPayerKeys);
        final var submitKey = asHederaKey(op.getSubmitKey());
        submitKey.ifPresent(metaBuilder::addToReqNonPayerKeys);

        if (op.hasAutoRenewAccount()) {
            final var autoRenewAccount = op.getAutoRenewAccount();
            metaBuilder.addNonPayerKey(
                    autoRenewAccount, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
        }

        return metaBuilder.build();
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
