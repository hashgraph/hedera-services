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

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.TransactionMetadata;
import com.hederahashgraph.api.proto.java.Transaction;

/**
 * An implementation of {@code CryptoPreTransactionHandler} for validating all transactions defined
 * in the protobuf "CryptoService" in pre-handle. It adds all the verified signatures to the
 * including signature verification.
 */
public class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final AccountStore accountStore;

    public CryptoPreTransactionHandlerImpl(final AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public TransactionMetadata cryptoCreate(final Transaction tx) {
        try {
            final var txn = extractTransactionBody(tx);
            final var op = txn.getCryptoCreateAccount();
            final var key = asUsableFcKey(op.getKey());
            final var receiverSigReq = op.getReceiverSigRequired();
            final var payer = txn.getTransactionID().getAccountID();
            return accountStore.createAccountSigningMetadata(tx, key, receiverSigReq, payer);
        } catch (InvalidProtocolBufferException ex) {
            return new TransactionMetadata(tx, true);
        }
    }
}
