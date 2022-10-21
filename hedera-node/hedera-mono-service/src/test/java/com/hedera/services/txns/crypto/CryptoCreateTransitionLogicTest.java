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

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.legacy.core.jproto.JEd25519Key.ED25519_BYTE_LENGTH;
import static com.hedera.services.txns.crypto.CryptoCreateTransitionLogic.MAX_CHARGEABLE_AUTO_ASSOCIATIONS;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.longThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CryptoCreateTransitionLogicTest {
    private static final Key KEY = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private static final long CUSTOM_AUTO_RENEW_PERIOD = 100_001L;
    private static final long CUSTOM_SEND_THRESHOLD = 49_000L;
    private static final long CUSTOM_RECEIVE_THRESHOLD = 51_001L;
    private static final Long BALANCE = 1_234L;
    private static final Long ZERO_BALANCE = 0L;
    private static final String MEMO = "The particular is pounded til it is man";
    private static final int MAX_AUTO_ASSOCIATIONS = 1234;
    private static final int MAX_TOKEN_ASSOCIATIONS = 2345;
    private static final AccountID PROXY = AccountID.newBuilder().setAccountNum(4_321L).build();
    private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
    private static final AccountID CREATED = AccountID.newBuilder().setAccountNum(9_999L).build();
    private static final AccountID STAKED_ACCOUNT_ID =
            AccountID.newBuilder().setAccountNum(1000L).build();
    private static final Key aPrimitiveEDKey =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                    .build();
    private static final Instant consensusTime = Instant.now();
    private static final long pretendMaxAccounts = 10;
    private static final byte[] ECDSA_KEY_BYTES =
            unhex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
    private static final byte[] EVM_ADDRESS_BYTES =
            unhex("627306090abaB3A6e1400e9345bC60c78a8BEf57");
    private static final byte[] ANOTHER_EVM_ADDRESS_BYTES =
            unhex("627306090abaB3A6e1400e9345bC60c78a8BEf58");
    private static final Key ECDSA_KEY =
            Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ECDSA_KEY_BYTES)).build();

    private HederaLedger ledger;
    private OptionValidator validator;
    private TransactionBody cryptoCreateTxn;
    private SigImpactHistorian sigImpactHistorian;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private GlobalDynamicProperties dynamicProperties;
    private CryptoCreateTransitionLogic subject;
    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    private NodeInfo nodeInfo;
    private UsageLimits usageLimits;
    private AliasManager aliasManager;

    @BeforeEach
    void setup() {
        txnCtx = mock(TransactionContext.class);
        usageLimits = mock(UsageLimits.class);
        aliasManager = mock(AliasManager.class);
        given(txnCtx.consensusTime()).willReturn(consensusTime);
        ledger = mock(HederaLedger.class);
        accessor = mock(SignedTxnAccessor.class);
        validator = mock(OptionValidator.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        dynamicProperties = mock(GlobalDynamicProperties.class);
        accounts = mock(MerkleMap.class);
        accountsLedger = mock(TransactionalLedger.class);
        nodeInfo = mock(NodeInfo.class);
        given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
        withRubberstampingValidator();

        subject =
                new CryptoCreateTransitionLogic(
                        usageLimits,
                        ledger,
                        validator,
                        sigImpactHistorian,
                        txnCtx,
                        dynamicProperties,
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        nodeInfo,
                        aliasManager);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(cryptoCreateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void returnsMemoTooLongWhenValidatorSays() {
        givenValidTxnCtx();
        given(validator.memoCheck(MEMO)).willReturn(MEMO_TOO_LONG);

        assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void returnsKeyRequiredOnEmptyKey() {
        givenValidTxnCtx(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());

        assertEquals(KEY_REQUIRED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void requiresKey() {
        givenMissingKey();

        assertEquals(KEY_REQUIRED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsInvalidKey() {
        givenEmptyKey();

        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsMissingAutoRenewPeriod() {
        givenMissingAutoRenewPeriod();

        assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsNegativeBalance() {
        givenAbsurdInitialBalance();

        assertEquals(INVALID_INITIAL_BALANCE, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsAliasSizeUnderEVMAddressSize() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder().setAlias(ByteString.copyFromUtf8("0"));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsKeyAndAliasSizeUnderEVMAddressSize() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(aPrimitiveEDKey)
                        .setAlias(ByteString.copyFromUtf8("0"));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsWhenEDKeyAndAliasDoesNotMatch() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(aPrimitiveEDKey)
                        .setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(aliasManager.lookupIdBy(ECDSA_KEY.toByteString())).willReturn(MISSING_NUM);

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsWhenECKeyAndAliasDoesNotMatch() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(ECDSA_KEY)
                        .setAlias(aPrimitiveEDKey.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();

        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(aPrimitiveEDKey.toByteString())).willReturn(MISSING_NUM);

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsWhenAliasIsEVMAddressAndDoesNotMatchToTheKey() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(ECDSA_KEY)
                        .setAlias(ByteString.copyFrom(ANOTHER_EVM_ADDRESS_BYTES));
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(ANOTHER_EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsWhenEmptyKeyAndEVMAddressAlias() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(asKeyUnchecked(new JEd25519Key(new byte[ED25519_BYTE_LENGTH - 1])))
                        .setAlias(ByteString.copyFrom(ANOTHER_EVM_ADDRESS_BYTES));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();

        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsWhenEDKeyAndAliasEVMAddress() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(aPrimitiveEDKey)
                        .setAlias(ByteString.copyFrom(EVM_ADDRESS_BYTES));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);

        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsWhenEVMAddressAsAliasIsNotUnique() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setAlias(ByteString.copyFrom(EVM_ADDRESS_BYTES));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.fromAccountId(PROXY));

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsEVMAddressAsAliasWhenCreateWithAliasIsDisabled() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setAlias(ByteString.copyFrom(EVM_ADDRESS_BYTES));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(false);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);

        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsEVMAddressAsAliasWhenLazyCreateIsDisabled() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setAlias(ByteString.copyFrom(EVM_ADDRESS_BYTES));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(false);

        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsECKeyAsAliasWhenCreateWithAliasIsDisabled() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder().setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(false);

        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsECKeyAsAliasWhenExtractedEVMAddressIsNotUnique() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder().setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(aliasManager.lookupIdBy(ECDSA_KEY.toByteString())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(EntityNum.fromAccountId(PROXY));

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsECKeyAsAliasWhenKeyIsNotUnique() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder().setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(aliasManager.lookupIdBy(ECDSA_KEY.toByteString()))
                .willReturn(EntityNum.fromAccountId(PROXY));

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsECKeyAndECKeyAsAliasWhenEcKeyIsnNotUnique() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(ECDSA_KEY)
                        .setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1()))
                .willReturn(EntityNum.fromAccountId(PROXY));
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsECKeyAndECKeyAsAliasWhenExtractedEVMAddressIsNotUnique() {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(ECDSA_KEY)
                        .setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(EntityNum.fromAccountId(PROXY));

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsECKeyWhenExtractedEVMAddressIsNotUnique() {
        final var opBuilder = CryptoCreateTransactionBody.newBuilder().setKey(ECDSA_KEY);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(EntityNum.fromAccountId(PROXY));

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void cantExtractEVMAddressFromInvalidECDSAKey() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        final var invalidEcdsaBytes =
                unhex("03af80b11d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
        final var invalidEcdsaKey =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(invalidEcdsaBytes)).build();
        final var opBuilder = CryptoCreateTransactionBody.newBuilder().setKey(invalidEcdsaKey);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());

        final var changes = captor.getValue().getChanges();
        assertNull(changes.get(AccountProperty.ALIAS));
    }

    @Test
    void rejectsECKeyWhenECKeyIsNotUnique() {
        final var opBuilder = CryptoCreateTransactionBody.newBuilder().setKey(ECDSA_KEY);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1()))
                .willReturn(EntityNum.fromAccountId(PROXY));

        assertEquals(INVALID_ALIAS_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsNegativeSendThreshold() {
        givenAbsurdSendThreshold();

        assertEquals(INVALID_SEND_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsNegativeReceiveThreshold() {
        givenAbsurdReceiveThreshold();

        assertEquals(
                INVALID_RECEIVE_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsKeyWithBadEncoding() {
        givenValidTxnCtx();
        given(validator.hasGoodEncoding(any())).willReturn(false);

        assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsInvalidAutoRenewPeriod() {
        givenValidTxnCtx();
        given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

        assertEquals(
                AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);

        assertEquals(OK, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsInvalidMaxAutomaticAssociations() {
        givenInvalidMaxAutoAssociations(MAX_TOKEN_ASSOCIATIONS + 1);
        given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);

        assertEquals(
                REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT,
                subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsTooManyMaxAutomaticAssociations() {
        givenInvalidMaxAutoAssociations(MAX_CHARGEABLE_AUTO_ASSOCIATIONS + 1);

        assertEquals(
                REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT,
                subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void followsHappyPathWithOverrides() throws Throwable {
        final var expiry = consensusTime.getEpochSecond() + CUSTOM_AUTO_RENEW_PERIOD;
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenValidTxnCtx();
        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger).create(argThat(PAYER::equals), longThat(BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(8, changes.size());
        assertEquals(CUSTOM_AUTO_RENEW_PERIOD, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(expiry, (long) changes.get(EXPIRY));
        assertEquals(KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(true, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertNull(changes.get(AccountProperty.PROXY));
        assertEquals(EntityId.fromGrpcAccountId(STAKED_ACCOUNT_ID).num(), changes.get(STAKED_ID));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
        assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void followsHappyPathEVMAddressAsAlias() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(MEMO)
                        .setReceiverSigRequired(false)
                        .setDeclineReward(false)
                        .setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
                        .setAlias(ByteString.copyFrom(EVM_ADDRESS_BYTES));
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(any())).willReturn(MISSING_NUM);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(7, changes.size());
        assertEquals(0, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(txnCtx.consensusTime().getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(ByteString.copyFrom(EVM_ADDRESS_BYTES), changes.get(AccountProperty.ALIAS));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
        assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
        assertEquals(false, changes.get(DECLINE_REWARD));
    }

    @Test
    void followsHappyPathECKeyAsAlias() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(MEMO)
                        .setReceiverSigRequired(false)
                        .setDeclineReward(false)
                        .setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
                        .setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(ECDSA_KEY.toByteString())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(8, changes.size());
        assertEquals(ECDSA_KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(txnCtx.consensusTime().getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
        assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals(ECDSA_KEY.toByteString(), changes.get(AccountProperty.ALIAS));
    }

    @Test
    void followsHappyPathEDKeyAsAlias() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(MEMO)
                        .setReceiverSigRequired(false)
                        .setDeclineReward(false)
                        .setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
                        .setAlias(aPrimitiveEDKey.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(aPrimitiveEDKey.toByteString())).willReturn(MISSING_NUM);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(8, changes.size());
        assertEquals(aPrimitiveEDKey, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(txnCtx.consensusTime().getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
        assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals(aPrimitiveEDKey.toByteString(), changes.get(AccountProperty.ALIAS));
    }

    @Test
    void followsHappyPathECKeyAndECKeyAsAlias() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(MEMO)
                        .setReceiverSigRequired(false)
                        .setDeclineReward(false)
                        .setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
                        .setKey(ECDSA_KEY)
                        .setAlias(ECDSA_KEY.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(ECDSA_KEY.toByteString())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(8, changes.size());
        assertEquals(ECDSA_KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(txnCtx.consensusTime().getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
        assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals(ECDSA_KEY.toByteString(), changes.get(AccountProperty.ALIAS));
    }

    @Test
    void followsHappyPathEDKeyAndEDKeyAsAlias() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(MEMO)
                        .setReceiverSigRequired(false)
                        .setDeclineReward(false)
                        .setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
                        .setKey(aPrimitiveEDKey)
                        .setAlias(aPrimitiveEDKey.toByteString());
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(aPrimitiveEDKey.toByteString())).willReturn(MISSING_NUM);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(8, changes.size());
        assertEquals(aPrimitiveEDKey, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(txnCtx.consensusTime().getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(MEMO, changes.get(AccountProperty.MEMO));
        assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals(aPrimitiveEDKey.toByteString(), changes.get(AccountProperty.ALIAS));
    }

    @Test
    void followsHappyPathECKey() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder = CryptoCreateTransactionBody.newBuilder().setKey(ECDSA_KEY);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);
        given(ledger.getAccountsLedger()).willReturn(accountsLedger);
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(8, changes.size());
        assertEquals(ECDSA_KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0L, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(consensusTime.getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(ByteString.copyFrom(EVM_ADDRESS_BYTES), changes.get(AccountProperty.ALIAS));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals("", changes.get(AccountProperty.MEMO));
        assertEquals(0, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void followsHappyPathECKeyAndCreateWithAliasAndLazyCreateDisabled() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder = CryptoCreateTransactionBody.newBuilder().setKey(ECDSA_KEY);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(ECDSA_KEY.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFrom(EVM_ADDRESS_BYTES)))
                .willReturn(MISSING_NUM);
        given(ledger.getAccountsLedger()).willReturn(accountsLedger);
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(false);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(false);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(7, changes.size());
        assertEquals(ECDSA_KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0L, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(consensusTime.getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals("", changes.get(AccountProperty.MEMO));
        assertEquals(0, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void followsHappyPathEDKey() throws DecoderException {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

        final var opBuilder = CryptoCreateTransactionBody.newBuilder().setKey(aPrimitiveEDKey);
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        given(aliasManager.lookupIdBy(aPrimitiveEDKey.getECDSASecp256K1())).willReturn(MISSING_NUM);
        given(ledger.getAccountsLedger()).willReturn(accountsLedger);

        given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
        given(dynamicProperties.isCryptoCreateWithAliasEnabled()).willReturn(true);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger)
                .create(argThat(PAYER::equals), longThat(ZERO_BALANCE::equals), captor.capture());
        verify(txnCtx).setCreated(CREATED);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

        final var changes = captor.getValue().getChanges();
        assertEquals(7, changes.size());
        assertEquals(aPrimitiveEDKey, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
        assertEquals(0L, (long) changes.get(AUTO_RENEW_PERIOD));
        assertEquals(consensusTime.getEpochSecond(), (long) changes.get(EXPIRY));
        assertEquals(false, changes.get(IS_RECEIVER_SIG_REQUIRED));
        assertEquals(false, changes.get(DECLINE_REWARD));
        assertEquals("", changes.get(AccountProperty.MEMO));
        assertEquals(0, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void failsIfMaxAccountsReached() {
        givenValidTxnCtx();
        given(dynamicProperties.maxNumAccounts()).willReturn(pretendMaxAccounts);
        given(accounts.size()).willReturn((int) pretendMaxAccounts + 1);

        subject.doStateTransition();

        verify(txnCtx).setStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
    }

    @Test
    void translatesInsufficientPayerBalance() {
        givenValidTxnCtx();
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
        given(ledger.create(any(), anyLong(), any())).willThrow(InsufficientFundsException.class);

        subject.doStateTransition();

        verify(txnCtx).setStatus(INSUFFICIENT_PAYER_BALANCE);
    }

    @Test
    void translatesUnknownException() {
        givenValidTxnCtx();
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);
        cryptoCreateTxn =
                cryptoCreateTxn.toBuilder()
                        .setCryptoCreateAccount(
                                cryptoCreateTxn.getCryptoCreateAccount().toBuilder()
                                        .setKey(unmappableKey()))
                        .build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);

        subject.doStateTransition();

        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    void validatesStakedId() {
        givenValidTxnCtx();
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);

        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsStakedIdIfStakingDisabled() {
        givenValidTxnCtx();

        assertEquals(STAKING_NOT_ENABLED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void rejectsDeclineRewardIfStakingDisabled() {
        givenValidTxnCtx(KEY, false, true);

        assertEquals(STAKING_NOT_ENABLED, subject.semanticCheck().apply(cryptoCreateTxn));
    }

    @Test
    void usingProxyAccountFails() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setMemo(MEMO)
                                        .setInitialBalance(BALANCE)
                                        .setProxyAccountID(PROXY)
                                        .setReceiverSigRequired(true)
                                        .setAutoRenewPeriod(
                                                Duration.newBuilder()
                                                        .setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
                                        .setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
                                        .setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
                                        .setKey(KEY)
                                        .setMaxAutomaticTokenAssociations(
                                                MAX_TOKEN_ASSOCIATIONS + 1))
                        .build();

        assertEquals(
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED,
                subject.semanticCheck().apply(cryptoCreateTxn));

        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setMemo(MEMO)
                                        .setInitialBalance(BALANCE)
                                        .setProxyAccountID(AccountID.getDefaultInstance())
                                        .setReceiverSigRequired(true)
                                        .setAutoRenewPeriod(
                                                Duration.newBuilder()
                                                        .setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
                                        .setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
                                        .setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
                                        .setKey(KEY)
                                        .setMaxAutomaticTokenAssociations(
                                                MAX_TOKEN_ASSOCIATIONS + 1))
                        .build();
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
        assertNotEquals(
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED,
                subject.semanticCheck().apply(cryptoCreateTxn));
    }

    private Key unmappableKey() {
        return Key.getDefaultInstance();
    }

    private void givenMissingKey() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder().setInitialBalance(BALANCE))
                        .build();
    }

    private void givenEmptyKey() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setKey(
                                                asKeyUnchecked(
                                                        new JEd25519Key(
                                                                new byte[ED25519_BYTE_LENGTH - 1])))
                                        .setInitialBalance(BALANCE))
                        .build();
    }

    private void givenMissingAutoRenewPeriod() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setKey(KEY)
                                        .setInitialBalance(BALANCE))
                        .build();
    }

    private void givenAbsurdSendThreshold() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
                                        .setKey(KEY)
                                        .setSendRecordThreshold(-1L))
                        .build();
    }

    private void givenAbsurdReceiveThreshold() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
                                        .setKey(KEY)
                                        .setReceiveRecordThreshold(-1L))
                        .build();
    }

    private void givenAbsurdInitialBalance() {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
                                        .setKey(KEY)
                                        .setInitialBalance(-1L))
                        .build();
    }

    private void givenInvalidMaxAutoAssociations(final int n) {
        cryptoCreateTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setMemo(MEMO)
                                        .setInitialBalance(BALANCE)
                                        .setStakedAccountId(STAKED_ACCOUNT_ID)
                                        .setReceiverSigRequired(true)
                                        .setAutoRenewPeriod(
                                                Duration.newBuilder()
                                                        .setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
                                        .setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
                                        .setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
                                        .setKey(KEY)
                                        .setMaxAutomaticTokenAssociations(n))
                        .build();
    }

    private void givenValidTxnCtx() {
        givenValidTxnCtx(KEY);
    }

    private void givenValidTxnCtx(final Key toUse) {
        givenValidTxnCtx(toUse, true, false);
    }

    private void givenValidTxnCtx(
            final Key toUse, final boolean setStakingId, final boolean declineReward) {
        final var opBuilder =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(MEMO)
                        .setInitialBalance(BALANCE)
                        .setDeclineReward(declineReward)
                        .setReceiverSigRequired(true)
                        .setAutoRenewPeriod(
                                Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
                        .setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
                        .setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
                        .setKey(toUse)
                        .setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS);
        if (setStakingId) {
            opBuilder.setStakedAccountId(STAKED_ACCOUNT_ID);
        }
        cryptoCreateTxn = TransactionBody.newBuilder().setCryptoCreateAccount(opBuilder).build();
        given(accessor.getTxn()).willReturn(cryptoCreateTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
    }

    private AccountID ourAccount() {
        return PAYER;
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(PAYER)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }

    private void withRubberstampingValidator() {
        given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
        given(validator.hasGoodEncoding(any())).willReturn(true);
        given(validator.memoCheck(any())).willReturn(OK);
    }
}
