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

import static com.hedera.services.txns.token.CreateLogic.MODEL_FACTORY;
import static com.hedera.services.txns.token.CreateLogic.RELS_LISTING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.process.Creation;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCreateTransitionLogicTest {
    private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private final long thisSecond = 1_234_567L;
    private final Instant now = Instant.ofEpochSecond(thisSecond);
    private final int decimals = 2;
    private final long initialSupply = 1_000_000L;
    private final Id createdId = new Id(0, 0, 777);
    private final AccountID payer = IdUtils.asAccount("1.2.3");
    private final AccountID treasury = IdUtils.asAccount("1.2.4");
    private final AccountID renewAccount = IdUtils.asAccount("1.2.5");

    private TransactionBody tokenCreateTxn;

    @Mock private Creation creation;
    @Mock private AccountStore accountStore;

    @Mock private UsageLimits usageLimits;
    @Mock private EntityIdSource ids;
    @Mock private TypedTokenStore tokenStore;
    @Mock private OptionValidator validator;
    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private Creation.CreationFactory creationFactory;
    @Mock private SigImpactHistorian sigImpactHistorian;

    private CreateLogic createLogic;
    private CreateChecks createChecks;
    private TokenCreateTransitionLogic subject;

    @BeforeEach
    void setup() {
        createLogic =
                new CreateLogic(
                        usageLimits,
                        accountStore,
                        tokenStore,
                        dynamicProperties,
                        sigImpactHistorian,
                        ids,
                        validator);
        createChecks = new CreateChecks(dynamicProperties, validator);
        subject = new TokenCreateTransitionLogic(txnCtx, createLogic, createChecks);
    }

    @Test
    void stateTransitionWorks() {
        final List<FcTokenAssociation> mockAssociations = List.of(new FcTokenAssociation(1L, 2L));
        givenValidTxnCtx();

        createLogic.setCreationFactory(creationFactory);

        given(accessor.getTxn()).willReturn(tokenCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(now);
        given(
                        creationFactory.processFrom(
                                accountStore,
                                tokenStore,
                                dynamicProperties,
                                tokenCreateTxn.getTokenCreation()))
                .willReturn(creation);
        given(creation.newTokenId()).willReturn(createdId);

        subject.doStateTransition();

        verify(usageLimits).assertCreatableTokens(1);
        verify(creation).loadModelsWith(payer, ids, validator);
        verify(creation).doProvisionallyWith(now.getEpochSecond(), MODEL_FACTORY, RELS_LISTING);
        verify(creation).persist();
        verify(sigImpactHistorian).markEntityChanged(createdId.num());
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenCreateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();
        withHappyValidator();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void uniqueNotSupportedIfNftsNotEnabled() {
        givenValidTxnCtx(false, false, true, true);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void uniqueSupportedIfNftsEnabled() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        givenValidTxnCtx(false, false, true, true);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void acceptsMissingAutoRenewAcount() {
        givenValidMissingRenewAccount();
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(txnCtx.consensusTime()).willReturn(now);

        // expect
        assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidSymbol() {
        givenValidTxnCtx();
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(MISSING_TOKEN_SYMBOL);

        // expect:
        assertEquals(MISSING_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsMissingName() {
        givenValidTxnCtx();
        withHappyValidatorExceptAutoRenew();
        given(validator.tokenNameCheck(any())).willReturn(MISSING_TOKEN_NAME);

        // expect:
        assertEquals(MISSING_TOKEN_NAME, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsTooLongName() {
        givenValidTxnCtx();
        withHappyValidatorExceptAutoRenew();
        given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

        // expect:
        assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidInitialSupply() {
        givenInvalidInitialSupply();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidDecimals() {
        givenInvalidDecimals();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_TOKEN_DECIMALS, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsMissingTreasury() {
        givenMissingTreasury();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(
                INVALID_TREASURY_ACCOUNT_FOR_TOKEN, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidFeeSchedule() {
        givenInvalidFeeScheduleKey();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(
                INVALID_CUSTOM_FEE_SCHEDULE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidAdminKey() {
        givenInvalidAdminKey();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidKycKey() {
        givenInvalidKycKey();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_KYC_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidWipeKey() {
        givenInvalidWipeKey();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_WIPE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidSupplyKey() {
        givenInvalidSupplyKey();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_SUPPLY_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectMissingFreezeKeyWithFreezeDefault() {
        givenMissingFreezeKeyWithFreezeDefault();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidFreezeKey() {
        givenInvalidFreezeKey();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_FREEZE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidAdminKeyBytes() {
        givenInvalidAdminKeyBytes();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidMemo() {
        givenValidTxnCtx();
        given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);

        // expect:
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidAutoRenewPeriod() {
        givenValidTxnCtx();
        withHappyValidatorExceptAutoRenew();

        // expect:
        assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsExpiryInPastInPrecheck() {
        givenInvalidExpirationTime();
        withHappyValidatorExceptAutoRenew();
        given(txnCtx.consensusTime()).willReturn(now);

        assertEquals(INVALID_EXPIRATION_TIME, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidSupplyChecks() {
        givenInvalidSupplyTypeAndSupply();
        withHappyValidatorExceptAutoRenew();

        assertEquals(INVALID_TOKEN_MAX_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsInvalidInitialAndMaxSupply() {
        givenTxWithInvalidSupplies();
        withHappyValidatorExceptAutoRenew();

        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    @Test
    void rejectsMissingSupplyKeyOnNftCreate() {
        givenTxnCtxWithoutSupplyKeyForNft();
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);

        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, subject.semanticCheck().apply(tokenCreateTxn));
    }

    private void givenInvalidSupplyTypeAndSupply() {
        var builder =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setSupplyType(TokenSupplyType.INFINITE)
                                        .setInitialSupply(0)
                                        .setMaxSupply(1)
                                        .build());

        tokenCreateTxn = builder.build();
    }

    private void givenTxWithInvalidSupplies() {
        var builder =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setSupplyType(TokenSupplyType.FINITE)
                                        .setInitialSupply(1000)
                                        .setMaxSupply(1)
                                        .build());
        tokenCreateTxn = builder.build();
    }

    private void givenValidTxnCtx() {
        givenValidTxnCtx(false, false, false, true);
    }

    private void givenTxnCtxWithoutSupplyKeyForNft() {
        givenValidTxnCtx(false, false, true, false);
    }

    private void givenValidTxnCtx(
            boolean withKyc, boolean withFreeze, boolean isUnique, boolean withSupplyKey) {
        final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
        final var memo = "...descending into thin air, where no arms / outstretch to catch her";
        var builder =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setAdminKey(key)
                                        .setAutoRenewAccount(renewAccount)
                                        .setExpiry(expiry));
        if (isUnique) {
            builder.getTokenCreationBuilder().setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
            builder.getTokenCreationBuilder().setInitialSupply(0L);
            builder.getTokenCreationBuilder().setDecimals(0);
        }
        if (withFreeze) {
            builder.getTokenCreationBuilder()
                    .setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey());
        }
        if (withKyc) {
            builder.getTokenCreationBuilder().setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey());
        }
        if (withSupplyKey) {
            builder.getTokenCreationBuilder()
                    .setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey());
        }
        tokenCreateTxn = builder.build();
    }

    private void givenInvalidInitialSupply() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder().setInitialSupply(-1))
                        .build();
    }

    private void givenInvalidDecimals() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(0)
                                        .setDecimals(-1))
                        .build();
    }

    private void givenMissingTreasury() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(TokenCreateTransactionBody.newBuilder())
                        .build();
    }

    private void givenMissingFreezeKeyWithFreezeDefault() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setFreezeDefault(true))
                        .build();
    }

    private void givenInvalidFreezeKey() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setFreezeKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidAdminKey() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setAdminKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidFeeScheduleKey() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setFeeScheduleKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidAdminKeyBytes() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setAdminKey(
                                                Key.newBuilder()
                                                        .setEd25519(
                                                                ByteString.copyFrom(
                                                                        "1".getBytes()))))
                        .build();
    }

    private void givenInvalidKycKey() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setKycKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidWipeKey() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setWipeKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidSupplyKey() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setSupplyKey(Key.getDefaultInstance()))
                        .build();
    }

    private void givenInvalidExpirationTime() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setExpiry(Timestamp.newBuilder().setSeconds(-1)))
                        .build();
    }

    private void givenValidMissingRenewAccount() {
        tokenCreateTxn =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setInitialSupply(initialSupply)
                                        .setDecimals(decimals)
                                        .setTreasury(treasury)
                                        .setAdminKey(key)
                                        .setExpiry(
                                                Timestamp.newBuilder()
                                                        .setSeconds(
                                                                thisSecond
                                                                        + Instant.now()
                                                                                .getEpochSecond())))
                        .build();
    }

    private void withHappyValidator() {
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
    }

    private void withHappyValidatorExceptAutoRenew() {
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
    }
}
