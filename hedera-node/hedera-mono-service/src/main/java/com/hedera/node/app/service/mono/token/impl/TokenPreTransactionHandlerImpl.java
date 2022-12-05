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
package com.hedera.node.app.service.mono.token.impl;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;

import com.hedera.node.app.service.mono.SigTransactionMetadata;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code TokenPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each token operation.
 */
public final class TokenPreTransactionHandlerImpl implements TokenPreTransactionHandler {

    private final AccountStore accountStore;
    private final PreHandleContext preHandleContext;

    public TokenPreTransactionHandlerImpl(
            @NonNull final AccountStore accountStore, @NonNull final PreHandleContext ctx) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.preHandleContext = Objects.requireNonNull(ctx);
    }

    @Override
    public TransactionMetadata preHandleCreateToken(final TransactionBody txn) {
        final var op = txn.getTokenCreation();
        final var payer = txn.getTransactionID().getAccountID();
        final var createAccountId = op.getTreasury();
        final var autoRenewalAccountId = op.getAutoRenewAccount();
        final var meta = new SigTransactionMetadata(accountStore, txn, payer);
        meta.addNonPayerKey(createAccountId);
        meta.addNonPayerKeyIfReceiverSigRequired(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        return meta;
    }

    @Override
    public TransactionMetadata preHandleUpdateToken(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleMintToken(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleBurnToken(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleDeleteToken(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleWipeTokenAccount(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleFreezeTokenAccount(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleUnfreezeTokenAccount(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleGrantKycToTokenAccount(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleRevokeKycFromTokenAccount(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleAssociateTokens(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleDissociateTokens(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleUpdateTokenFeeSchedule(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandlePauseToken(final TransactionBody txn) {
        return null;
    }

    @Override
    public TransactionMetadata preHandleUnpauseToken(final TransactionBody txn) {
        return null;
    }

    /**
     * Returns metadata for {@code CryptoCreate} transaction needed to validate signatures needed
     * for signing the transaction
     *
     * @param tx given transaction body
     * @param key key provided in the transaction body
     * @param receiverSigReq flag for receiverSigReq on the given transaction body
     * @param payer payer for the transaction
     * @return transaction's metadata needed to validate signatures
     */
    private TransactionMetadata createAccountSigningMetadata(
            final TransactionBody tx,
            final Optional<HederaKey> key,
            final boolean receiverSigReq,
            final AccountID payer) {
        final var meta = new SigTransactionMetadata(accountStore, tx, payer);
        if (receiverSigReq && key.isPresent()) {
            meta.addToReqKeys(key.get());
        }
        return meta;
    }
}
