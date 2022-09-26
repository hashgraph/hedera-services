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
package com.hedera.services.txns.crypto;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.DECLINE_REWARD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.EXPIRY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.STAKED_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AccountCustomizer;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CryptoUpdateTransitionLogicTest {
    private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L);
    private static final long CUR_EXPIRY = CONSENSUS_TIME.getEpochSecond() + 2L;
    private static final long NEW_EXPIRY = CONSENSUS_TIME.getEpochSecond() + 7776000L;
    private static final int CUR_MAX_AUTOMATIC_ASSOCIATIONS = 10;
    private static final int NEW_MAX_AUTOMATIC_ASSOCIATIONS = 15;

    private static final Key KEY = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private static final long AUTO_RENEW_PERIOD = 100_001L;
    private static final AccountID PROXY = AccountID.newBuilder().setAccountNum(4_321L).build();
    private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
    private static final AccountID TARGET = AccountID.newBuilder().setAccountNum(9_999L).build();
    private static final String MEMO = "Not since life began";

    private boolean useLegacyFields;
    private HederaLedger ledger;
    private OptionValidator validator;
    private TransactionBody cryptoUpdateTxn;
    private SigImpactHistorian sigImpactHistorian;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private GlobalDynamicProperties dynamicProperties;
    private CryptoUpdateTransitionLogic subject;
    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private NodeInfo nodeInfo;

    @BeforeEach
    void setup() {
        useLegacyFields = false;

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        ledger = mock(HederaLedger.class);
        accessor = mock(SignedTxnAccessor.class);
        validator = mock(OptionValidator.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        dynamicProperties = mock(GlobalDynamicProperties.class);
        accounts = mock(MerkleMap.class);
        nodeInfo = mock(NodeInfo.class);
        withRubberstampingValidator();

        subject =
                new CryptoUpdateTransitionLogic(
                        ledger,
                        validator,
                        sigImpactHistorian,
                        txnCtx,
                        dynamicProperties,
                        () -> accounts,
                        nodeInfo);
    }

    @Test
    void ignoresProxyIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(AccountCustomizer.Option.PROXY));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(TARGET.getAccountNum());
        final var changes = captor.getValue().getChanges();
        assertEquals(0, changes.size());
    }

    @Test
    void updatesStakedIdIfPresentAndEnabled() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(STAKED_ID));
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(TARGET.getAccountNum());
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(-11L, changes.get(AccountProperty.STAKED_ID));
        assertNull(changes.get(AccountProperty.DECLINE_REWARD));
    }

    @Test
    void validatesStakedId() {
        final var op = CryptoUpdateTransactionBody.newBuilder();
        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(op)
                        .build();
        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(10).build())
                                        .build())
                        .build();
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);

        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void rejectsStakedIdIfStakingDisabled() {
        final var op = CryptoUpdateTransactionBody.newBuilder();
        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(op)
                        .build();
        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(10).build())
                                        .build())
                        .build();

        assertEquals(STAKING_NOT_ENABLED, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void rejectsDeclineRewardIfStakingDisabled() {
        final var op = CryptoUpdateTransactionBody.newBuilder();
        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(op)
                        .build();
        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setDeclineReward(BoolValue.newBuilder().setValue(false))
                                        .build())
                        .build();

        assertEquals(STAKING_NOT_ENABLED, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void agreesUpdatingToSentinelValues() {
        final var op = CryptoUpdateTransactionBody.newBuilder();
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(op)
                        .build();
        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(0).build())
                                        .build())
                        .build();

        assertEquals(OK, subject.semanticCheck().apply(cryptoUpdateTxn));

        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setStakedAccountId(
                                                AccountID.newBuilder().setAccountNum(-2).build())
                                        .build())
                        .build();

        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);
        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(cryptoUpdateTxn));

        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setStakedNodeId(-1)
                                        .build())
                        .build();
        assertEquals(OK, subject.semanticCheck().apply(cryptoUpdateTxn));

        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setStakedNodeId(-2)
                                        .build())
                        .build();
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);
        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void updatesDeclineRewardIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(DECLINE_REWARD));
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(TARGET.getAccountNum());
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(true, changes.get(AccountProperty.DECLINE_REWARD));
    }

    @Test
    void updatesReceiverSigReqIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(true, changes.get(AccountProperty.IS_RECEIVER_SIG_REQUIRED));
    }

    @Test
    void updatesReceiverSigReqIfTrueInLegacy() {
        useLegacyFields = true;
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(true, changes.get(AccountProperty.IS_RECEIVER_SIG_REQUIRED));
    }

    @Test
    void updatesExpiryIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(EXPIRY));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(NEW_EXPIRY, (long) changes.get(AccountProperty.EXPIRY));
    }

    @Test
    void updatesMaxAutomaticAssociationsIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(MAX_AUTOMATIC_ASSOCIATIONS));
        given(ledger.alreadyUsedAutomaticAssociations(any()))
                .willReturn(CUR_MAX_AUTOMATIC_ASSOCIATIONS);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
        given(dynamicProperties.maxTokensPerAccount())
                .willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS + 1);

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(SUCCESS);
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(
                NEW_MAX_AUTOMATIC_ASSOCIATIONS,
                (int) changes.get(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void updateMaxAutomaticAssociationsFailAsExpectedWithMaxLessThanAlreadyExisting() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(MAX_AUTOMATIC_ASSOCIATIONS));
        given(ledger.alreadyUsedAutomaticAssociations(any()))
                .willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS + 1);

        subject.doStateTransition();

        verify(ledger, never()).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
    }

    @Test
    void usingProxyAccountFails() {
        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(
                                CryptoUpdateTransactionBody.newBuilder()
                                        .setMemo(StringValue.of(MEMO))
                                        .setProxyAccountID(PROXY)
                                        .setReceiverSigRequired(true)
                                        .setKey(KEY))
                        .build();

        assertEquals(
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED,
                subject.semanticCheck().apply(cryptoUpdateTxn));

        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(
                                CryptoUpdateTransactionBody.newBuilder()
                                        .setMemo(StringValue.of(MEMO))
                                        .setProxyAccountID(AccountID.getDefaultInstance())
                                        .setReceiverSigRequired(true)
                                        .setKey(KEY))
                        .build();

        assertNotEquals(
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED,
                subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void updateMaxAutomaticAssociationsFailAsExpectedWithMaxMoreThanAllowedTokenAssociations() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(MAX_AUTOMATIC_ASSOCIATIONS));
        given(ledger.alreadyUsedAutomaticAssociations(any()))
                .willReturn(CUR_MAX_AUTOMATIC_ASSOCIATIONS);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
        given(dynamicProperties.maxTokensPerAccount())
                .willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS - 1);

        subject.doStateTransition();

        verify(ledger, never()).customize(argThat(TARGET::equals), captor.capture());
        verify(txnCtx).setStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
    }

    @Test
    void updatesMemoIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(AccountCustomizer.Option.MEMO));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
    }

    @Test
    void updatesAutoRenewIfPresent() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(AccountCustomizer.Option.AUTO_RENEW_PERIOD));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(AUTO_RENEW_PERIOD, changes.get(AccountProperty.AUTO_RENEW_PERIOD));
    }

    @Test
    void updatesKeyIfPresent() throws Throwable {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenTxnCtx(EnumSet.of(AccountCustomizer.Option.KEY));

        subject.doStateTransition();

        verify(ledger).customize(argThat(TARGET::equals), captor.capture());
        final var changes = captor.getValue().getChanges();
        assertEquals(1, changes.size());
        assertEquals(KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
    }

    @Test
    void hasCorrectApplicability() {
        givenTxnCtx();

        assertTrue(subject.applicability().test(cryptoUpdateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void rejectsKeyWithBadEncoding() {
        rejectsKey(unmappableKey(), BAD_ENCODING);
    }

    @Test
    void rejectsInvalidKey() {
        rejectsKey(emptyKey(), INVALID_ADMIN_KEY);
    }

    @Test
    void rejectsInvalidMemo() {
        givenTxnCtx(EnumSet.of(AccountCustomizer.Option.MEMO));
        given(validator.memoCheck(MEMO)).willReturn(MEMO_TOO_LONG);

        assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void rejectsInvalidAutoRenewPeriod() {
        givenTxnCtx();
        given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

        assertEquals(
                AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void acceptsValidTxn() {
        givenTxnCtx();
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        assertEquals(OK, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    @Test
    void rejectsDetachedAccount() {
        givenTxnCtx();
        given(ledger.isDetached(TARGET)).willReturn(true);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void rejectsInvalidExpiry() {
        givenTxnCtx();
        given(validator.isValidExpiry(any())).willReturn(false);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
    }

    @Test
    void permitsDetachedIfOnlyExtendingExpiry() {
        givenTxnCtx(EnumSet.of(EXPIRY));
        given(ledger.isDetached(TARGET)).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void rejectsInvalidExpiryForDetached() {
        givenTxnCtx(EnumSet.of(EXPIRY), EnumSet.of(EXPIRY));
        given(ledger.isDetached(TARGET)).willReturn(true);
        given(ledger.expiry(TARGET)).willReturn(CUR_EXPIRY);

        subject.doStateTransition();

        verify(txnCtx).setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
    }

    @Test
    void rejectsSmartContract() {
        givenTxnCtx();
        given(ledger.isSmartContract(TARGET)).willReturn(true);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
    }

    @Test
    void preemptsMissingAccountException() {
        givenTxnCtx();
        given(ledger.exists(TARGET)).willReturn(false);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
    }

    @Test
    void translatesMissingAccountException() {
        givenTxnCtx();
        willThrow(MissingEntityException.class).given(ledger).customize(any(), any());
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
    }

    @Test
    void translatesAccountIsDeletedException() {
        givenTxnCtx();
        willThrow(DeletedAccountException.class).given(ledger).customize(any(), any());
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(txnCtx).setStatus(ACCOUNT_DELETED);
    }

    @Test
    void translatesUnknownException() {
        givenTxnCtx();
        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder()
                                        .setKey(unmappableKey()))
                        .build();
        given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
        given(txnCtx.accessor()).willReturn(accessor);

        subject.doStateTransition();

        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    private Key unmappableKey() {
        return Key.getDefaultInstance();
    }

    private Key emptyKey() {
        return Key.newBuilder()
                .setThresholdKey(
                        ThresholdKey.newBuilder()
                                .setKeys(KeyList.getDefaultInstance())
                                .setThreshold(0))
                .build();
    }

    private void rejectsKey(final Key key, ResponseCodeEnum err) {
        givenTxnCtx();
        cryptoUpdateTxn =
                cryptoUpdateTxn.toBuilder()
                        .setCryptoUpdateAccount(
                                cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(key))
                        .build();

        assertEquals(err, subject.semanticCheck().apply(cryptoUpdateTxn));
    }

    private void givenTxnCtx() {
        givenTxnCtx(
                EnumSet.of(
                        AccountCustomizer.Option.KEY,
                        AccountCustomizer.Option.MEMO,
                        STAKED_ID,
                        DECLINE_REWARD,
                        EXPIRY,
                        IS_RECEIVER_SIG_REQUIRED,
                        AccountCustomizer.Option.AUTO_RENEW_PERIOD),
                EnumSet.noneOf(AccountCustomizer.Option.class));
    }

    private void givenTxnCtx(final EnumSet<AccountCustomizer.Option> updating) {
        givenTxnCtx(updating, EnumSet.noneOf(AccountCustomizer.Option.class));
    }

    private void givenTxnCtx(
            final EnumSet<AccountCustomizer.Option> updating,
            final EnumSet<AccountCustomizer.Option> misconfiguring) {
        final var op = CryptoUpdateTransactionBody.newBuilder();
        if (updating.contains(AccountCustomizer.Option.MEMO)) {
            op.setMemo(StringValue.newBuilder().setValue(MEMO).build());
        }
        if (updating.contains(AccountCustomizer.Option.KEY)) {
            op.setKey(KEY);
        }
        if (updating.contains(AccountCustomizer.Option.PROXY)) {
            op.setProxyAccountID(PROXY);
        }
        if (updating.contains(EXPIRY)) {
            if (misconfiguring.contains(EXPIRY)) {
                op.setExpirationTime(Timestamp.newBuilder().setSeconds(CUR_EXPIRY - 1));
            } else {
                op.setExpirationTime(Timestamp.newBuilder().setSeconds(NEW_EXPIRY));
            }
        }
        if (updating.contains(IS_RECEIVER_SIG_REQUIRED)) {
            if (!useLegacyFields) {
                op.setReceiverSigRequiredWrapper(BoolValue.newBuilder().setValue(true));
            } else {
                op.setReceiverSigRequired(true);
            }
        }
        if (updating.contains(AccountCustomizer.Option.AUTO_RENEW_PERIOD)) {
            op.setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD));
        }
        if (updating.contains(MAX_AUTOMATIC_ASSOCIATIONS)) {
            op.setMaxAutomaticTokenAssociations(Int32Value.of(NEW_MAX_AUTOMATIC_ASSOCIATIONS));
        }
        if (updating.contains(STAKED_ID)) {
            op.setStakedNodeId(10L);
        }
        if (updating.contains(DECLINE_REWARD)) {
            op.setDeclineReward(BoolValue.of(true));
        }
        op.setAccountIDToUpdate(TARGET);
        cryptoUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoUpdateAccount(op)
                        .build();
        given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(ledger.exists(TARGET)).willReturn(true);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(PAYER)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
                .build();
    }

    private void withRubberstampingValidator() {
        given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
        given(validator.isValidExpiry(any())).willReturn(true);
        given(validator.hasGoodEncoding(any())).willReturn(true);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
    }
}
