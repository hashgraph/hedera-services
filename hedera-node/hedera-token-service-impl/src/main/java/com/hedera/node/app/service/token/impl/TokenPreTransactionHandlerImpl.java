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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@code TokenPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each token operation.
 */
public final class TokenPreTransactionHandlerImpl implements TokenPreTransactionHandler {
    private final ReadableAccountStore accountStore;
    private final ReadableTokenStore tokenStore;
    private final PreHandleContext preHandleContext;

    public TokenPreTransactionHandlerImpl(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final PreHandleContext ctx) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.preHandleContext = Objects.requireNonNull(ctx);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCreateToken(
            final TransactionBody txn, final AccountID payer) {
        final var tokenCreateTxnBody = txn.getTokenCreation();
        final var customFees = tokenCreateTxnBody.getCustomFeesList();
        final var treasuryId = tokenCreateTxnBody.getTreasury();
        final var autoRenewalAccountId = tokenCreateTxnBody.getAutoRenewAccount();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        meta.addNonPayerKey(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        meta.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        if (tokenCreateTxnBody.hasAdminKey()) {
            final var adminKey = asHederaKey(tokenCreateTxnBody.getAdminKey());
            adminKey.ifPresent(meta::addToReqNonPayerKeys);
        }
        addCustomFeeKey(payer, meta, customFees);
        return meta.build();
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
    public TransactionMetadata preHandlePauseToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        final var op = txn.getTokenPause();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        if (op.hasToken()) {
            addPauseKey(op.getToken(), meta);
        } else {
            meta.status(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUnpauseToken(
            @NonNull final TransactionBody txn, @NonNull final AccountID payer) {
        Objects.requireNonNull(txn);
        final var op = txn.getTokenUnpause();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        if (op.hasToken()) {
            addPauseKey(op.getToken(), meta);
        } else {
            meta.status(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        return meta.build();
    }

    /**
     * Gets the token meta for a given {@link TokenID} and attempts to add a pause key to the list
     * of required keys for a given pause or unpause transaction. Upon failure the status of the
     * {@link SigTransactionMetadataBuilder} is set to the corresponding {@link ResponseCodeEnum}
     *
     * @param tokenId given token id
     * @param meta given transaction metadata builder
     */
    private void addPauseKey(TokenID tokenId, SigTransactionMetadataBuilder meta) {
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

    private void addCustomFeeKey(
            final AccountID payer,
            SigTransactionMetadataBuilder meta,
            final List<CustomFee> customFeesList) {
        final var hasSigRecKey = accountStore.getKeyIfReceiverSigRequired(payer);
        final var failureStatus = INVALID_FEE_COLLECTOR_ACCOUNT_ID;
        for (final var customFee : customFeesList) {
            final var collector = customFee.getFeeCollectorAccountId();
            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.getFixedFee();
                final var alwaysAdd =
                        fixedFee.hasDenominatingTokenId()
                                && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
                if (alwaysAdd) {
                    meta.addNonPayerKey(collector, failureStatus);
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            collector, INVALID_CUSTOM_FEE_COLLECTOR);
                }
                return;
            } else if (customFee.hasFractionalFee()) {
                meta.addNonPayerKey(collector, failureStatus);
                return;
            } else {
                final var royaltyFee = customFee.getRoyaltyFee();
                var alwaysAdd = false;
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.getFallbackFee();
                    alwaysAdd =
                            fFee.hasDenominatingTokenId()
                                    && fFee.getDenominatingTokenId().getTokenNum() == 0;
                }
                if (alwaysAdd) {
                    meta.addNonPayerKey(collector, failureStatus);
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            collector, INVALID_CUSTOM_FEE_COLLECTOR);
                }
                return;
            }
        }
    }
}
