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

import static com.hedera.node.app.service.mono.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Objects;

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
        final var payer = txn.getTransactionID().getAccountID();
        final var receiverSigReq = accountStore.getKeyIfReceiverSigRequired(payer) == null;
        return buildSigTransactionMetadata(txn, payer, receiverSigReq);
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
     * Returns metadata for {@code TokenCreate} transaction needed to validate signatures needed
     * for signing the transaction
     *
     * @param txn            given transaction body
     * @param payer          payer for the transaction
     * @param receiverSigReq flag for receiverSigReq on the given transaction body
     * @return transaction's metadata needed to validate signatures
     */
    private TransactionMetadata buildSigTransactionMetadata(
            final TransactionBody txn,
            final AccountID payer,
            final boolean receiverSigReq) {
        final var tokenCreateTxnBody = txn.getTokenCreation();
        final var customFees = tokenCreateTxnBody.getCustomFeesList();
        final var treasuryId = tokenCreateTxnBody.getTreasury();
        final var autoRenewalAccountId = tokenCreateTxnBody.getAutoRenewAccount();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        meta.addNonPayerKey(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        meta.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        if (tokenCreateTxnBody.hasAdminKey()) {
            final var adminKey = asUsableFcKey(tokenCreateTxnBody.getAdminKey());
            adminKey.ifPresent(meta::addToReqKeys);
        }
        addCustomFeeKey(meta, customFees, receiverSigReq);
        return meta.build();
    }

    private void addCustomFeeKey(final SigTransactionMetadataBuilder meta, final List<CustomFee> customFeesList,
                                 final boolean sigRequirement) {
        final var failureStatus = INVALID_FEE_COLLECTOR_ACCOUNT_ID;
        for (final var customFee : customFeesList) {
            final var collector = customFee.getFeeCollectorAccountId();
            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            final boolean alwaysAdd;
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.getFixedFee();
                alwaysAdd = fixedFee.hasDenominatingTokenId()
                        && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
                if (alwaysAdd || sigRequirement) {
                    meta.addNonPayerKey(collector, failureStatus);
                }
            } else if (customFee.hasFractionalFee()) {
                meta.addNonPayerKey(collector, failureStatus);
            } else if (customFee.hasRoyaltyFee()) {
                final var royaltyFee = customFee.getRoyaltyFee();
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.getFallbackFee();
                    alwaysAdd = fFee.hasDenominatingTokenId() && fFee.getDenominatingTokenId().getTokenNum() == 0;
                    if (alwaysAdd || sigRequirement) {
                        meta.addNonPayerKey(collector, failureStatus);
                    }
                }
            } else {
                meta.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
            }
        }
    }

}
