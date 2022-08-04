/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.util.TokenUpdateValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Provides the state transition for token updates. */
@Singleton
public class TokenUpdateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(TokenUpdateTransitionLogic.class);

    private final boolean allowChangedTreasuryToOwnNfts;
    private final TokenStore store;
    private final HederaLedger ledger;
    private final OptionValidator validator;
    private final TransactionContext txnCtx;
    private final SigImpactHistorian sigImpactHistorian;
    private final Predicate<TokenUpdateTransactionBody> affectsExpiryOnly;

    @Inject
    public TokenUpdateTransitionLogic(
            final @AreTreasuryWildcardsEnabled boolean allowChangedTreasuryToOwnNfts,
            final OptionValidator validator,
            final TokenStore store,
            final HederaLedger ledger,
            final TransactionContext txnCtx,
            final SigImpactHistorian sigImpactHistorian,
            final Predicate<TokenUpdateTransactionBody> affectsExpiryOnly) {
        this.validator = validator;
        this.store = store;
        this.ledger = ledger;
        this.txnCtx = txnCtx;
        this.affectsExpiryOnly = affectsExpiryOnly;
        this.sigImpactHistorian = sigImpactHistorian;
        this.allowChangedTreasuryToOwnNfts = allowChangedTreasuryToOwnNfts;
    }

    @Override
    public void doStateTransition() {
        try {
            transitionFor(txnCtx.accessor().getTxn().getTokenUpdate());
        } catch (Exception e) {
            log.warn(
                    "Unhandled error while processing :: {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    e);
            abortWith(FAIL_INVALID);
        }
    }

    private void transitionFor(TokenUpdateTransactionBody op) {
        var id = store.resolve(op.getToken());
        if (id == MISSING_TOKEN) {
            txnCtx.setStatus(INVALID_TOKEN_ID);
            return;
        }

        ResponseCodeEnum outcome;
        MerkleToken token = store.get(id);

        if (op.hasExpiry() && !validator.isValidExpiry(op.getExpiry())) {
            txnCtx.setStatus(INVALID_EXPIRATION_TIME);
            return;
        }

        if (token.adminKey().isEmpty() && !affectsExpiryOnly.test(op)) {
            txnCtx.setStatus(TOKEN_IS_IMMUTABLE);
            return;
        }

        if (token.isDeleted()) {
            txnCtx.setStatus(TOKEN_WAS_DELETED);
            return;
        }

        if (token.isPaused()) {
            txnCtx.setStatus(TOKEN_IS_PAUSED);
            return;
        }

        outcome = autoRenewAttachmentCheck(op, token);
        if (outcome != OK) {
            txnCtx.setStatus(outcome);
            return;
        }

        Optional<AccountID> replacedTreasury = Optional.empty();
        if (op.hasTreasury()) {
            var newTreasury = op.getTreasury();
            if (ledger.isDetached(newTreasury)) {
                txnCtx.setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
                return;
            }
            if (!store.associationExists(newTreasury, id)) {
                outcome = store.autoAssociate(newTreasury, id);
                if (outcome != OK) {
                    abortWith(outcome);
                    return;
                }
            }
            var existingTreasury = token.treasury().toGrpcAccountId();
            if (!allowChangedTreasuryToOwnNfts && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
                var existingTreasuryBalance = ledger.getTokenBalance(existingTreasury, id);
                if (existingTreasuryBalance > 0L) {
                    abortWith(CURRENT_TREASURY_STILL_OWNS_NFTS);
                    return;
                }
            }
            if (!newTreasury.equals(existingTreasury)) {
                if (ledger.isDetached(existingTreasury)) {
                    txnCtx.setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
                    return;
                }
                outcome = prepTreasuryChange(id, token, newTreasury, existingTreasury);
                if (outcome != OK) {
                    abortWith(outcome);
                    return;
                }
                replacedTreasury = Optional.of(token.treasury().toGrpcAccountId());
            }
        }

        outcome = store.update(op, txnCtx.consensusTime().getEpochSecond());
        if (outcome == OK && replacedTreasury.isPresent()) {
            final var oldTreasury = replacedTreasury.get();
            long replacedTreasuryBalance = ledger.getTokenBalance(oldTreasury, id);
            if (replacedTreasuryBalance > 0) {
                if (token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
                    outcome =
                            ledger.doTokenTransfer(
                                    id, oldTreasury, op.getTreasury(), replacedTreasuryBalance);
                } else {
                    outcome =
                            store.changeOwnerWildCard(
                                    new NftId(
                                            id.getShardNum(),
                                            id.getRealmNum(),
                                            id.getTokenNum(),
                                            -1),
                                    oldTreasury,
                                    op.getTreasury());
                }
            }
        }
        if (outcome != OK) {
            abortWith(outcome);
            return;
        }

        txnCtx.setStatus(SUCCESS);
        sigImpactHistorian.markEntityChanged(id.getTokenNum());
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenUpdate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return TokenUpdateValidator.validate(txnBody, validator);
    }

    private ResponseCodeEnum autoRenewAttachmentCheck(
            TokenUpdateTransactionBody op, MerkleToken token) {
        if (op.hasAutoRenewAccount()) {
            final var newAutoRenew = op.getAutoRenewAccount();
            if (ledger.isDetached(newAutoRenew)) {
                return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
            }
            if (token.hasAutoRenewAccount()) {
                final var existingAutoRenew = token.autoRenewAccount().toGrpcAccountId();
                if (ledger.isDetached(existingAutoRenew)) {
                    return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
                }
            }
        }
        return OK;
    }

    private ResponseCodeEnum prepTreasuryChange(
            final TokenID id,
            final MerkleToken token,
            final AccountID newTreasury,
            final AccountID oldTreasury) {
        var status = OK;
        if (token.hasFreezeKey()) {
            status = ledger.unfreeze(newTreasury, id);
        }
        if (status == OK && token.hasKycKey()) {
            status = ledger.grantKyc(newTreasury, id);
        }
        if (status == OK) {
            ledger.decrementNumTreasuryTitles(oldTreasury);
            ledger.incrementNumTreasuryTitles(newTreasury);
        }
        return status;
    }

    private void abortWith(ResponseCodeEnum cause) {
        ledger.dropPendingTokenChanges();
        txnCtx.setStatus(cause);
    }
}
