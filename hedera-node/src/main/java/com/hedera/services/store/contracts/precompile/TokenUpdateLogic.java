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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateFalseOrRevert;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.ledger.TransferLogic.dropTokenChanges;
import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.store.tokens.HederaTokenStore.affectsExpiryAtMost;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.services.txns.util.TokenUpdateValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import javax.inject.Inject;

public class TokenUpdateLogic {
    private final OptionValidator validator;
    private final HederaTokenStore tokenStore;
    private final WorldLedgers worldLedgers;
    private final SideEffectsTracker sideEffectsTracker;
    private final SigImpactHistorian sigImpactHistorian;
    boolean allowChangedTreasuryToOwnNfts;

    @Inject
    public TokenUpdateLogic(
            final @AreTreasuryWildcardsEnabled boolean allowChangedTreasuryToOwnNfts,
            OptionValidator validator,
            HederaTokenStore tokenStore,
            WorldLedgers worldLedgers,
            SideEffectsTracker sideEffectsTracker,
            SigImpactHistorian sigImpactHistorian) {
        this.validator = validator;
        this.tokenStore = tokenStore;
        this.worldLedgers = worldLedgers;
        this.sideEffectsTracker = sideEffectsTracker;
        this.sigImpactHistorian = sigImpactHistorian;
        this.allowChangedTreasuryToOwnNfts = allowChangedTreasuryToOwnNfts;
    }

