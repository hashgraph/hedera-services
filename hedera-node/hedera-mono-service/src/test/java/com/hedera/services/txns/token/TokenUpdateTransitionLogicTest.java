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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUpdateTransitionLogicTest {
    long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);
    private TokenID target = IdUtils.asToken("1.2.666");
    private NftId nftId =
            new NftId(target.getShardNum(), target.getRealmNum(), target.getTokenNum(), -1);
    private AccountID oldTreasury = IdUtils.asAccount("1.2.4");
    private AccountID newTreasury = IdUtils.asAccount("1.2.5");
    private AccountID newAutoRenew = IdUtils.asAccount("5.2.1");
    private AccountID oldAutoRenew = IdUtils.asAccount("4.2.1");
    private String symbol = "SYMBOL";
    private String name = "Name";
    private JKey adminKey = new JEd25519Key("w/e".getBytes());
    private TransactionBody tokenUpdateTxn;
    private MerkleToken token;

    private OptionValidator validator;
    private TokenStore store;
    private HederaLedger ledger;
    private TransactionContext txnCtx;
    private SigImpactHistorian sigImpactHistorian;
    private SignedTxnAccessor accessor;
    private Predicate<TokenUpdateTransactionBody> expiryOnlyCheck;

    private TokenUpdateTransitionLogic subject;

    @BeforeEach
    void setup() {
        validator = mock(OptionValidator.class);
        store = mock(TokenStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(SignedTxnAccessor.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);

        token = mock(MerkleToken.class);
        given(token.adminKey()).willReturn(Optional.of(adminKey));
        given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(oldTreasury));
        given(token.autoRenewAccount()).willReturn(EntityId.fromGrpcAccountId(oldAutoRenew));
        given(token.hasAutoRenewAccount()).willReturn(true);
        given(token.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(store.resolve(target)).willReturn(target);
        given(store.get(target)).willReturn(token);
        given(store.associationExists(newTreasury, target)).willReturn(true);
        given(store.associationExists(oldTreasury, target)).willReturn(true);
        withAlwaysValidValidator();

        txnCtx = mock(TransactionContext.class);

        expiryOnlyCheck = (Predicate<TokenUpdateTransactionBody>) mock(Predicate.class);
        given(expiryOnlyCheck.test(any())).willReturn(false);

        subject =
                new TokenUpdateTransitionLogic(
                        true,
                        validator,
                        store,
                        ledger,
                        txnCtx,
                        sigImpactHistorian,
                        expiryOnlyCheck);
    }

    @Test
    void abortsOnInvalidIdForSafety() {
        givenValidTxnCtx(true);
        given(store.resolve(target)).willReturn(TokenStore.MISSING_TOKEN);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_TOKEN_ID);
    }

    @Test
    void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx(false);
        givenToken(true, true);

        given(store.update(any(), anyLong())).willThrow(IllegalStateException.class);

        subject.doStateTransition();

        verify(txnCtx).setStatus(FAIL_INVALID);
        verify(ledger).dropPendingTokenChanges();
    }

    @Test
    void abortsIfCreationFails() {
        givenValidTxnCtx();
        given(store.update(any(), anyLong())).willReturn(INVALID_TOKEN_SYMBOL);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_TOKEN_SYMBOL);
        verify(ledger).dropPendingTokenChanges();
    }

    @Test
    void rollsbackNewTreasuryChangesIfUpdateFails() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
        given(store.update(any(), anyLong())).willReturn(INVALID_TOKEN_SYMBOL);

        subject.doStateTransition();

        verify(ledger).dropPendingTokenChanges();
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
        verify(txnCtx).setStatus(INVALID_TOKEN_SYMBOL);
    }

    @Test
    void worksWithUnassociatedNewTreasury() {
        final long oldTreasuryBalance = 10;
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(store.associationExists(newTreasury, target)).willReturn(false);
        given(store.autoAssociate(newTreasury, target)).willReturn(OK);
        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
        given(store.update(any(), anyLong())).willReturn(OK);
        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
        given(ledger.doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance))
                .willReturn(OK);

        subject.doStateTransition();

        verify(txnCtx).setStatus(SUCCESS);
        verify(ledger).decrementNumTreasuryTitles(oldTreasury);
        verify(ledger).incrementNumTreasuryTitles(newTreasury);
        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    }

    @Test
    void abortsOnFailedAutoAssociationForUnassociatedNewTreasury() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(store.associationExists(newTreasury, target)).willReturn(false);
        given(store.autoAssociate(newTreasury, target))
                .willReturn(NO_REMAINING_AUTOMATIC_ASSOCIATIONS);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(txnCtx).setStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void abortsOnInvalidNewTreasury() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.unfreeze(newTreasury, target)).willReturn(INVALID_ACCOUNT_ID);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(ledger).unfreeze(newTreasury, target);
        verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void abortsOnDetachedNewTreasury() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.usabilityOf(newTreasury)).willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void abortsOnDetachedOldTreasury() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.isDetached(oldTreasury)).willReturn(true);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void abortsOnDetachedOldAutoRenew() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.usabilityOf(newAutoRenew)).willReturn(OK);
        given(ledger.isDetached(oldAutoRenew)).willReturn(true);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void abortsOnDetachedNewAutoRenew() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.usabilityOf(newAutoRenew)).willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void abortsOnDeletedAutoRenew() {
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.usabilityOf(newAutoRenew)).willReturn(ACCOUNT_DELETED);

        subject.doStateTransition();

        verify(store, never()).update(any(), anyLong());
        verify(txnCtx).setStatus(INVALID_AUTORENEW_ACCOUNT);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    }

    @Test
    void permitsExtendingExpiry() {
        givenValidTxnCtx(false);
        given(token.adminKey()).willReturn(Optional.empty());
        given(expiryOnlyCheck.test(any())).willReturn(true);
        given(store.update(any(), anyLong())).willReturn(OK);

        subject.doStateTransition();

        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void abortsOnNotSetAdminKey() {
        givenValidTxnCtx(true);
        given(token.adminKey()).willReturn(Optional.empty());

        subject.doStateTransition();

        verify(txnCtx).setStatus(TOKEN_IS_IMMUTABLE);
    }

    @Test
    void abortsOnInvalidNewExpiry() {
        final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();

        final var builder =
                TransactionBody.newBuilder()
                        .setTokenUpdate(TokenUpdateTransactionBody.newBuilder().setExpiry(expiry));
        tokenUpdateTxn = builder.build();
        given(accessor.getTxn()).willReturn(tokenUpdateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(validator.isValidExpiry(expiry)).willReturn(false);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
    }

    @Test
    void abortsOnAlreadyDeletedToken() {
        givenValidTxnCtx(true);
        // and:
        given(token.isDeleted()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(TOKEN_WAS_DELETED);
    }

    @Test
    void abortsOnPausedToken() {
        givenValidTxnCtx(true);
        given(token.isPaused()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(TOKEN_IS_PAUSED);
    }

    @Test
    void doesntReplaceIdenticalTreasury() {
        givenValidTxnCtx(true, true);
        givenToken(true, true);
        given(store.update(any(), anyLong())).willReturn(OK);

        subject.doStateTransition();

        verify(ledger, never()).getTokenBalance(oldTreasury, target);
        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void followsHappyPathWithNewTreasury() {
        // setup:
        final long oldTreasuryBalance = 1000;
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
        given(store.update(any(), anyLong())).willReturn(OK);
        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
        given(ledger.doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance))
                .willReturn(OK);

        subject.doStateTransition();

        verify(txnCtx).setStatus(SUCCESS);
        verify(ledger).decrementNumTreasuryTitles(oldTreasury);
        verify(ledger).incrementNumTreasuryTitles(newTreasury);
        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    }

    @Test
    void followsHappyPathWithNewTreasuryAndZeroBalanceOldTreasury() {
        final long oldTreasuryBalance = 0;
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
        given(store.update(any(), anyLong())).willReturn(OK);
        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);

        subject.doStateTransition();

        verify(ledger).unfreeze(newTreasury, target);
        verify(ledger).grantKyc(newTreasury, target);
        verify(ledger).getTokenBalance(oldTreasury, target);
        verify(ledger, never())
                .doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    }

    @Test
    void followsHappyPathNftWithNewTreasury() {
        final long oldTreasuryBalance = 1;
        givenValidTxnCtx(true);
        givenToken(true, true);
        given(token.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
        given(store.update(any(), anyLong())).willReturn(OK);
        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
        given(store.changeOwnerWildCard(nftId, oldTreasury, newTreasury)).willReturn(OK);

        subject.doStateTransition();

        verify(ledger).unfreeze(newTreasury, target);
        verify(ledger).grantKyc(newTreasury, target);
        verify(ledger).getTokenBalance(oldTreasury, target);
        verify(store).changeOwnerWildCard(nftId, oldTreasury, newTreasury);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void doesntGrantKycOrUnfreezeNewTreasuryIfNoKeyIsPresent() {
        givenValidTxnCtx(true);
        givenToken(false, false);
        given(store.update(any(), anyLong())).willReturn(OK);
        given(ledger.doTokenTransfer(eq(target), eq(oldTreasury), eq(newTreasury), anyLong()))
                .willReturn(OK);

        subject.doStateTransition();

        verify(ledger, never()).unfreeze(newTreasury, target);
        verify(ledger, never()).grantKyc(newTreasury, target);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(tokenUpdateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsExcessiveMemo() {
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsTooLongSymbol() {
        givenValidTxnCtx();
        given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

        assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidSymbol() {
        givenValidTxnCtx();
        given(validator.tokenSymbolCheck(any())).willReturn(INVALID_TOKEN_SYMBOL);

        assertEquals(INVALID_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsTooLongName() {
        givenValidTxnCtx();
        given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

        assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidAdminKey() {
        givenInvalidAdminKey();

        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidKycKey() {
        givenInvalidKycKey();

        assertEquals(INVALID_KYC_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidWipeKey() {
        givenInvalidWipeKey();

        assertEquals(INVALID_WIPE_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidSupplyKey() {
        givenInvalidSupplyKey();

        assertEquals(INVALID_SUPPLY_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidMemo() {
        givenValidTxnCtx();
        given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);

        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsInvalidFreezeKey() {
        givenInvalidFreezeKey();

        assertEquals(INVALID_FREEZE_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
    }

    @Test
    void rejectsTreasuryUpdateIfNonzeroBalanceForUnique() {
        final long oldTreasuryBalance = 1;
        subject =
                new TokenUpdateTransitionLogic(
                        false,
                        validator,
                        store,
                        ledger,
                        txnCtx,
                        sigImpactHistorian,
                        expiryOnlyCheck);

        givenValidTxnCtx(true);
        givenToken(true, true, true);
        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
        given(store.update(any(), anyLong())).willReturn(OK);
        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);

        subject.doStateTransition();

        verify(txnCtx).setStatus(CURRENT_TREASURY_STILL_OWNS_NFTS);
    }

    private void givenValidTxnCtx() {
        givenValidTxnCtx(false);
    }

    private void givenToken(boolean hasKyc, boolean hasFreeze) {
        givenToken(hasKyc, hasFreeze, false);
    }

    private void givenToken(boolean hasKyc, boolean hasFreeze, boolean isUnique) {
        given(token.hasKycKey()).willReturn(hasKyc);
        given(token.hasFreezeKey()).willReturn(hasFreeze);
        if (isUnique) {
            given(token.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        } else {
            given(token.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        }
    }

    private void givenValidTxnCtx(boolean withNewTreasury) {
        givenValidTxnCtx(withNewTreasury, false);
    }

    private void givenValidTxnCtx(boolean withNewTreasury, boolean useDuplicateTreasury) {
        final var builder =
                TransactionBody.newBuilder()
                        .setTokenUpdate(
                                TokenUpdateTransactionBody.newBuilder()
                                        .setSymbol(symbol)
                                        .setAutoRenewAccount(newAutoRenew)
                                        .setName(name)
                                        .setMemo(
                                                StringValue.newBuilder()
                                                        .setValue("FATALITY")
                                                        .build())
                                        .setToken(target));
        if (withNewTreasury) {
            builder.getTokenUpdateBuilder()
                    .setTreasury(useDuplicateTreasury ? oldTreasury : newTreasury);
        }
        tokenUpdateTxn = builder.build();
        given(accessor.getTxn()).willReturn(tokenUpdateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(now);
        if (useDuplicateTreasury) {
            given(ledger.usabilityOf(oldTreasury)).willReturn(OK);
        } else {
            given(ledger.usabilityOf(newTreasury)).willReturn(OK);
        }
        given(ledger.usabilityOf(newAutoRenew)).willReturn(OK);
        given(ledger.isDeleted(oldTreasury)).willReturn(false);
    }

    private void givenMissingToken() {
        tokenUpdateTxn =
                TransactionBody.newBuilder()
                        .setTokenUpdate(TokenUpdateTransactionBody.newBuilder())
                        .build();
    }

    private void givenInvalidFreezeKey() {
        tokenUpdateTxn =
                TransactionBody.newBuilder()
                        .setTokenUpdate(
                                TokenUpdateTransactionBody.newBuilder()
                                        .setToken(target)
                                        .setFreezeKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidAdminKey() {
        tokenUpdateTxn =
                TransactionBody.newBuilder()
                        .setTokenUpdate(
                                TokenUpdateTransactionBody.newBuilder()
                                        .setToken(target)
                                        .setAdminKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidWipeKey() {
        tokenUpdateTxn =
                TransactionBody.newBuilder()
                        .setTokenUpdate(
                                TokenUpdateTransactionBody.newBuilder()
                                        .setToken(target)
                                        .setWipeKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidSupplyKey() {
        tokenUpdateTxn =
                TransactionBody.newBuilder()
                        .setTokenUpdate(
                                TokenUpdateTransactionBody.newBuilder()
                                        .setToken(target)
                                        .setSupplyKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidKycKey() {
        tokenUpdateTxn =
                TransactionBody.newBuilder()
                        .setTokenUpdate(
                                TokenUpdateTransactionBody.newBuilder()
                                        .setToken(target)
                                        .setKycKey(Key.getDefaultInstance()))
                        .build();
    }

    private void withAlwaysValidValidator() {
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.memoCheck(any())).willReturn(OK);
    }
}
