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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.spi.key.HederaKey.asHederaKey;

import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * An implementation of {@code CryptoPreTransactionHandler} for validating all transactions defined
 * in the protobuf "CryptoService" in pre-handle. It adds all the verified signatures to the
 * including signature verification.
 */
public final class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final AccountStore accountStore;

    public CryptoPreTransactionHandlerImpl(@Nonnull final AccountStore accountStore) {
        this.accountStore = Objects.requireNonNull(accountStore);
    }

    public TransactionMetadata preHandleCryptoCreate(final TransactionBody tx) {
        final var op = tx.getCryptoCreateAccount();
        final var key = asHederaKey(op.getKey());
        final var receiverSigReq = op.getReceiverSigRequired();
        final var payer = tx.getTransactionID().getAccountID();
        return createAccountSigningMetadata(tx, key, receiverSigReq, payer);
    }

    private TransactionMetadata createAccountSigningMetadata(
            final TransactionBody tx,
            final Optional<HederaKey> key,
            final boolean receiverSigReq,
            final AccountID payer) {
        if (receiverSigReq && key.isPresent()) {
            return new SigTransactionMetadata(accountStore, tx, payer, List.of(key.get()));
        }
        return new SigTransactionMetadata(accountStore, tx, payer);
    }
}
