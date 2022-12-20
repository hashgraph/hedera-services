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

import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@code TokenPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each token operation.
 */
public final class TokenPreTransactionHandlerImpl implements TokenPreTransactionHandler {
    private final AccountStore accountStore;
    private final TokenStore tokenStore;
    private final PreHandleContext preHandleContext;

    public TokenPreTransactionHandlerImpl(
            @NonNull final AccountStore accountStore,
            @NonNull final TokenStore tokenStore,
            @NonNull final PreHandleContext ctx) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.preHandleContext = Objects.requireNonNull(ctx);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCreateToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleMintToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleBurnToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleWipeTokenAccount(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        final var op = txn.getTokenWipe();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        if (op.hasToken()) {
            final var tokenMeta = tokenStore.getTokenMeta(op.getToken());
            if (!tokenMeta.failed()) {
                if (tokenMeta.metadata().wipeKey().isPresent()) {
                    meta.addToReqNonPayerKeys(tokenMeta.metadata().wipeKey().get());
                } else {
                    meta.status(ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY);
                }
            } else {
                meta.status(tokenMeta.failureReason());
            }
        } else {
            meta.status(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleFreezeTokenAccount(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUnfreezeTokenAccount(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleGrantKycToTokenAccount(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleRevokeKycFromTokenAccount(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleAssociateTokens(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDissociateTokens(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateTokenFeeSchedule(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandlePauseToken(@NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        final var op = txn.getTokenPause();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        if (op.hasToken()) {
            handlePauseUnpause(op.getToken(), meta);
        } else {
            meta.status(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUnpauseToken(@NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        final var op = txn.getTokenUnpause();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        if (op.hasToken()) {
            handlePauseUnpause(op.getToken(), meta);
        } else {
            meta.status(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        return meta.build();
    }

    private void handlePauseUnpause(TokenID tokenId, SigTransactionMetadataBuilder meta) {
        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        if (!tokenMeta.failed()) {
            if (tokenMeta.metadata().pauseKey().isPresent()) {
                meta.addToReqNonPayerKeys(tokenMeta.metadata().pauseKey().get());
            } else {
                meta.status(ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY);
            }
        } else {
            meta.status(tokenMeta.failureReason());
        }
    }
}
