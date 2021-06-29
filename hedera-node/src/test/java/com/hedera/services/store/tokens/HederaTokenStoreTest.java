package com.hedera.services.store.tokens;

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

import com.google.protobuf.StringValue;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import proto.CustomFeesOuterClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.mocks.TestContextValidator.CONSENSUS_NOW;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_ARE_MARKED_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

class HederaTokenStoreTest {
	private EntityIdSource ids;
	private GlobalDynamicProperties properties;
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	private HederaLedger hederaLedger;

	private MerkleToken token;

	private Key newKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asKey();
	private JKey newFcKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asJKeyUnchecked();
	private Key adminKey, kycKey, freezeKey, supplyKey, wipeKey;
	private String symbol = "NOTHBAR";
	private String newSymbol = "REALLYSOM";
	private String newMemo = "NEWMEMO";
	private String memo = "TOKENMEMO";
	private String name = "TOKENNAME";
	private String newName = "NEWNAME";
	private int maxCustomFees = 4;
	private long expiry = CONSENSUS_NOW + 1_234_567;
	private long newExpiry = CONSENSUS_NOW + 1_432_765;
	private long totalSupply = 1_000_000;
	private long adjustment = 1;
	private int decimals = 10;
	private long treasuryBalance = 50_000, sponsorBalance = 1_000;
	private TokenID misc = IdUtils.asToken("3.2.1");
	private TokenID anotherMisc = IdUtils.asToken("6.4.2");
	private boolean freezeDefault = true;
	private boolean accountsKycGrantedByDefault = false;
	private long autoRenewPeriod = 500_000;
	private long newAutoRenewPeriod = 2_000_000;
	private AccountID autoRenewAccount = IdUtils.asAccount("1.2.5");
	private AccountID newAutoRenewAccount = IdUtils.asAccount("1.2.6");
	private AccountID treasury = IdUtils.asAccount("1.2.3");
	private AccountID newTreasury = IdUtils.asAccount("3.2.1");
	private AccountID sponsor = IdUtils.asAccount("1.2.666");
	private AccountID feeCollector = treasury;
	private AccountID anotherFeeCollector = IdUtils.asAccount("1.2.777");
	private TokenID created = IdUtils.asToken("1.2.666666");
	private TokenID pending = IdUtils.asToken("1.2.555555");
	private int MAX_TOKENS_PER_ACCOUNT = 100;
	private int MAX_TOKEN_SYMBOL_UTF8_BYTES = 10;
	private int MAX_TOKEN_NAME_UTF8_BYTES = 100;
	private Pair<AccountID, TokenID> sponsorMisc = asTokenRel(sponsor, misc);
	private Pair<AccountID, TokenID> treasuryMisc = asTokenRel(treasury, misc);
	private Pair<AccountID, TokenID> anotherFeeCollectorMisc = asTokenRel(anotherFeeCollector, misc);
	private CustomFeesOuterClass.FixedFee fixedFeeInTokenUnits = CustomFeesOuterClass.FixedFee.newBuilder()
			.setDenominatingTokenId(misc)
			.setAmount(100)
			.build();
	private CustomFeesOuterClass.FixedFee fixedFeeInHbar = CustomFeesOuterClass.FixedFee.newBuilder()
			.setAmount(100)
			.build();
	private Fraction fraction = Fraction.newBuilder().setNumerator(15).setDenominator(100).build();
	private Fraction invalidFraction = Fraction.newBuilder().setNumerator(15).setDenominator(0).build();
	private CustomFeesOuterClass.FractionalFee fractionalFee = CustomFeesOuterClass.FractionalFee.newBuilder()
			.setFractionalAmount(fraction)
			.setMaximumAmount(50)
			.setMinimumAmount(10)
			.build();
	private CustomFeesOuterClass.CustomFee customFixedFeeInHbar = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector)
			.setFixedFee(fixedFeeInHbar)
			.build();
	private CustomFeesOuterClass.CustomFee customFixedFeeInHts = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(anotherFeeCollector)
			.setFixedFee(fixedFeeInTokenUnits)
			.build();
	private CustomFeesOuterClass.CustomFee customFractionalFee = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector)
			.setFractionalFee(fractionalFee)
			.build();
	private CustomFeesOuterClass.CustomFees grpcCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(customFixedFeeInHbar)
			.addCustomFees(customFixedFeeInHts)
			.addCustomFees(customFractionalFee)
			.build();
	private CustomFeesOuterClass.CustomFees grpcUnderspecifiedCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector))
			.build();
	private CustomFeesOuterClass.CustomFees grpcDivideByZeroCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
							.setFractionalAmount(invalidFraction)
					)).build();
	private CustomFeesOuterClass.CustomFees grpcNegativeFractionCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
							.setFractionalAmount(Fraction.newBuilder()
									.setNumerator(123L).setDenominator(-1_000L)
							)
					)).build();
	private CustomFeesOuterClass.CustomFees grpcNegativeMaximumCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
							.setFractionalAmount(Fraction.newBuilder()
									.setNumerator(123L).setDenominator(1_000L)
							).setMaximumAmount(-1L)
					)).build();
	private CustomFeesOuterClass.CustomFees grpcNegativeMinimumCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
							.setFractionalAmount(Fraction.newBuilder()
									.setNumerator(123L).setDenominator(1_000L)
							).setMinimumAmount(-1L)
					)).build();
	private CustomFeesOuterClass.CustomFees grpcZeroFractionCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
							.setFractionalAmount(Fraction.newBuilder()
									.setNumerator(0L).setDenominator(1_000L)
							)
					)).build();
	private CustomFeesOuterClass.CustomFees grpcNegativeFixedCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
							.setAmount(-1_000L)
					)).build();
	private CustomFeesOuterClass.CustomFees grpcZeroFixedCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(CustomFeesOuterClass.CustomFee.newBuilder()
					.setFeeCollectorAccountId(feeCollector)
					.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
							.setAmount(0L)
					)).build();

	private HederaTokenStore subject;

	@BeforeEach
	void setup() {
		adminKey = TOKEN_ADMIN_KT.asKey();
		kycKey = TOKEN_KYC_KT.asKey();
		freezeKey = TOKEN_FREEZE_KT.asKey();
		wipeKey = MISC_ACCOUNT_KT.asKey();
		supplyKey = COMPLEX_KEY_ACCOUNT_KT.asKey();

		token = mock(MerkleToken.class);
		given(token.expiry()).willReturn(expiry);
		given(token.symbol()).willReturn(symbol);
		given(token.hasAutoRenewAccount()).willReturn(true);
		given(token.adminKey()).willReturn(Optional.of(TOKEN_ADMIN_KT.asJKeyUnchecked()));
		given(token.name()).willReturn(name);
		given(token.hasAdminKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));
		given(token.isFeeScheduleMutable()).willReturn(true);

		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(sponsor)).willReturn(created);

		hederaLedger = mock(HederaLedger.class);

		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(
				TransactionalLedger.class);
		given(accountsLedger.exists(treasury)).willReturn(true);
		given(accountsLedger.exists(anotherFeeCollector)).willReturn(true);
		given(accountsLedger.exists(autoRenewAccount)).willReturn(true);
		given(accountsLedger.exists(newAutoRenewAccount)).willReturn(true);
		given(accountsLedger.exists(sponsor)).willReturn(true);
		given(accountsLedger.get(treasury, IS_DELETED)).willReturn(false);
		given(accountsLedger.get(autoRenewAccount, IS_DELETED)).willReturn(false);
		given(accountsLedger.get(newAutoRenewAccount, IS_DELETED)).willReturn(false);

		tokenRelsLedger = mock(TransactionalLedger.class);
		given(tokenRelsLedger.exists(sponsorMisc)).willReturn(true);
		given(tokenRelsLedger.get(sponsorMisc, TOKEN_BALANCE)).willReturn(sponsorBalance);
		given(tokenRelsLedger.get(sponsorMisc, IS_FROZEN)).willReturn(false);
		given(tokenRelsLedger.get(sponsorMisc, IS_KYC_GRANTED)).willReturn(true);
		given(tokenRelsLedger.exists(treasuryMisc)).willReturn(true);
		given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(true);
		given(tokenRelsLedger.get(treasuryMisc, TOKEN_BALANCE)).willReturn(treasuryBalance);
		given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(false);
		given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(true);

		tokens = (FCMap<MerkleEntityId, MerkleToken>) mock(FCMap.class);
		given(tokens.get(fromTokenId(created))).willReturn(token);
		given(tokens.containsKey(fromTokenId(misc))).willReturn(true);
		given(tokens.get(fromTokenId(misc))).willReturn(token);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxTokensPerAccount()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(properties.maxTokenSymbolUtf8Bytes()).willReturn(MAX_TOKEN_SYMBOL_UTF8_BYTES);
		given(properties.maxTokenNameUtf8Bytes()).willReturn(MAX_TOKEN_NAME_UTF8_BYTES);
		given(properties.maxCustomFeesAllowed()).willReturn(maxCustomFees);

		subject = new HederaTokenStore(ids, TEST_VALIDATOR, properties, () -> tokens, tokenRelsLedger);
		subject.setAccountsLedger(accountsLedger);
		subject.setHederaLedger(hederaLedger);
		subject.knownTreasuries.put(treasury, new HashSet<>() {{
			add(misc);
		}});
	}

	@Test
	void rebuildsAsExpected() {
		// setup:
		ArgumentCaptor<BiConsumer<MerkleEntityId, MerkleToken>> captor = forClass(BiConsumer.class);
		// and:
		subject.getKnownTreasuries().put(treasury, Set.of(anotherMisc));
		// and:
		final var deletedToken = new MerkleToken();
		deletedToken.setDeleted(true);
		deletedToken.setTreasury(EntityId.fromGrpcAccountId(newTreasury));

		// when:
		subject.rebuildViews();

		// then:
		verify(tokens, times(2)).forEach(captor.capture());
		// and:
		BiConsumer<MerkleEntityId, MerkleToken> visitor = captor.getAllValues().get(1);

		// and when:
		visitor.accept(fromTokenId(misc), token);
		visitor.accept(fromTokenId(anotherMisc), deletedToken);

		// then:
		final var extant = subject.getKnownTreasuries();
		assertEquals(1, extant.size());
		// and:
		assertTrue(extant.containsKey(treasury));
		assertEquals(extant.get(treasury), Set.of(misc));
	}

	@Test
	void injectsTokenRelsLedger() {
		// expect:
		verify(hederaLedger).setTokenRelsLedger(tokenRelsLedger);
	}

	@Test
	void applicationRejectsMissing() {
		// setup:
		final var change = mock(Consumer.class);

		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));
	}

	@Test
	void applicationAlwaysReplacesModifiableToken() {
		// setup:
		final var change = mock(Consumer.class);
		final var modifiableToken = mock(MerkleToken.class);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(modifiableToken);

		willThrow(IllegalStateException.class).given(change).accept(modifiableToken);

		// then:
		assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));
	}

	@Test
	void applicationWorks() {
		// setup:
		final var change = mock(Consumer.class);
		// and:
		InOrder inOrder = Mockito.inOrder(change, tokens);

		// when:
		subject.apply(misc, change);

		// then:
		inOrder.verify(tokens).getForModify(fromTokenId(misc));
		inOrder.verify(change).accept(token);
	}

	@Test
	void deletionWorksAsExpected() {
		// when:
		TokenStore.DELETION.accept(token);

		// then:
		verify(token).setDeleted(true);
	}

	@Test
	void deletesAsExpected() {
		// when:
		final var outcome = subject.delete(misc);

		// then:
		assertEquals(OK, outcome);
		// and:
		assertTrue(subject.knownTreasuries.isEmpty());
	}

	@Test
	void rejectsDeletionMissingAdminKey() {
		// given:
		given(token.adminKey()).willReturn(Optional.empty());

		// when:
		final var outcome = subject.delete(misc);

		// then:
		assertEquals(TOKEN_IS_IMMUTABLE, outcome);
	}

	@Test
	void rejectsDeletionTokenAlreadyDeleted() {
		// given:
		given(token.isDeleted()).willReturn(true);

		// when:
		final var outcome = subject.delete(misc);

		// then:
		assertEquals(TOKEN_WAS_DELETED, outcome);
	}

	@Test
	void rejectsMissingDeletion() {
		// given:
		final var mockSubject = mock(TokenStore.class);

		given(mockSubject.resolve(misc)).willReturn(TokenStore.MISSING_TOKEN);
		willCallRealMethod().given(mockSubject).delete(misc);

		// when:
		final var outcome = mockSubject.delete(misc);

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
		verify(mockSubject, never()).apply(any(), any());
	}

	@Test
	void getDelegates() {
		// expect:
		assertSame(token, subject.get(misc));
	}

	@Test
	void getThrowsIseOnMissing() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.get(misc));
	}

	@Test
	void getCanReturnPending() {
		// setup:
		subject.pendingId = pending;
		subject.pendingCreation = token;

		// expect:
		assertSame(token, subject.get(pending));
	}

	@Test
	void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
		// expect:
		assertFalse(subject.exists(HederaTokenStore.NO_PENDING_ID));
	}

	@Test
	void existenceCheckIncludesPending() {
		// setup:
		subject.pendingId = pending;

		// expect:
		assertTrue(subject.exists(pending));
	}

	@Test
	void freezingRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		final var status = subject.freeze(sponsor, misc);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	void associatingRejectsDeletedTokens() {
		given(token.isDeleted()).willReturn(true);

		// when:
		final var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKEN_WAS_DELETED, status);
	}

	@Test
	void associatingRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		final var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(INVALID_TOKEN_ID, status);
	}

	@Test
	void associatingRejectsMissingAccounts() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		final var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	void realAssociationsExist() {
		// expect:
		assertTrue(subject.associationExists(sponsor, misc));
	}

	@Test
	void noAssociationsWithMissingAccounts() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// expect:
		assertFalse(subject.associationExists(sponsor, misc));
	}

	@Test
	void dissociatingRejectsUnassociatedTokens() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(false);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
	}

	@Test
	void dissociatingRejectsTreasuryAccount() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		subject = spy(subject);
		given(subject.isTreasuryForToken(sponsor, misc)).willReturn(true);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(ACCOUNT_IS_TREASURY, status);
	}

	@Test
	void dissociatingRejectsFrozenAccount() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		given(tokenRelsLedger.get(sponsorMisc, IS_FROZEN)).willReturn(true);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
	}

	@Test
	void associatingRejectsAlreadyAssociatedTokens() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		final var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
	}

	@Test
	void associatingRejectsIfCappedAssociationsLimit() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(false);
		given(tokens.numAssociations()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		final var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		verify(tokens, never()).associateAll(any());
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
	}

	@Test
	void associatingHappyPathWorks() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(false);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(token.hasKycKey()).willReturn(true);
		given(token.hasFreezeKey()).willReturn(true);
		given(token.accountsAreFrozenByDefault()).willReturn(true);

		// when:
		final var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).associateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).create(key);
		verify(tokenRelsLedger).set(key, TokenRelProperty.IS_FROZEN, true);
		verify(tokenRelsLedger).set(key, TokenRelProperty.IS_KYC_GRANTED, false);
	}

	@Test
	void dissociatingWorksEvenIfTokenDoesntExistAnymore() {
		// setup:
		final var accountTokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(accountTokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(accountTokens);
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(0L);
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(accountTokens).dissociateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, accountTokens);
		verify(tokenRelsLedger).destroy(key);
	}

	@Test
	void dissociatingHappyPathWorks() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(0L);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).dissociateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).destroy(key);
	}

	@Test
	void dissociatingFailsIfTokenBalanceIsNonzero() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(1L);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES, status);
		// and:
		verify(tokens, never()).dissociateAll(Set.of(misc));
		verify(hederaLedger, never()).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger, never()).destroy(key);
	}

	@Test
	void dissociatingPermitsFrozenRelIfDeleted() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(1L);
		given(token.isDeleted()).willReturn(true);
		given(tokenRelsLedger.get(sponsorMisc, IS_FROZEN)).willReturn(true);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).dissociateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).destroy(key);
	}

	@Test
	void dissociatingPermitsNonzeroTokenBalanceIfDeleted() {
		// setup:
		final var tokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(1L);
		given(token.isDeleted()).willReturn(true);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).dissociateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).destroy(key);
	}

	@Test
	void dissociatingPermitsNonzeroTokenBalanceIfExpired() {
		// setup:
		long balance = 123L;
		final var tokens = mock(MerkleAccountTokens.class);
		final var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(balance);
		given(token.expiry()).willReturn(CONSENSUS_NOW - 1);

		// when:
		final var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).dissociateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).destroy(key);
		verify(hederaLedger).doTokenTransfer(misc, sponsor, treasury, balance);
	}

	@Test
	void grantingKycRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		final var status = subject.grantKyc(sponsor, misc);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	void grantingKycRejectsDetachedAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(true);
		given(hederaLedger.isDetached(sponsor)).willReturn(true);

		// when:
		final var status = subject.grantKyc(sponsor, misc);

		// expect:
		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
	}

	@Test
	void grantingKycRejectsDeletedAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(true);
		given(hederaLedger.isDeleted(sponsor)).willReturn(true);

		// when:
		final var status = subject.grantKyc(sponsor, misc);

		// expect:
		assertEquals(ACCOUNT_DELETED, status);
	}

	@Test
	void revokingKycRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		final var status = subject.revokeKyc(sponsor, misc);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	void wipingRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		final var status = subject.wipe(sponsor, misc, adjustment, false);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	void wipingRejectsTokenWithNoWipeKey() {
		// when:
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));

		final var status = subject.wipe(sponsor, misc, adjustment, false);

		// expect:
		assertEquals(TOKEN_HAS_NO_WIPE_KEY, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -adjustment);
	}

	@Test
	void wipingRejectsTokenTreasury() {
		long wiping = 3L;

		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(sponsor));

		// when:
		final var status = subject.wipe(sponsor, misc, wiping, false);

		// expect:
		assertEquals(CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -wiping);
	}

	@Test
	void wipingWithoutTokenRelationshipFails() {
		// setup:
		given(token.hasWipeKey()).willReturn(false);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));
		// and:
		given(tokenRelsLedger.exists(sponsorMisc)).willReturn(false);

		// when:
		final var status = subject.wipe(sponsor, misc, adjustment, true);

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -adjustment);
	}

	@Test
	void wipingWorksWithoutWipeKeyIfCheckSkipped() {
		// setup:
		given(token.hasWipeKey()).willReturn(false);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));

		// when:
		final var status = subject.wipe(sponsor, misc, adjustment, true);

		// expect:
		assertEquals(OK, status);
		verify(hederaLedger).updateTokenXfers(misc, sponsor, -adjustment);
		verify(token).adjustTotalSupplyBy(-adjustment);
		verify(tokenRelsLedger).set(
				argThat(sponsorMisc::equals),
				argThat(TOKEN_BALANCE::equals),
				longThat(l -> l == (sponsorBalance - adjustment)));
	}

	@Test
	void wipingUpdatesTokenXfersAsExpected() {
		// setup:
		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));

		// when:
		final var status = subject.wipe(sponsor, misc, adjustment, false);

		// expect:
		assertEquals(OK, status);
		// and:
		verify(hederaLedger).updateTokenXfers(misc, sponsor, -adjustment);
		verify(token).adjustTotalSupplyBy(-adjustment);
		verify(tokenRelsLedger).set(
				argThat(sponsorMisc::equals),
				argThat(TOKEN_BALANCE::equals),
				longThat(l -> l == (sponsorBalance - adjustment)));
	}

	@Test
	void wipingFailsWithInvalidWipingAmount() {
		// setup:
		long wipe = 1_235L;

		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));

		// when:
		final var status = subject.wipe(sponsor, misc, wipe, false);

		// expect:
		assertEquals(INVALID_WIPING_AMOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -wipe);
	}

	@Test
	void adjustingRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		final var status = subject.adjustBalance(sponsor, misc, 1);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	void updateRejectsInvalidExpiry() {
		// given:
		var op = updateWith(NO_KEYS, true, true, false);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(expiry - 1)).build();

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(INVALID_EXPIRATION_TIME, outcome);
	}

	@Test
	void updateRejectsImmutableToken() {
		given(token.hasAdminKey()).willReturn(false);

		var op = updateWith(NO_KEYS, true, true, false);
		var outcome = subject.update(op, CONSENSUS_NOW);
		assertEquals(TOKEN_IS_IMMUTABLE, outcome);

		verify(token, never()).isFeeScheduleMutable();
		verify(token, never()).setFeeScheduleFrom(any());
		op = updateWithCustomFees(grpcCustomFees);
		outcome = subject.update(op, CONSENSUS_NOW);
		assertEquals(TOKEN_IS_IMMUTABLE, outcome);
	}

	@Test
	void canExtendImmutableExpiry() {
		given(token.hasAdminKey()).willReturn(false);
		// given:
		var op = updateWith(NO_KEYS, false, false, false);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234)).build();

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(OK, outcome);
	}

	@Test
	void updateRejectsInvalidNewAutoRenew() {
		given(accountsLedger.exists(newAutoRenewAccount)).willReturn(false);
		// and:
		final var op = updateWith(NO_KEYS, true, true, false, true, false);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
	}

	@Test
	void updateRejectsInvalidNewAutoRenewPeriod() {
		var op = updateWith(NO_KEYS, true, true, false, false, false);
		op = op.toBuilder().setAutoRenewPeriod(enduring(-1L)).build();

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(INVALID_RENEWAL_PERIOD, outcome);
	}

	@Test
	void updateRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		final var op = updateWith(ALL_KEYS, true, true, true);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
	}


	@Test
	void updateRejectsInappropriateKycKey() {
		givenUpdateTarget(NO_KEYS);
		// and:
		final var op = updateWith(EnumSet.of(KeyType.KYC), false, false, false);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(TOKEN_HAS_NO_KYC_KEY, outcome);
	}

	@Test
	void updateRejectsInappropriateFreezeKey() {
		givenUpdateTarget(NO_KEYS);
		// and:
		final var op = updateWith(EnumSet.of(KeyType.FREEZE), false, false, false);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, outcome);
	}

	@Test
	void updateRejectsInappropriateWipeKey() {
		givenUpdateTarget(NO_KEYS);
		// and:
		final var op = updateWith(EnumSet.of(KeyType.WIPE), false, false, false);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(TOKEN_HAS_NO_WIPE_KEY, outcome);
	}

	@Test
	void updateRejectsInappropriateSupplyKey() {
		givenUpdateTarget(NO_KEYS);
		// and:
		final var op = updateWith(EnumSet.of(KeyType.SUPPLY), false, false, false);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, outcome);
	}

	@Test
	void updateRejectsImmutableTokenFeeSchedule() {
		given(token.isFeeScheduleMutable()).willReturn(false);
		givenUpdateTarget(NO_KEYS);
		final var clearImmutability = CustomFeesOuterClass.CustomFees.newBuilder()
				.setCanUpdateWithAdminKey(true)
				.build();
		final var op = updateWithCustomFees(clearImmutability);

		final var outcome = subject.update(op, CONSENSUS_NOW);

		verify(token, never()).setFeeScheduleFrom(any());
		assertEquals(CUSTOM_FEES_ARE_MARKED_IMMUTABLE, outcome);
	}

	@Test
	void treasuryRemovalForTokenRemovesKeyWhenEmpty() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc));
		subject.knownTreasuries.put(treasury, tokenSet);

		subject.removeKnownTreasuryForToken(treasury, misc);

		// expect:
		assertFalse(subject.knownTreasuries.containsKey(treasury));
		assertTrue(subject.knownTreasuries.isEmpty());
	}

	@Test
	void addKnownTreasuryWorks() {
		subject.addKnownTreasury(treasury, misc);

		// expect:
		assertTrue(subject.knownTreasuries.containsKey(treasury));
	}

	@Test
	void removeKnownTreasuryWorks() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc, anotherMisc));
		subject.knownTreasuries.put(treasury, tokenSet);

		subject.removeKnownTreasuryForToken(treasury, misc);

		// expect:
		assertTrue(subject.knownTreasuries.containsKey(treasury));
		assertEquals(1, subject.knownTreasuries.size());
		assertTrue(subject.knownTreasuries.get(treasury).contains(anotherMisc));
	}

	@Test
	void isKnownTreasuryWorks() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc));

		subject.knownTreasuries.put(treasury, tokenSet);

		// expect:
		assertTrue(subject.isKnownTreasury(treasury));
	}

	@Test
	void treasuriesServeWorks() {
		Set<TokenID> tokenSet = new HashSet<>(List.of(anotherMisc, misc));

		subject.knownTreasuries.put(treasury, tokenSet);

		// expect:
		assertEquals(List.of(misc, anotherMisc), subject.listOfTokensServed(treasury));

		// and when:
		subject.knownTreasuries.remove(treasury);

		// then:
		assertSame(Collections.emptyList(), subject.listOfTokensServed(treasury));
	}

	@Test
	void isTreasuryForTokenWorks() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc));

		subject.knownTreasuries.put(treasury, tokenSet);

		// expect:
		assertTrue(subject.isTreasuryForToken(treasury, misc));
	}

	@Test
	void isTreasuryForTokenReturnsFalse() {
		// setup:
		subject.knownTreasuries.clear();

		// expect:
		assertFalse(subject.isTreasuryForToken(treasury, misc));
	}

	@Test
	void throwsIfKnownTreasuryIsMissing() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.removeKnownTreasuryForToken(null, misc));
	}

	@Test
	void throwsIfInvalidTreasury() {
		// setup:
		subject.knownTreasuries.clear();

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.removeKnownTreasuryForToken(treasury, misc));
	}


	@Test
	void updateHappyPathIgnoresZeroExpiry() {
		// setup:
		subject.addKnownTreasury(treasury, misc);
		Set<TokenID> tokenSet = new HashSet<>();
		tokenSet.add(misc);

		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(0)).build();

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(OK, outcome);
		verify(token, never()).setExpiry(anyLong());
		// and:
		assertFalse(subject.knownTreasuries.containsKey(treasury));
		assertEquals(subject.knownTreasuries.get(newTreasury), tokenSet);
	}

	@Test
	void updateRemovesAdminKeyWhenAppropos() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		Set<TokenID> tokenSet = new HashSet<>();
		tokenSet.add(misc);

		givenUpdateTarget(EnumSet.noneOf(KeyType.class));
		// and:
		final var op = updateWith(EnumSet.of(KeyType.EMPTY_ADMIN), false, false, false);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);
		// then:
		assertEquals(OK, outcome);
		verify(token).setAdminKey(MerkleToken.UNUSED_KEY);
	}

	@Test
	void updateHappyPathWorksForEverythingWithNewExpiry() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		Set<TokenID> tokenSet = new HashSet<>();
		tokenSet.add(misc);

		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);
		op = op.toBuilder()
				.setExpiry(Timestamp.newBuilder().setSeconds(newExpiry))
				.setCustomFees(grpcCustomFees)
				.build();

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);
		// then:
		assertEquals(OK, outcome);
		verify(token).setSymbol(newSymbol);
		verify(token).setName(newName);
		verify(token).setExpiry(newExpiry);
		verify(token).setTreasury(EntityId.fromGrpcAccountId(newTreasury));
		verify(token).setAdminKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setFreezeKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setKycKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setSupplyKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setWipeKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setFeeScheduleFrom(grpcCustomFees);
		// and:
		assertFalse(subject.knownTreasuries.containsKey(treasury));
		assertEquals(subject.knownTreasuries.get(newTreasury), tokenSet);
	}

	@Test
	void updateHappyPathWorksWithNewMemo() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		givenUpdateTarget(ALL_KEYS);
		// and:
		final var op = updateWith(NO_KEYS,
				false,
				false,
				false,
				false,
				false,
				false,
				true);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(OK, outcome);
		verify(token).setMemo(newMemo);
	}

	@Test
	void updateHappyPathWorksWithNewAutoRenewAccount() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		givenUpdateTarget(ALL_KEYS);
		// and:
		final var op = updateWith(ALL_KEYS, true, true, true, true, true);

		// when:
		final var outcome = subject.update(op, CONSENSUS_NOW);

		// then:
		assertEquals(OK, outcome);
		verify(token).setAutoRenewAccount(EntityId.fromGrpcAccountId(newAutoRenewAccount));
		verify(token).setAutoRenewPeriod(newAutoRenewPeriod);
	}

	enum KeyType {
		WIPE, FREEZE, SUPPLY, KYC, ADMIN, EMPTY_ADMIN
	}

	private static EnumSet<KeyType> NO_KEYS = EnumSet.noneOf(KeyType.class);
	private static EnumSet<KeyType> ALL_KEYS = EnumSet.complementOf(EnumSet.of(KeyType.EMPTY_ADMIN));

	private TokenUpdateTransactionBody updateWithCustomFees(CustomFeesOuterClass.CustomFees customFees) {
		return TokenUpdateTransactionBody.newBuilder().setToken(misc).setCustomFees(customFees).build();
	}

	private TokenUpdateTransactionBody updateWith(
			EnumSet<KeyType> keys,
			boolean useNewSymbol,
			boolean useNewName,
			boolean useNewTreasury
	) {
		return updateWith(keys, useNewName, useNewSymbol, useNewTreasury, false, false);
	}

	private TokenUpdateTransactionBody updateWith(
			EnumSet<KeyType> keys,
			boolean useNewSymbol,
			boolean useNewName,
			boolean useNewTreasury,
			boolean useNewAutoRenewAccount,
			boolean useNewAutoRenewPeriod
	) {
		return updateWith(
				keys,
				useNewSymbol,
				useNewName,
				useNewTreasury,
				useNewAutoRenewAccount,
				useNewAutoRenewPeriod,
				false,
				false);
	}

	private TokenUpdateTransactionBody updateWith(
			EnumSet<KeyType> keys,
			boolean useNewSymbol,
			boolean useNewName,
			boolean useNewTreasury,
			boolean useNewAutoRenewAccount,
			boolean useNewAutoRenewPeriod,
			boolean setInvalidKeys,
			boolean useNewMemo
	) {
		final var invalidKey = Key.getDefaultInstance();
		final var op = TokenUpdateTransactionBody.newBuilder().setToken(misc);
		if (useNewSymbol) {
			op.setSymbol(newSymbol);
		}
		if (useNewName) {
			op.setName(newName);
		}
		if (useNewMemo) {
			op.setMemo(StringValue.newBuilder().setValue(newMemo).build());
		}
		if (useNewTreasury) {
			op.setTreasury(newTreasury);
		}
		if (useNewAutoRenewAccount) {
			op.setAutoRenewAccount(newAutoRenewAccount);
		}
		if (useNewAutoRenewPeriod) {
			op.setAutoRenewPeriod(enduring(newAutoRenewPeriod));
		}
		for (KeyType key : keys) {
			switch (key) {
				case WIPE:
					op.setWipeKey(setInvalidKeys ? invalidKey : newKey);
					break;
				case FREEZE:
					op.setFreezeKey(setInvalidKeys ? invalidKey : newKey);
					break;
				case SUPPLY:
					op.setSupplyKey(setInvalidKeys ? invalidKey : newKey);
					break;
				case KYC:
					op.setKycKey(setInvalidKeys ? invalidKey : newKey);
					break;
				case ADMIN:
					op.setAdminKey(setInvalidKeys ? invalidKey : newKey);
					break;
				case EMPTY_ADMIN:
					op.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
					break;
			}
		}
		return op.build();
	}

	private void givenUpdateTarget(EnumSet<KeyType> keys) {
		if (keys.contains(KeyType.WIPE)) {
			given(token.hasWipeKey()).willReturn(true);
		}
		if (keys.contains(KeyType.FREEZE)) {
			given(token.hasFreezeKey()).willReturn(true);
		}
		if (keys.contains(KeyType.SUPPLY)) {
			given(token.hasSupplyKey()).willReturn(true);
		}
		if (keys.contains(KeyType.KYC)) {
			given(token.hasKycKey()).willReturn(true);
		}
	}

	@Test
	void understandsPendingCreation() {
		// expect:
		assertFalse(subject.isCreationPending());

		// and when:
		subject.pendingId = misc;

		// expect:
		assertTrue(subject.isCreationPending());
	}

	@Test
	void adjustingRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		final var status = subject.adjustBalance(sponsor, misc, 1);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, status);
	}

	@Test
	void freezingRejectsUnfreezableToken() {
		given(token.freezeKey()).willReturn(Optional.empty());

		// when:
		final var status = subject.freeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY, status);
	}

	@Test
	void grantingRejectsUnknowableToken() {
		given(token.kycKey()).willReturn(Optional.empty());

		// when:
		final var status = subject.grantKyc(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY, status);
	}

	@Test
	public void wipingRejectsDeletedToken() {
		given(token.isDeleted()).willReturn(true);

		// when:
		final var status = subject.wipe(sponsor, misc, adjustment, false);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	public void freezingRejectsDeletedToken() {
		givenTokenWithFreezeKey(true);
		given(token.isDeleted()).willReturn(true);

		// when:
		final var status = subject.freeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	void unfreezingInvalidWithoutFreezeKey() {
		// when:
		final var status = subject.unfreeze(treasury, misc);

		// then:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, status);
	}

	@Test
	void performsValidFreeze() {
		givenTokenWithFreezeKey(false);

		// when:
		subject.freeze(treasury, misc);

		// then:
		verify(tokenRelsLedger).set(treasuryMisc, TokenRelProperty.IS_FROZEN, true);
	}

	private void givenTokenWithFreezeKey(boolean freezeDefault) {
		given(token.freezeKey()).willReturn(Optional.of(TOKEN_TREASURY_KT.asJKeyUnchecked()));
		given(token.accountsAreFrozenByDefault()).willReturn(freezeDefault);
	}

	@Test
	void adjustingRejectsDeletedToken() {
		given(token.isDeleted()).willReturn(true);

		// when:
		final var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	void refusesToAdjustFrozenRelationship() {
		given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(true);
		// when:
		final var status = subject.adjustBalance(treasury, misc, -1);

		// then:
		assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
	}

	@Test
	void refusesToAdjustRevokedKycRelationship() {
		given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(false);
		// when:
		final var status = subject.adjustBalance(treasury, misc, -1);

		// then:
		assertEquals(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN, status);
	}

	@Test
	void refusesInvalidAdjustment() {
		// when:
		final var status = subject.adjustBalance(treasury, misc, -treasuryBalance - 1);

		// then:
		assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
	}

	@Test
	void performsValidAdjustment() {
		// when:
		subject.adjustBalance(treasury, misc, -1);

		// then:
		verify(tokenRelsLedger).set(treasuryMisc, TOKEN_BALANCE, treasuryBalance - 1);
	}

	@Test
	void rollbackReclaimsIdAndClears() {
		// setup:
		subject.pendingId = created;
		subject.pendingCreation = token;

		// when:
		subject.rollbackCreation();

		// then:
		verify(tokens, never()).put(fromTokenId(created), token);
		verify(ids).reclaimLastId();
		// and:
		assertSame(subject.pendingId, HederaTokenStore.NO_PENDING_ID);
		assertNull(subject.pendingCreation);
	}

	@Test
	void commitAndRollbackThrowIseIfNoPendingCreation() {
		// expect:
		assertThrows(IllegalStateException.class, subject::commitCreation);
		assertThrows(IllegalStateException.class, subject::rollbackCreation);
	}

	@Test
	void commitPutsToMapAndClears() {
		// setup:
		subject.pendingId = created;
		subject.pendingCreation = token;

		// when:
		subject.commitCreation();

		// then:
		verify(tokens).put(fromTokenId(created), token);
		// and:
		assertSame(subject.pendingId, HederaTokenStore.NO_PENDING_ID);
		assertNull(subject.pendingCreation);
		// and:
		assertTrue(subject.isKnownTreasury(treasury));
		assertEquals(Set.of(created, misc), subject.knownTreasuries.get(treasury));
	}

	@Test
	void rejectsTooManyFeeSchedules() {
		given(properties.maxCustomFeesAllowed()).willReturn(1);

		// given:
		final var req = fullyValidAttempt().build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEES_LIST_TOO_LONG, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsUnderspecifiedFeeSchedules() {
		// given:
		final var req = fullyValidAttempt().setCustomFees(grpcUnderspecifiedCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_NOT_FULLY_SPECIFIED, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsInvalidFeeCollector() {
		given(accountsLedger.exists(anotherFeeCollector)).willReturn(false);

		// given:
		final var req = fullyValidAttempt().build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(INVALID_CUSTOM_FEE_COLLECTOR, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsMissingTokenDenomination() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// given:
		final var req = fullyValidAttempt().build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(INVALID_TOKEN_ID_IN_CUSTOM_FEES, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsUnassociatedFeeCollector() {
		given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);

		// given:
		final var req = fullyValidAttempt().build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsZeroFractionInFractionalFee() {
		final var req = fullyValidAttempt().setCustomFees(grpcZeroFractionCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_MUST_BE_POSITIVE, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsNegativeFractionInFractionalFee() {
		final var req = fullyValidAttempt().setCustomFees(grpcNegativeFractionCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_MUST_BE_POSITIVE, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsNegativeMaxInFractionalFee() {
		final var req = fullyValidAttempt().setCustomFees(grpcNegativeMaximumCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_MUST_BE_POSITIVE, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsNegativeMinInFractionalFee() {
		final var req = fullyValidAttempt().setCustomFees(grpcNegativeMinimumCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_MUST_BE_POSITIVE, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsNegativeAmountInFixedFee() {
		final var req = fullyValidAttempt().setCustomFees(grpcNegativeFixedCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_MUST_BE_POSITIVE, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void rejectsZeroAmountInFixedFeeOnTokenUpdate() {
		final var op = updateWithCustomFees(grpcZeroFixedCustomFees);

		// when:
		final var result = subject.update(op, CONSENSUS_NOW);

		// expect:
		assertEquals(CUSTOM_FEE_MUST_BE_POSITIVE, result);
	}

	@Test
	void rejectsInvalidFractionInFractionalFee() {
		final var req = fullyValidAttempt().setCustomFees(grpcDivideByZeroCustomFees).build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// expect:
		assertEquals(FRACTION_DIVIDES_BY_ZERO, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	void happyPathWorksWithAutoRenew() {
		// setup:
		final var expected = new MerkleToken(
				CONSENSUS_NOW + autoRenewPeriod,
				totalSupply,
				decimals,
				symbol,
				name,
				freezeDefault,
				accountsKycGrantedByDefault,
				new EntityId(treasury.getShardNum(), treasury.getRealmNum(), treasury.getAccountNum()));
		expected.setAutoRenewAccount(EntityId.fromGrpcAccountId(autoRenewAccount));
		expected.setAutoRenewPeriod(autoRenewPeriod);
		expected.setAdminKey(TOKEN_ADMIN_KT.asJKeyUnchecked());
		expected.setFreezeKey(TOKEN_FREEZE_KT.asJKeyUnchecked());
		expected.setKycKey(TOKEN_KYC_KT.asJKeyUnchecked());
		expected.setWipeKey(MISC_ACCOUNT_KT.asJKeyUnchecked());
		expected.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked());
		expected.setMemo(memo);
		expected.setFeeScheduleFrom(grpcCustomFees);

		// given:
		final var req = fullyValidAttempt()
				.setExpiry(Timestamp.newBuilder().setSeconds(0))
				.setAutoRenewAccount(autoRenewAccount)
				.setAutoRenewPeriod(enduring(autoRenewPeriod))
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(OK, result.getStatus());
		assertEquals(created, result.getCreated().get());
		// and:
		assertEquals(created, subject.pendingId);
		assertEquals(expected, subject.pendingCreation);
	}

	@Test
	void happyPathWorksWithExplicitExpiry() {
		// setup:
		final var expected = new MerkleToken(
				expiry,
				totalSupply,
				decimals,
				symbol,
				name,
				freezeDefault,
				accountsKycGrantedByDefault,
				new EntityId(treasury.getShardNum(), treasury.getRealmNum(), treasury.getAccountNum()));
		expected.setAdminKey(TOKEN_ADMIN_KT.asJKeyUnchecked());
		expected.setFreezeKey(TOKEN_FREEZE_KT.asJKeyUnchecked());
		expected.setKycKey(TOKEN_KYC_KT.asJKeyUnchecked());
		expected.setWipeKey(MISC_ACCOUNT_KT.asJKeyUnchecked());
		expected.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked());
		expected.setMemo(memo);
		expected.setFeeScheduleFrom(CustomFeesOuterClass.CustomFees.getDefaultInstance());

		// given:
		final var req = fullyValidAttempt().clearCustomFees().build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(OK, result.getStatus());
		assertEquals(created, result.getCreated().get());
		// and:
		assertEquals(created, subject.pendingId);
		assertEquals(expected, subject.pendingCreation);
	}

	@Test
	void rejectsInvalidAutoRenewAccount() {
		given(accountsLedger.exists(autoRenewAccount)).willReturn(false);

		// given:
		final var req = fullyValidAttempt()
				.setAutoRenewAccount(autoRenewAccount)
				.setAutoRenewPeriod(enduring(1000L))
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(INVALID_AUTORENEW_ACCOUNT, result.getStatus());
	}

	@Test
	void rejectsMissingTreasury() {
		given(accountsLedger.exists(treasury)).willReturn(false);
		// and:
		final var req = fullyValidAttempt()
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result.getStatus());
	}

	@Test
	void rejectsDeletedTreasuryAccount() {
		given(hederaLedger.isDeleted(treasury)).willReturn(true);

		// and:
		final var req = fullyValidAttempt()
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result.getStatus());
	}

	@Test
	void allowsZeroInitialSupplyAndDecimals() {
		// given:
		final var req = fullyValidAttempt()
				.clearCustomFees()
				.setInitialSupply(0L)
				.setDecimals(0)
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
	}

	@Test
	void allowsToCreateTokenWithTheBiggestAmountInLong() {
		// given:
		final var req = fullyValidAttempt()
				.clearCustomFees()
				.setInitialSupply(9)
				.setDecimals(18)
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
	}

	@Test
	void forcesToTrueAccountsKycGrantedByDefaultWithoutKycKey() {
		// given:
		final var req = fullyValidAttempt()
				.clearCustomFees()
				.clearKycKey()
				.build();

		// when:
		final var result = subject.createProvisionally(req, sponsor, CONSENSUS_NOW);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
		assertTrue(subject.pendingCreation.accountsKycGrantedByDefault());
	}

	TokenCreateTransactionBody.Builder fullyValidAttempt() {
		return TokenCreateTransactionBody.newBuilder()
				.setExpiry(Timestamp.newBuilder().setSeconds(expiry))
				.setMemo(memo)
				.setAdminKey(adminKey)
				.setKycKey(kycKey)
				.setFreezeKey(freezeKey)
				.setWipeKey(wipeKey)
				.setSupplyKey(supplyKey)
				.setSymbol(symbol)
				.setName(name)
				.setInitialSupply(totalSupply)
				.setTreasury(treasury)
				.setDecimals(decimals)
				.setFreezeDefault(freezeDefault)
				.setCustomFees(grpcCustomFees);
	}

	private Duration enduring(long secs) {
		return Duration.newBuilder().setSeconds(secs).build();
	}
}
