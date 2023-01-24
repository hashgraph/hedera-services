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
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadataBuilder;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoUpdate}.
 */
public class CryptoUpdateHandler implements TransactionHandler {

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param meta the {@link TransactionMetadataBuilder} which collects all information that
     *                will be passed to {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final TransactionMetadataBuilder<?> meta,
            @NonNull final CryptoSignatureWaivers waivers) {
        requireNonNull(meta);
        requireNonNull(waivers);
        final var txn = meta.getTxn();
        final var payer = meta.getPayer();
        final var op = txn.getCryptoUpdateAccount();
        final var updateAccountId = op.getAccountIDToUpdate();

        final var newAccountKeyMustSign = !waivers.isNewKeySignatureWaived(txn, payer);
        final var targetAccountKeyMustSign = !waivers.isTargetAccountSignatureWaived(txn, payer);
        if (targetAccountKeyMustSign) {
            meta.addNonPayerKey(updateAccountId);
        }
        if (newAccountKeyMustSign && op.hasKey()) {
            final var candidate = asHederaKey(op.getKey());
            candidate.ifPresent(meta::addToReqNonPayerKeys);
        }
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
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }
}