    public void updateToken(TokenUpdateTransactionBody op, long now) {
        final var tokenID = tokenValidityCheck(op);
        if (op.hasExpiry()) {
            validateTrueOrRevert(validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
        }
        MerkleToken token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);

        ResponseCodeEnum outcome = autoRenewAttachmentCheck(op, token);
        validateTrueOrRevert(outcome == OK, outcome);

        Optional<AccountID> replacedTreasury = Optional.empty();
        if (op.hasTreasury()) {
            var newTreasury = op.getTreasury();
            validateFalseOrRevert(isDetached(newTreasury), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

            if (!tokenStore.associationExists(newTreasury, tokenID)) {
                outcome = tokenStore.autoAssociate(newTreasury, tokenID);
                if (outcome != OK) {
                    abortWith(outcome);
                }
            }
            var existingTreasury = token.treasury().toGrpcAccountId();
            if (!allowChangedTreasuryToOwnNfts && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
                var existingTreasuryBalance = getTokenBalance(existingTreasury, tokenID);
                if (existingTreasuryBalance > 0L) {
                    abortWith(CURRENT_TREASURY_STILL_OWNS_NFTS);
                }
            }
            if (!newTreasury.equals(existingTreasury)) {
                validateFalseOrRevert(
                        isDetached(existingTreasury), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

                outcome = prepTreasuryChange(tokenID, token, newTreasury, existingTreasury);
                if (outcome != OK) {
                    abortWith(outcome);
                }
                replacedTreasury = Optional.of(token.treasury().toGrpcAccountId());
            }
        }

        outcome = tokenStore.update(op, now);
        if (outcome == OK && replacedTreasury.isPresent()) {
            final var oldTreasury = replacedTreasury.get();
            long replacedTreasuryBalance = getTokenBalance(oldTreasury, tokenID);
            if (replacedTreasuryBalance > 0) {
                if (token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
                    outcome =
                            doTokenTransfer(
                                    tokenID,
                                    oldTreasury,
                                    op.getTreasury(),
                                    replacedTreasuryBalance);
                } else {
                    outcome =
                            tokenStore.changeOwnerWildCard(
                                    new NftId(
                                            tokenID.getShardNum(),
                                            tokenID.getRealmNum(),
                                            tokenID.getTokenNum(),
                                            -1),
                                    oldTreasury,
                                    op.getTreasury());
                }
            }
        }
        if (outcome != OK) {
            abortWith(outcome);
        }
        sigImpactHistorian.markEntityChanged(tokenID.getTokenNum());
    }

    public void updateTokenExpiryInfo(TokenUpdateTransactionBody op) {
        final var tokenID = tokenStore.resolve(op.getToken());
        validateTrueOrRevert(!tokenID.equals(MISSING_TOKEN), INVALID_TOKEN_ID);
        if (op.hasExpiry()) {
            validateTrueOrRevert(validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
        }
        MerkleToken token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);

        ResponseCodeEnum outcome = autoRenewAttachmentCheck(op, token);
        validateTrueOrRevert(outcome == OK, outcome);

        outcome = tokenStore.updateExpiryInfo(op);
        if (outcome != OK) {
            abortWith(outcome);
        }
        sigImpactHistorian.markEntityChanged(tokenID.getTokenNum());
    }

    public void updateTokenKeys(TokenUpdateTransactionBody op, long now) {
        final var tokenID = tokenValidityCheck(op);
        MerkleToken token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);
        final var outcome = tokenStore.update(op, now);

        if (outcome != OK) {
            abortWith(outcome);
        }
        sigImpactHistorian.markEntityChanged(tokenID.getTokenNum());
    }

    private TokenID tokenValidityCheck(TokenUpdateTransactionBody op) {
        final var tokenID = Id.fromGrpcToken(op.getToken()).asGrpcToken();
        validateFalse(tokenID.equals(MISSING_TOKEN), INVALID_TOKEN_ID);
        return tokenID;
    }

    private void checkTokenPreconditions(MerkleToken token, TokenUpdateTransactionBody op) {
        if (!token.hasAdminKey())
            validateTrueOrRevert((affectsExpiryAtMost(op)), TOKEN_IS_IMMUTABLE);
        validateFalseOrRevert(token.isDeleted(), TOKEN_WAS_DELETED);
        validateFalseOrRevert(token.isPaused(), TOKEN_IS_PAUSED);
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return TokenUpdateValidator.validate(txnBody, validator);
    }

    private ResponseCodeEnum autoRenewAttachmentCheck(
            TokenUpdateTransactionBody op, MerkleToken token) {
        if (op.hasAutoRenewAccount()) {
            final var newAutoRenew = op.getAutoRenewAccount();
            validateFalseOrRevert(isDetached(newAutoRenew), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

            if (token.hasAutoRenewAccount()) {
                final var existingAutoRenew = token.autoRenewAccount().toGrpcAccountId();
                validateFalseOrRevert(
                        isDetached(existingAutoRenew), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
            }
        }
        return OK;
    }

    private boolean isDetached(AccountID accountID) {
        return validator.expiryStatusGiven(worldLedgers.accounts(), accountID) != OK;
    }

    private ResponseCodeEnum prepTreasuryChange(
            final TokenID id,
            final MerkleToken token,
            final AccountID newTreasury,
            final AccountID oldTreasury) {
        var status = OK;
        if (token.hasFreezeKey()) {
            status = tokenStore.unfreeze(newTreasury, id);
        }
        if (status == OK && token.hasKycKey()) {
            status = tokenStore.grantKyc(newTreasury, id);
        }
        if (status == OK) {
            decrementNumTreasuryTitles(oldTreasury);
            incrementNumTreasuryTitles(newTreasury);
        }
        return status;
    }

    private void abortWith(ResponseCodeEnum cause) {
        dropTokenChanges(
                sideEffectsTracker,
                worldLedgers.nfts(),
                worldLedgers.accounts(),
                worldLedgers.tokenRels());
        throw new InvalidTransactionException(cause);
    }

    private ResponseCodeEnum doTokenTransfer(
            TokenID tId, AccountID from, AccountID to, long adjustment) {
        ResponseCodeEnum validity = tokenStore.adjustBalance(from, tId, -adjustment);
        if (validity == OK) {
            validity = tokenStore.adjustBalance(to, tId, adjustment);
        }

        if (validity != OK) {
            dropTokenChanges(
                    sideEffectsTracker,
                    worldLedgers.nfts(),
                    worldLedgers.accounts(),
                    worldLedgers.tokenRels());
        }
        return validity;
    }

    public long getTokenBalance(AccountID aId, TokenID tId) {
        var relationship = asTokenRel(aId, tId);
        return (long) worldLedgers.tokenRels().get(relationship, TOKEN_BALANCE);
    }

    private void incrementNumTreasuryTitles(final AccountID aId) {
        changeNumTreasuryTitles(aId, +1);
    }

    private void decrementNumTreasuryTitles(final AccountID aId) {
        changeNumTreasuryTitles(aId, -1);
    }

    private void changeNumTreasuryTitles(final AccountID aId, final int delta) {
        final var numTreasuryTitles = (int) worldLedgers.accounts().get(aId, NUM_TREASURY_TITLES);
        worldLedgers.accounts().set(aId, NUM_TREASURY_TITLES, numTreasuryTitles + delta);
    }
}
