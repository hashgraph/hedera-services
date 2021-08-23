package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHbar;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

class TokenCreateTransitionLogicTest {
	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private final long thisSecond = 1_234_567L;
	private final Instant now = Instant.ofEpochSecond(thisSecond);
	private final int decimals = 2;
	private final long initialSupply = 1_000_000L;
	private final String memo = "...descending into thin air, where no arms / outstretch to catch her";
	private final AccountID payer = IdUtils.asAccount("1.2.3");
	private final AccountID treasury = IdUtils.asAccount("1.2.4");
	private final AccountID renewAccount = IdUtils.asAccount("1.2.5");
	private final TokenID created = IdUtils.asToken("1.2.666");
	private TransactionBody tokenCreateTxn;

	private Token newProvisionalToken;
	private Token denom;
	private final Id denomId = Id.fromGrpcToken(IdUtils.asToken("17.71.77"));
	private final Id modelTreasuryId = IdUtils.asModelId("1.2.3");
	private Account modelTreasury;
	private final Id autoRenewId = IdUtils.asModelId("12.13.12");
	private CopyOnWriteIds treasuryAssociatedTokenIds;

	private OptionValidator validator;
	private TokenStore store;
	private TypedTokenStore typedTokenStore;
	private AccountStore accountStore;
	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private GlobalDynamicProperties dynamicProperties;
	private EntityIdSource ids;

	private TokenCreateTransitionLogic subject;
	private MockedStatic<Token> staticTokenHandle;
	private final Id miscId = new Id(3, 2,1);
	private final TokenID misc = IdUtils.asToken("3.2.1");
	private final Id feeCollectorId = new Id(6, 6,6);
	private final AccountID feeCollector = IdUtils.asAccount("6.6.6");
	private final Account feeCollectorModel = mock(Account.class);
	private final Id hbarFeeCollectorId = new Id(7, 7,7);
	private final AccountID hbarFeeCollector = IdUtils.asAccount("7.7.7");
	private final Account hbarFeeCollectorModel = mock(Account.class);
	private final Id fixedFeeCollectorId = new Id(8,8,8);
	private final AccountID fixedFeeCollector = IdUtils.asAccount("8.8.8");
	private final Account fixedFeeCollectorModel = mock(Account.class);
	private final Id fractionalFeeCollectorId = new Id(9,9,9);
	private final AccountID fractionalFeeCollector = IdUtils.asAccount("9.9.9");
	private final Account fractionalFeeCollectorModel = mock(Account.class);
	private final Id nonAutoEnabledFeeCollectorId = new Id(1,2,777);
	private final AccountID nonAutoEnabledFeeCollector = IdUtils.asAccount("1.2.777");
	private final Account nonAutoEnabledFeeCollectorModel = mock(Account.class);
	private final CustomFeeBuilder builder = new CustomFeeBuilder(feeCollector);
	private final CustomFee customFixedFeeA = builder.withFixedFee(fixedHts(200L));
	private final CustomFee customFractionalFeeA = builder.withFractionalFee(
			fractional(15L, 100L)
					.setMinimumAmount(10L)
					.setMaximumAmount(50L));
	private final CustomFee customFixedFeeInHbar = new CustomFeeBuilder(hbarFeeCollector).withFixedFee(fixedHbar(100L));
	private final CustomFee customFixedFeeInHts = new CustomFeeBuilder(nonAutoEnabledFeeCollector).withFixedFee(
			fixedHts(misc, 100L));
	private final CustomFee customFixedFeeB = new CustomFeeBuilder(fixedFeeCollector).withFixedFee(fixedHts(300L));
	private final CustomFee customFractionalFeeB = new CustomFeeBuilder(fractionalFeeCollector).withFractionalFee(
			fractional(15L, 100L)
					.setMinimumAmount(5L)
					.setMaximumAmount(15L));
	private final List<CustomFee> grpcCustomFees = List.of(
			customFixedFeeInHbar,
			customFixedFeeInHts,
			customFixedFeeA,
			customFixedFeeB,
			customFractionalFeeA,
			customFractionalFeeB
	);

	@BeforeEach
	private void setup() {
		validator = mock(OptionValidator.class);
		store = mock(TokenStore.class);
		typedTokenStore = mock(TypedTokenStore.class);
		accountStore = mock(AccountStore.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);
		dynamicProperties = mock(GlobalDynamicProperties.class);
		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(any())).willReturn(created);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.consensusTime()).willReturn(Instant.now());
		withAlwaysValidValidator();

		newProvisionalToken = mock(Token.class);
		modelTreasury = mock(Account.class);
		treasuryAssociatedTokenIds = mock(CopyOnWriteIds.class);
		denom = mock(Token.class);

		staticTokenHandle = mockStatic(Token.class);
		subject = new TokenCreateTransitionLogic(
				validator, typedTokenStore, accountStore, txnCtx, dynamicProperties, ids);
	}

	@AfterEach
	void cleanup() {
		staticTokenHandle.close();
	}

	@Test
	void doesNotApplyChangesOnThrownException() {
		givenValidTxnCtx();
		// and:
		mockProvisionalToken();
		mockModelTreasury();

		doThrow(new InvalidTransactionException(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)).when(modelTreasury).associateWith(
				any(), anyInt(), anyBoolean());
		// when:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);

		// then:
		verify(typedTokenStore, never()).persistNew(any());
		verify(typedTokenStore, never()).persistTokenRelationships(anyList());
	}

	@Test
	void abortsIfInitialExpiryIsInvalid() {
		givenValidTxnCtx();
		mockModelTreasury();
		mockProvisionalToken();
		given(validator.isValidExpiry(any())).willReturn(false);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), INVALID_EXPIRATION_TIME);
		assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());

		// then:
		verify(typedTokenStore, never()).persistNew(any());
	}

	@Test
	void abortsIfAnyAssociationFails() {
		givenValidTxnCtx();
		mockModelTreasury();
		mockProvisionalToken();
		// and:
		given(treasuryAssociatedTokenIds.contains(any(Id.class))).willReturn(false);
		doThrow(new InvalidTransactionException(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED))
				.when(modelTreasury)
				.associateWith(anyList(), anyInt(), anyBoolean());

		// when & then:
		assertFailsWith(() -> subject.doStateTransition(), TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// and:
		verify(accountStore, never()).persistAccount(modelTreasury);
		verify(typedTokenStore, never()).persistTokenRelationships(anyList());
		verify(typedTokenStore, never()).persistNew(any());
	}

	@Test
	void skipsTokenBalanceAdjustmentForNft() {
		givenValidTxnCtx();
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(tokenCreateTxn.getTokenCreation().toBuilder().setTokenType(NON_FUNGIBLE_UNIQUE))
				.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		mockModelTreasury();
		mockProvisionalToken();

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(treasuryRelMock.getAccount()).willReturn(modelTreasury);
		given(treasuryRelMock.getToken()).willReturn(newProvisionalToken);

		subject.doStateTransition();

		verify(ledger, never()).unfreeze(any(), any());
		verify(ledger, never()).grantKyc(any(), any());

		verify(ledger, never()).adjustTokenBalance(any(AccountID.class), any(TokenID.class), anyLong());

		verify(typedTokenStore).persistNew(any());
		verify(typedTokenStore).persistTokenRelationships(anyList());
	}

	@Test
	void followsHappyPathForCustomFees() {
		givenValidTxnCtx(true, true, true, false);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(treasuryRelMock.getToken()).willReturn(newProvisionalToken);
		given(treasuryRelMock.getAccount()).willReturn(modelTreasury);
		final var hbarFeeCollectorRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(hbarFeeCollectorModel)).willReturn(hbarFeeCollectorRel);
		given(hbarFeeCollectorRel.getToken()).willReturn(newProvisionalToken);
		given(hbarFeeCollectorRel.getAccount()).willReturn(hbarFeeCollectorModel);
		final var feeCollectorRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(feeCollectorModel)).willReturn(feeCollectorRel);
		given(feeCollectorRel.getToken()).willReturn(newProvisionalToken);
		given(feeCollectorRel.getAccount()).willReturn(feeCollectorModel);
		final var fixedFeeCollectorRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(fixedFeeCollectorModel)).willReturn(fixedFeeCollectorRel);
		given(fixedFeeCollectorRel.getToken()).willReturn(newProvisionalToken);
		given(fixedFeeCollectorRel.getAccount()).willReturn(fixedFeeCollectorModel);
		final var fractionalFeeCollectorRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(fractionalFeeCollectorModel)).willReturn(
				fractionalFeeCollectorRel);
		given(fractionalFeeCollectorRel.getToken()).willReturn(newProvisionalToken);
		given(fractionalFeeCollectorRel.getAccount()).willReturn(fractionalFeeCollectorModel);
		final var nonAutoEnabledCollectorRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(nonAutoEnabledFeeCollectorModel)).willReturn(
				nonAutoEnabledCollectorRel);
		given(nonAutoEnabledCollectorRel.getToken()).willReturn(newProvisionalToken);
		given(nonAutoEnabledCollectorRel.getAccount()).willReturn(nonAutoEnabledFeeCollectorModel);

		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(100);
		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
		given(typedTokenStore.loadTokenOrFailWith(eq(Id.fromGrpcToken(misc)), any())).willReturn(denom);
		given(denom.getId()).willReturn(denomId);
		given(denom.getType()).willReturn(FUNGIBLE_COMMON);
		given(feeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(treasuryAssociatedTokenIds.contains(any(Id.class))).willReturn(true);
		given(treasuryAssociatedTokenIds.contains(newProvisionalToken.getId())).willReturn(false);
		given(newProvisionalToken.hasKycKey()).willReturn(true);
		given(newProvisionalToken.hasFreezeKey()).willReturn(true);

		subject.doStateTransition();

		assertNotNull(newProvisionalToken.getCustomFees());
		verify(typedTokenStore).persistTokenRelationships(anyList());
		verify(feeCollectorModel).associateWith(anyList(), anyInt(), anyBoolean());
	}

	@Test
	void abortsIfFeeCollectorEnablementFails() {
		givenValidTxnCtx(true, true, true, false);
		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();

		// and:
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(100);
		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
		given(typedTokenStore.loadTokenOrFailWith(eq(Id.fromGrpcToken(misc)), any())).willReturn(denom);
		given(denom.getId()).willReturn(denomId);
		given(denom.getType()).willReturn(FUNGIBLE_COMMON);
		given(feeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(treasuryAssociatedTokenIds.contains(any(Id.class))).willReturn(false);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);

		// then:
		verify(typedTokenStore, never()).persistNew(any());
		verify(typedTokenStore, never()).persistTokenRelationships(anyList());
	}

	@Test
	void abortsOnNotFullySpecifiedCustomFeeList() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(CustomFee.newBuilder().build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEE_NOT_FULLY_SPECIFIED);
	}

	@Test
	void abortsOnTooLongFeeList() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(-10);

		assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEES_LIST_TOO_LONG);
	}

	@Test
	void abortsOnInvalidFeeCollector() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(CustomFee.newBuilder().setFixedFee(
				FixedFee.newBuilder()
						.setAmount(100)
						.build())
				.setFeeCollectorAccountId(feeCollector)
				.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(feeCollector)), any()))
				.willThrow(new InvalidTransactionException(INVALID_CUSTOM_FEE_COLLECTOR));

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_CUSTOM_FEE_COLLECTOR);
	}

	@Test
	void abortsOnMissingDenomination() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(CustomFee.newBuilder().setFixedFee(
				FixedFee.newBuilder()
						.setAmount(100)
						.setDenominatingTokenId(denomId.asGrpcToken())
						.build())
				.setFeeCollectorAccountId(feeCollector)
				.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();
		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willThrow(
				new InvalidTransactionException(INVALID_TOKEN_ID_IN_CUSTOM_FEES));

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
	}

	@Test
	void rejectsNftAsFeeDenomination() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(CustomFee.newBuilder().setFixedFee(
				FixedFee.newBuilder()
						.setAmount(100)
						.setDenominatingTokenId(denomId.asGrpcToken())
						.build())
				.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();

		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
		given(denom.getType()).willReturn(com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE);

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
	}

	@Test
	void rejectsUnassociatedFeeCollector() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(CustomFee.newBuilder().setFixedFee(
				FixedFee.newBuilder()
						.setAmount(100)
						.setDenominatingTokenId(denomId.asGrpcToken())
						.build())
				.setFeeCollectorAccountId(feeCollector)
				.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();
		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
		given(denom.getType()).willReturn(FUNGIBLE_COMMON);
		given(treasuryAssociatedTokenIds.contains(any(Id.class))).willReturn(false);

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
	}

	@Test
	void rejectsInvalidAutoRenewAccount() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setAutoRenewAccount(autoRenewId.asGrpcAccount())
						.setExpiry(expiry));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		given(accountStore.loadAccountOrFailWith(eq(autoRenewId), any())).willThrow(
				new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_AUTORENEW_ACCOUNT);
	}

	@Test
	void rejectsInvalidTreasury() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(treasury)), any())).willThrow(
				new InvalidTransactionException(INVALID_TREASURY_ACCOUNT_FOR_TOKEN));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
	}

	@Test
	void rejectsRoyaltyFeeWithInvalidType() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(9)
										.setDenominator(10)))
						.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
	}

	@Test
	void rejectsRoyaltyFeeWithInvalidFallbackDenominator() {

		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setTokenType(NON_FUNGIBLE_UNIQUE)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(9)
										.setDenominator(10))
								.setFallbackFee(
										FixedFee.newBuilder()
												.setAmount(10)
												.setDenominatingTokenId(denomId.asGrpcToken())
												.build()))
						.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willThrow(
				new InvalidTransactionException(INVALID_TOKEN_ID_IN_CUSTOM_FEES));
		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
	}

	@Test
	void rejectRoyaltyFeeWithNonAssociatedDenominator() {

		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setTokenType(NON_FUNGIBLE_UNIQUE)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(9)
										.setDenominator(10))
								.setFallbackFee(
										FixedFee.newBuilder()
												.setAmount(10)
												.setDenominatingTokenId(denomId.asGrpcToken())
												.build()))
						.setFeeCollectorAccountId(feeCollector)
						.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();

		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
		given(denom.getType()).willReturn(com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE);
		given(denom.getId()).willReturn(denomId);
		given(treasuryAssociatedTokenIds.contains(any(Id.class))).willReturn(false);

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
	}

	@Test
	void rejectsRoyaltyFeeWithInvalidFraction() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setTokenType(NON_FUNGIBLE_UNIQUE)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(11)
										.setDenominator(10))
								.setFallbackFee(
										FixedFee.newBuilder()
												.setAmount(10)
												.setDenominatingTokenId(denomId.asGrpcToken())
												.build()))
						.setFeeCollectorAccountId(feeCollector)
						.build()));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		assertFailsWith(() -> subject.doStateTransition(), ROYALTY_FRACTION_CANNOT_EXCEED_ONE);
	}

	@Test
	void acceptsValidRoyaltyFee() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setTokenType(NON_FUNGIBLE_UNIQUE)
						.setExpiry(expiry));
		builder.getTokenCreationBuilder().addAllCustomFees(List.of(
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(5)
										.setDenominator(10))
								.setFallbackFee(
										FixedFee.newBuilder()
												.setAmount(10)
												.setDenominatingTokenId(denomId.asGrpcToken())
												.build()))
						.setFeeCollectorAccountId(feeCollector)
						.build(),
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(5)
										.setDenominator(10)
										.build())
								.setFallbackFee(FixedFee.newBuilder()
										.setAmount(10)
										.setDenominatingTokenId(Id.DEFAULT.asGrpcToken())
										.build()))
						.build(),
				CustomFee.newBuilder()
						.setRoyaltyFee(RoyaltyFee.newBuilder()
								.setExchangeValueFraction(Fraction.newBuilder()
										.setNumerator(5)
										.setDenominator(10)
										.build())
								.setFallbackFee(FixedFee.newBuilder()
										.setAmount(10)
										.build()))
						.build()
				));
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);

		mockModelTreasury();
		mockProvisionalToken();
		mockFeeCollectors();

		given(typedTokenStore.loadTokenOrFailWith(eq(denomId), any())).willReturn(denom);
		given(denom.getId()).willReturn(denomId);
		given(treasuryAssociatedTokenIds.contains(any(Id.class))).willReturn(true);
		given(treasuryAssociatedTokenIds.contains(newProvisionalToken.getId())).willReturn(false);

		final var treasuryRelMock = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(treasuryRelMock);
		given(treasuryRelMock.getAccount()).willReturn(modelTreasury);
		given(treasuryRelMock.getToken()).willReturn(newProvisionalToken);
		given(dynamicProperties.maxCustomFeesAllowed()).willReturn(10);

		subject.doStateTransition();
		verify(ids, never()).reclaimLastId();
		verify(ids, never()).resetProvisionalIds();
	}


	@Test
	void doesntUnfreezeIfNoKeyIsPresent() {
		givenValidTxnCtx(true, false, false, false);
		// and:
		mockModelTreasury();
		mockProvisionalToken();

		final var mockRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(mockRel);
		given(mockRel.getAccount()).willReturn(modelTreasury);
		given(mockRel.getToken()).willReturn(newProvisionalToken);
		given(modelTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		// when:
		subject.doStateTransition();

		// then:
		verify(mockRel, never()).setFrozen(true);
		// and:
		verify(typedTokenStore).persistNew(any());
		verify(typedTokenStore).persistTokenRelationships(anyList());
	}

	@Test
	void doesntGrantKycIfNoKeyIsPresent() {
		givenValidTxnCtx(false, true, false, false);
		// and:
		mockModelTreasury();
		mockProvisionalToken();
		final var mockRel = mock(TokenRelationship.class);
		given(newProvisionalToken.newEnabledRelationship(modelTreasury)).willReturn(mockRel);
		given(mockRel.getAccount()).willReturn(modelTreasury);
		given(mockRel.getToken()).willReturn(newProvisionalToken);

		// when:
		subject.doStateTransition();

		// then:
		verify(mockRel, never()).setKycGranted(true);
		// and:
		verify(typedTokenStore).persistNew(any());
		verify(typedTokenStore).persistTokenRelationships(anyList());
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

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void uniqueNotSupportedIfNftsNotEnabled() {
		givenValidTxnCtx(false, false, false, true);

		// expect:
		assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void uniqueSupportedIfNftsEnabled() {
		given(dynamicProperties.areNftsEnabled()).willReturn(true);
		givenValidTxnCtx(false, false, false, true);

		// expect:
		assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void acceptsMissingAutoRenewAcount() {
		givenValidMissingRenewAccount();

		// expect
		assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsMissingSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(MISSING_TOKEN_SYMBOL);

		// expect:
		assertEquals(MISSING_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsTooLongSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// expect:
		assertEquals(INVALID_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsMissingName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(MISSING_TOKEN_NAME);

		// expect:
		assertEquals(MISSING_TOKEN_NAME, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsTooLongName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidInitialSupply() {
		givenInvalidInitialSupply();

		// expect:
		assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidDecimals() {
		givenInvalidDecimals();

		// expect:
		assertEquals(INVALID_TOKEN_DECIMALS, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsMissingTreasury() {
		givenMissingTreasury();

		// expect:
		assertEquals(INVALID_TREASURY_ACCOUNT_FOR_TOKEN, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidFeeSchedule() {
		givenInvalidFeeScheduleKey();

		// expect:
		assertEquals(INVALID_CUSTOM_FEE_SCHEDULE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidAdminKey() {
		givenInvalidAdminKey();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidKycKey() {
		givenInvalidKycKey();

		// expect:
		assertEquals(INVALID_KYC_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidWipeKey() {
		givenInvalidWipeKey();

		// expect:
		assertEquals(INVALID_WIPE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidSupplyKey() {
		givenInvalidSupplyKey();

		// expect:
		assertEquals(INVALID_SUPPLY_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectMissingFreezeKeyWithFreezeDefault() {
		givenMissingFreezeKeyWithFreezeDefault();

		// expect:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidFreezeKey() {
		givenInvalidFreezeKey();

		// expect:
		assertEquals(INVALID_FREEZE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidAdminKeyBytes() {
		givenInvalidAdminKeyBytes();

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
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsExpiryInPastInPrecheck() {
		givenInvalidExpirationTime();

		assertEquals(INVALID_EXPIRATION_TIME, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidSupplyChecks() {
		givenInvalidSupplyTypeAndSupply();
		assertEquals(INVALID_TOKEN_MAX_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidInitialAndMaxSupply() {
		givenTxWithInvalidSupplies();
		assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void objectContractWorks() {
		final var newId = ids.newTokenId(treasury);
		subject.reclaimCreatedIds();
		subject.resetCreatedIds();
		assertEquals(newId, ids.newTokenId(treasury));
	}

	private void givenInvalidSupplyTypeAndSupply() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setSupplyType(TokenSupplyType.INFINITE)
						.setInitialSupply(0)
						.setMaxSupply(1)
						.build()
				);


		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(store.isCreationPending()).willReturn(true);
		given(validator.isValidExpiry(expiry)).willReturn(true);
	}

	private void givenTxWithInvalidSupplies() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setSupplyType(TokenSupplyType.FINITE)
						.setInitialSupply(1000)
						.setMaxSupply(1)
						.build()
				);
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(validator.isValidExpiry(expiry)).willReturn(true);
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(false, false, false, false);
	}

	private void givenValidTxnCtx(
			boolean withKyc,
			boolean withFreeze,
			boolean withCustomFees,
			boolean isUnique
	) {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setMemo(memo)
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setAutoRenewAccount(renewAccount)
						.setExpiry(expiry));
		if (isUnique) {
			builder.getTokenCreationBuilder().setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
		}
		if (withCustomFees) {
			builder.getTokenCreationBuilder().addAllCustomFees(grpcCustomFees);
		}
		if (withFreeze) {
			builder.getTokenCreationBuilder().setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey());
		}
		if (withKyc) {
			builder.getTokenCreationBuilder().setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey());
		}
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(store.isCreationPending()).willReturn(true);
		given(validator.isValidExpiry(expiry)).willReturn(true);
	}

	private void givenInvalidInitialSupply() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(-1))
				.build();
	}

	private void givenInvalidDecimals() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(0)
						.setDecimals(-1))
				.build();
	}

	private void givenMissingTreasury() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder())
				.build();
	}

	private void givenMissingFreezeKeyWithFreezeDefault() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setFreezeDefault(true))
				.build();
	}

	private void givenInvalidFreezeKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setFreezeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidAdminKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidFeeScheduleKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setFeeScheduleKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidAdminKeyBytes() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(Key.newBuilder().setEd25519(ByteString.copyFrom("1".getBytes()))))
				.build();
	}

	private void givenInvalidKycKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setKycKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidWipeKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setWipeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidSupplyKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setSupplyKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidExpirationTime() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setExpiry(Timestamp.newBuilder().setSeconds(-1)))
				.build();
	}

	private void givenValidMissingRenewAccount() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(Timestamp.newBuilder().setSeconds(thisSecond + Instant.now().getEpochSecond())))
				.build();
	}

	private void withAlwaysValidValidator() {
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.tokenNameCheck(any())).willReturn(OK);
		given(validator.tokenSymbolCheck(any())).willReturn(OK);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
	}

	private void mockProvisionalToken() {
		given(newProvisionalToken.getId()).willReturn(Id.fromGrpcToken(created));
		given(newProvisionalToken.getType()).willReturn(FUNGIBLE_COMMON);
		staticTokenHandle.when(() -> Token.fromGrpcTokenCreate(any(), any(), any(), any(), any(), anyLong())).thenReturn(
				newProvisionalToken);
	}

	private void mockModelTreasury() {
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(treasury)), any())).willReturn(modelTreasury);
		given(modelTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(modelTreasury.getId()).willReturn(modelTreasuryId);
	}

	private void mockFeeCollectors() {
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(feeCollector)), any())).willReturn(
				feeCollectorModel);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(fixedFeeCollector)), any())).willReturn(
				fixedFeeCollectorModel);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(fractionalFeeCollector)), any())).willReturn(
				fractionalFeeCollectorModel);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(hbarFeeCollector)), any())).willReturn(
				hbarFeeCollectorModel);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(nonAutoEnabledFeeCollector)), any())).willReturn(
				nonAutoEnabledFeeCollectorModel);

		given(accountStore.loadAccount(Id.fromGrpcAccount(feeCollector))).willReturn(feeCollectorModel);
		given(accountStore.loadAccount(Id.fromGrpcAccount(fixedFeeCollector))).willReturn(fixedFeeCollectorModel);
		given(accountStore.loadAccount(Id.fromGrpcAccount(fractionalFeeCollector))).willReturn(
				fractionalFeeCollectorModel);
		given(accountStore.loadAccount(Id.fromGrpcAccount(hbarFeeCollector))).willReturn(hbarFeeCollectorModel);
		given(accountStore.loadAccount(Id.fromGrpcAccount(nonAutoEnabledFeeCollector))).willReturn(
				nonAutoEnabledFeeCollectorModel);

		given(feeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(feeCollectorModel.getId()).willReturn(feeCollectorId);
		given(fixedFeeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(fixedFeeCollectorModel.getId()).willReturn(fixedFeeCollectorId);
		given(fractionalFeeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(fractionalFeeCollectorModel.getId()).willReturn(fractionalFeeCollectorId);
		given(hbarFeeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(hbarFeeCollectorModel.getId()).willReturn(hbarFeeCollectorId);
		given(nonAutoEnabledFeeCollectorModel.getAssociatedTokens()).willReturn(treasuryAssociatedTokenIds);
		given(nonAutoEnabledFeeCollectorModel.getId()).willReturn(nonAutoEnabledFeeCollectorId);
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
