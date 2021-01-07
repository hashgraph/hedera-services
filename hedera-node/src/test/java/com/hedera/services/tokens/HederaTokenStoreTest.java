package com.hedera.services.tokens;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

@RunWith(JUnitPlatform.class)
class HederaTokenStoreTest {
	long thisSecond = 1_234_567L;

	EntityIdSource ids;
	GlobalDynamicProperties properties;
	FCMap<MerkleEntityId, MerkleToken> tokens;
	TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	TransactionalLedger<Map.Entry<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	HederaLedger hederaLedger;

	MerkleToken token;
	MerkleToken modifiableToken;
	MerkleAccount account;

	Key newKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asKey();
	JKey newFcKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asJKeyUnchecked();
	Key adminKey, kycKey, freezeKey, supplyKey, wipeKey;
	String symbol = "NOTHBAR";
	String newSymbol = "REALLYSOM";
	String name = "TOKENNAME";
	String newName = "NEWNAME";
	long expiry = thisSecond + 1_234_567;
	long newExpiry = thisSecond + 1_432_765;
	long totalSupply = 1_000_000;
	long adjustment = 1;
	int decimals = 10;
	long treasuryBalance = 50_000, sponsorBalance = 1_000;
	TokenID misc = IdUtils.asToken("3.2.1");
	TokenID anotherMisc = IdUtils.asToken("6.4.2");
	boolean freezeDefault = true;
	boolean accountsKycGrantedByDefault = false;
	long autoRenewPeriod = 500_000;
	long newAutoRenewPeriod = 2_000_000;
	AccountID autoRenewAccount = IdUtils.asAccount("1.2.5");
	AccountID newAutoRenewAccount = IdUtils.asAccount("1.2.6");
	AccountID treasury = IdUtils.asAccount("1.2.3");
	AccountID newTreasury = IdUtils.asAccount("3.2.1");
	AccountID sponsor = IdUtils.asAccount("1.2.666");
	TokenID created = IdUtils.asToken("1.2.666666");
	TokenID pending = IdUtils.asToken("1.2.555555");
	int MAX_TOKENS_PER_ACCOUNT = 100;
	int MAX_TOKEN_SYMBOL_UTF8_BYTES = 10;
	int MAX_TOKEN_NAME_UTF8_BYTES = 100;
	Map.Entry<AccountID, TokenID> sponsorMisc = asTokenRel(sponsor, misc);
	Map.Entry<AccountID, TokenID> treasuryMisc = asTokenRel(treasury, misc);

	HederaTokenStore subject;

	@BeforeEach
	public void setup() {
		adminKey = TOKEN_ADMIN_KT.asKey();
		kycKey = TOKEN_KYC_KT.asKey();
		freezeKey = TOKEN_FREEZE_KT.asKey();
		wipeKey = MISC_ACCOUNT_KT.asKey();
		supplyKey = COMPLEX_KEY_ACCOUNT_KT.asKey();

		token = mock(MerkleToken.class);
		modifiableToken = mock(MerkleToken.class);
		given(token.expiry()).willReturn(expiry);
		given(token.symbol()).willReturn(symbol);
		given(token.hasAutoRenewAccount()).willReturn(true);
		given(token.adminKey()).willReturn(Optional.of(TOKEN_ADMIN_KT.asJKeyUnchecked()));
		given(token.name()).willReturn(name);
		given(token.hasAdminKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));

		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(sponsor)).willReturn(created);

		account = mock(MerkleAccount.class);

		hederaLedger = mock(HederaLedger.class);

		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
		given(accountsLedger.exists(treasury)).willReturn(true);
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
		given(tokenRelsLedger.get(treasuryMisc, TOKEN_BALANCE)).willReturn(treasuryBalance);
		given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(false);
		given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(true);

		tokens = (FCMap<MerkleEntityId, MerkleToken>) mock(FCMap.class);
		given(tokens.get(fromTokenId(created))).willReturn(token);
		given(tokens.containsKey(fromTokenId(misc))).willReturn(true);
		given(tokens.get(fromTokenId(misc))).willReturn(token);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(modifiableToken);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxTokensPerAccount()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(properties.maxTokenSymbolUtf8Bytes()).willReturn(MAX_TOKEN_SYMBOL_UTF8_BYTES);
		given(properties.maxTokenNameUtf8Bytes()).willReturn(MAX_TOKEN_NAME_UTF8_BYTES);

		subject = new HederaTokenStore(ids, TEST_VALIDATOR, properties, () -> tokens, tokenRelsLedger);
		subject.setAccountsLedger(accountsLedger);
		subject.setHederaLedger(hederaLedger);
		subject.knownTreasuries.put(treasury, new HashSet<>() {{ add(misc); }});
	}

	@Test
	public void injectsTokenRelsLedger() {
		// expect:
		verify(hederaLedger).setTokenRelsLedger(tokenRelsLedger);
	}

	@Test
	public void applicationRejectsMissing() {
		// setup:
		var change = mock(Consumer.class);

		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));
	}

	@Test
	public void applicationAlwaysReplacesModifiableToken() {
		// setup:
		var change = mock(Consumer.class);
		var key = fromTokenId(misc);

		given(tokens.getForModify(key)).willReturn(token);

		willThrow(IllegalStateException.class).given(change).accept(any());

		// when:
		assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));

		// then:
		verify(tokens).replace(key, token);
	}

	@Test
	public void applicationWorks() {
		// setup:
		var change = mock(Consumer.class);
		// and:
		InOrder inOrder = Mockito.inOrder(change, tokens);

		// when:
		subject.apply(misc, change);

		// then:
		inOrder.verify(tokens).getForModify(fromTokenId(misc));
		inOrder.verify(change).accept(modifiableToken);
		inOrder.verify(tokens).replace(fromTokenId(misc), modifiableToken);
	}

	@Test
	public void deletionWorksAsExpected() {
		// when:
		TokenStore.DELETION.accept(token);

		// then:
		verify(token).setDeleted(true);
	}

	@Test
	public void deletesAsExpected() {
		// given:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var outcome = subject.delete(misc);

		// then:
		assertEquals(OK, outcome);
		// and:
		assertTrue(subject.knownTreasuries.isEmpty());
	}

	@Test
	public void rejectsDeletionMissingAdminKey() {
		// given:
		given(token.adminKey()).willReturn(Optional.empty());

		// when:
		var outcome = subject.delete(misc);

		// then:
		assertEquals(TOKEN_IS_IMMUTABLE, outcome);
	}

	@Test
	public void rejectsDeletionTokenAlreadyDeleted() {
		// given:
		given(token.isDeleted()).willReturn(true);

		// when:
		var outcome = subject.delete(misc);

		// then:
		assertEquals(TOKEN_WAS_DELETED, outcome);
	}

	@Test
	public void rejectsMissingDeletion() {
		// given:
		var mockSubject = mock(TokenStore.class);

		given(mockSubject.resolve(misc)).willReturn(TokenStore.MISSING_TOKEN);
		willCallRealMethod().given(mockSubject).delete(misc);

		// when:
		var outcome = mockSubject.delete(misc);

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
		verify(mockSubject, never()).apply(any(), any());
	}

	@Test
	public void getDelegates() {
		// expect:
		assertSame(token, subject.get(misc));
	}

	@Test
	public void getThrowsIseOnMissing() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.get(misc));
	}

	@Test
	public void getCanReturnPending() {
		// setup:
		subject.pendingId = pending;
		subject.pendingCreation = token;

		// expect:
		assertSame(token, subject.get(pending));
	}

	@Test
	public void existenceCheckIncludesPending() {
		// setup:
		subject.pendingId = pending;

		// expect:
		assertTrue(subject.exists(pending));
	}

	@Test
	public void freezingRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.freeze(sponsor, misc);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void associatingRejectsDeletedTokens() {
		given(token.isDeleted()).willReturn(true);

		// when:
		var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKEN_WAS_DELETED, status);
	}

	@Test
	public void associatingRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(INVALID_TOKEN_ID, status);
	}

	@Test
	public void associatingRejectsMissingAccounts() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void realAssociationsExist() {
		// expect:
		assertTrue(subject.associationExists(sponsor, misc));
	}

	@Test
	public void noAssociationsWithMissingAccounts() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// expect:
		assertFalse(subject.associationExists(sponsor, misc));
	}

	@Test
	public void dissociatingRejectsUnassociatedTokens() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(false);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
	}

	@Test
	public void dissociatingRejectsTreasuryAccount() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		subject = spy(subject);
		given(subject.isTreasuryForToken(sponsor, misc)).willReturn(true);

		// when:
		var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(ACCOUNT_IS_TREASURY, status);
	}

	@Test
	public void dissociatingRejectsFrozenAccount() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		given(tokenRelsLedger.get(sponsorMisc, IS_FROZEN)).willReturn(true);

		// when:
		var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
	}

	@Test
	public void associatingRejectsAlreadyAssociatedTokens() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
	}

	@Test
	public void associatingRejectsIfCappedAssociationsEvenAfterPurging() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.includes(misc)).willReturn(false);
		given(tokens.purge(any(), any())).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.associate(sponsor, List.of(misc));

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		verify(tokens, never()).associateAll(any());
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
	}

	@Test
	public void associatingHappyPathWorks() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(false);
		given(tokens.purge(any(), any())).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(token.hasKycKey()).willReturn(true);
		given(token.hasFreezeKey()).willReturn(true);
		given(token.accountsAreFrozenByDefault()).willReturn(true);

		// when:
		var status = subject.associate(sponsor, List.of(misc));

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
	public void dissociatingHappyPathWorks() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(0L);

		// when:
		var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).dissociateAll(Set.of(misc));
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).destroy(key);
	}

	@Test
	public void dissociatingFailsIfTokenBalanceIsNonzero() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		var key = asTokenRel(sponsor, misc);

		given(tokens.includes(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(tokenRelsLedger.get(key, TOKEN_BALANCE)).willReturn(1L);

		// when:
		var status = subject.dissociate(sponsor, List.of(misc));

		// expect:
		assertEquals(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES, status);
		// and:
		verify(tokens, never()).dissociateAll(Set.of(misc));
		verify(hederaLedger, never()).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger, never()).destroy(key);
	}

	@Test
	public void grantingKycRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.grantKyc(sponsor, misc);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void grantingKycRejectsDeletedAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(true);
		given(hederaLedger.isDeleted(sponsor)).willReturn(true);

		// when:
		var status = subject.grantKyc(sponsor, misc);

		// expect:
		assertEquals(ACCOUNT_DELETED, status);
	}

	@Test
	public void revokingKycRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.revokeKyc(sponsor, misc);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void wipingRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, false);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void wipingRejectsTokenWithNoWipeKey() {
		// when:
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));

		var status = subject.wipe(sponsor, misc, adjustment, false);

		// expect:
		assertEquals(TOKEN_HAS_NO_WIPE_KEY, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -adjustment);
	}

	@Test
	public void wipingRejectsTokenTreasury() {
		long wiping = 3L;

		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(sponsor));

		// when:
		var status = subject.wipe(sponsor, misc, wiping, false);

		// expect:
		assertEquals(CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -wiping);
	}

	@Test
	public void wipingWithoutTokenRelationshipFails() {
		// setup:
		long balance = 1_234L;
		given(token.hasWipeKey()).willReturn(false);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(tokenRelsLedger.exists(sponsorMisc)).willReturn(false);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, true);

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -adjustment);
	}

	@Test
	public void wipingWorksWithoutWipeKeyIfCheckSkipped() {
		// setup:
		given(token.hasWipeKey()).willReturn(false);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, true);

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
	public void wipingUpdatesTokenXfersAsExpected() {
		// setup:
		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, false);

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
	public void wipingFailsWithInvalidWipingAmount() {
		// setup:
		long wipe = 1_235L;

		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));

		// when:
		var status = subject.wipe(sponsor, misc, wipe, false);

		// expect:
		assertEquals(INVALID_WIPING_AMOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -wipe);
	}

	@Test
	public void adjustingRejectsMissingAccount() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.adjustBalance(sponsor, misc, 1);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void updateRejectsInvalidExpiry() {
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// given:
		var op = updateWith(NO_KEYS, true, true, false);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(expiry - 1)).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_EXPIRATION_TIME, outcome);
	}

	@Test
	public void updateRejectsImmutableToken() {
		given(token.hasAdminKey()).willReturn(false);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// given:
		var op = updateWith(NO_KEYS, true, true, false);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(TOKEN_IS_IMMUTABLE, outcome);
	}

	@Test
	public void canExtendImmutableExpiry() {
		given(token.hasAdminKey()).willReturn(false);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// given:
		var op = updateWith(NO_KEYS, false, false, false);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234)).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(OK, outcome);
	}

	@Test
	public void updateRejectsInvalidNewAutoRenew() {
		given(accountsLedger.exists(newAutoRenewAccount)).willReturn(false);
		// and:
		var op = updateWith(NO_KEYS, true, true, false, true, false);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
	}

	@Test
	public void updateRejectsInvalidNewAutoRenewPeriod() {
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		var op = updateWith(NO_KEYS, true, true, false, false, false);
		op = op.toBuilder().setAutoRenewPeriod(enduring(-1L)).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_RENEWAL_PERIOD, outcome);
	}

	@Test
	public void updateRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
	}


	@Test
	public void updateRejectsInappropriateKycKey() {
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(NO_KEYS);
		// and:
		var op = updateWith(EnumSet.of(KeyType.KYC), false, false, false);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(TOKEN_HAS_NO_KYC_KEY, outcome);
	}

	@Test
	public void updateRejectsInappropriateFreezeKey() {
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(NO_KEYS);
		// and:
		var op = updateWith(EnumSet.of(KeyType.FREEZE), false, false, false);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, outcome);
	}

	@Test
	public void updateRejectsInappropriateWipeKey() {
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(NO_KEYS);
		// and:
		var op = updateWith(EnumSet.of(KeyType.WIPE), false, false, false);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(TOKEN_HAS_NO_WIPE_KEY, outcome);
	}

	@Test
	public void updateRejectsInappropriateSupplyKey() {
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(NO_KEYS);
		// and:
		var op = updateWith(EnumSet.of(KeyType.SUPPLY), false, false, false);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, outcome);
	}

	@Test
	public void treasuryRemovalForTokenRemovesKeyWhenEmpty() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc));
		subject.knownTreasuries.put(treasury, tokenSet);

		subject.removeKnownTreasuryForToken(treasury, misc);

		// expect:
		assertFalse(subject.knownTreasuries.containsKey(treasury));
		assertTrue(subject.knownTreasuries.isEmpty());
	}

	@Test
	public void addKnownTreasuryWorks() {
		subject.addKnownTreasury(treasury, misc);

		// expect:
		assertTrue(subject.knownTreasuries.containsKey(treasury));
	}

	@Test
	public void removeKnownTreasuryWorks() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc, anotherMisc));
		subject.knownTreasuries.put(treasury, tokenSet);

		subject.removeKnownTreasuryForToken(treasury, misc);

		// expect:
		assertTrue(subject.knownTreasuries.containsKey(treasury));
		assertEquals(1, subject.knownTreasuries.size());
		assertTrue(subject.knownTreasuries.get(treasury).contains(anotherMisc));
	}

	@Test
	public void isKnownTreasuryWorks() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc));

		subject.knownTreasuries.put(treasury, tokenSet);

		// expect:
		assertTrue(subject.isKnownTreasury(treasury));
	}

	@Test
	public void isTreasuryForTokenWorks() {
		Set<TokenID> tokenSet = new HashSet<>(Arrays.asList(misc));

		subject.knownTreasuries.put(treasury, tokenSet);

		// expect:
		assertTrue(subject.isTreasuryForToken(treasury, misc));
	}

	@Test
	public void isTreasuryForTokenReturnsFalse() {
		// setup:
		subject.knownTreasuries.clear();
		
		// expect:
		assertFalse(subject.isTreasuryForToken(treasury, misc));
	}

	@Test
	public void throwsIfKnownTreasuryIsMissing() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.removeKnownTreasuryForToken(null, misc));
	}

	@Test
	public void throwsIfInvalidTreasury() {
		// setup:
		subject.knownTreasuries.clear();

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.removeKnownTreasuryForToken(treasury, misc));
	}


	@Test
	public void updateHappyPathIgnoresZeroExpiry() {
		// setup:
		subject.addKnownTreasury(treasury, misc);
		Set<TokenID> tokenSet = new HashSet<>();
		tokenSet.add(misc);

		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(0)).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(OK, outcome);
		verify(token, never()).setExpiry(anyLong());
		// and:
		assertFalse(subject.knownTreasuries.containsKey(treasury));
		assertEquals(subject.knownTreasuries.get(newTreasury), tokenSet);
	}

	@Test
	public void updateRemovesAdminKeyWhenAppropos() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		Set<TokenID> tokenSet = new HashSet<>();
		tokenSet.add(misc);

		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(EnumSet.noneOf(KeyType.class));
		// and:
		var op = updateWith(EnumSet.of(KeyType.EMPTY_ADMIN), false, false, false);

		// when:
		var outcome = subject.update(op, thisSecond);
		// then:
		assertEquals(OK, outcome);
		verify(token).setAdminKey(MerkleToken.UNUSED_KEY);
	}

	@Test
	public void updateHappyPathWorksForEverythingWithNewExpiry() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		Set<TokenID> tokenSet = new HashSet<>();
		tokenSet.add(misc);

		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);
		op = op.toBuilder().setExpiry(Timestamp.newBuilder().setSeconds(newExpiry)).build();

		// when:
		var outcome = subject.update(op, thisSecond);
		// then:
		assertEquals(OK, outcome);
		verify(token).setSymbol(newSymbol);
		verify(token).setName(newName);
		verify(token).setExpiry(newExpiry);
		verify(token).setTreasury(EntityId.ofNullableAccountId(newTreasury));
		verify(token).setAdminKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setFreezeKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setKycKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setSupplyKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		verify(token).setWipeKey(argThat((JKey k) -> JKey.equalUpToDecodability(k, newFcKey)));
		// and:
		assertFalse(subject.knownTreasuries.containsKey(treasury));
		assertEquals(subject.knownTreasuries.get(newTreasury), tokenSet);
	}

	@Test
	public void updateHappyPathWorksWithNewAutoRenewAccount() {
		// setup:
		subject.addKnownTreasury(treasury, misc);

		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true, true, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(OK, outcome);
		verify(token).setAutoRenewAccount(EntityId.ofNullableAccountId(newAutoRenewAccount));
		verify(token).setAutoRenewPeriod(newAutoRenewPeriod);
	}

	enum KeyType {
		WIPE, FREEZE, SUPPLY, KYC, ADMIN, EMPTY_ADMIN
	}

	private static EnumSet<KeyType> NO_KEYS = EnumSet.noneOf(KeyType.class);
	private static EnumSet<KeyType> ALL_KEYS = EnumSet.complementOf(EnumSet.of(KeyType.EMPTY_ADMIN));

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
		return updateWith(keys, useNewSymbol, useNewName, useNewTreasury, useNewAutoRenewAccount, useNewAutoRenewPeriod, false);
	}

	private TokenUpdateTransactionBody updateWith(
			EnumSet<KeyType> keys,
			boolean useNewSymbol,
			boolean useNewName,
			boolean useNewTreasury,
			boolean useNewAutoRenewAccount,
			boolean useNewAutoRenewPeriod,
			boolean setInvalidKeys
	) {
		var invalidKey = Key.getDefaultInstance();
		var op = TokenUpdateTransactionBody.newBuilder().setToken(misc);
		if (useNewSymbol) {
			op.setSymbol(newSymbol);
		}
		if (useNewName) {
			op.setName(newName);
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
	public void understandsPendingCreation() {
		// expect:
		assertFalse(subject.isCreationPending());

		// and when:
		subject.pendingId = misc;

		// expect:
		assertTrue(subject.isCreationPending());
	}

	@Test
	public void adjustingRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		var status = subject.adjustBalance(sponsor, misc, 1);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, status);
	}

	@Test
	public void freezingRejectsUnfreezableToken() {
		given(token.freezeKey()).willReturn(Optional.empty());

		// when:
		var status = subject.freeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY, status);
	}

	@Test
	public void grantingRejectsUnknowableToken() {
		given(token.kycKey()).willReturn(Optional.empty());

		// when:
		var status = subject.grantKyc(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY, status);
	}

	@Test
	public void mintingRejectsInvalidToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		var status = subject.mint(misc, 1L);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, status);
	}

	@Test
	public void burningRejectsInvalidToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		var status = subject.burn(misc, 1L);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, status);
	}

	@Test
	public void mintingRejectsFixedSupplyToken() {
		given(token.hasSupplyKey()).willReturn(false);

		// when:
		var status = subject.mint(misc, 1L);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY, status);
	}

	@Test
	public void burningRejectsFixedSupplyToken() {
		given(token.hasSupplyKey()).willReturn(false);

		// when:
		var status = subject.burn(misc, 1L);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY, status);
	}

	@Test
	public void mintingRejectsNegativeMintAmount() {
		given(token.hasSupplyKey()).willReturn(true);

		// when:
		var status = subject.mint(misc, -1L);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT, status);
	}

	@Test
	public void burningRejectsDueToInsufficientFundsInTreasury() {
		given(token.hasSupplyKey()).willReturn(true);
		given(token.totalSupply()).willReturn(treasuryBalance * 2);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));

		// when:
		var status = subject.burn(misc, treasuryBalance + 1);

		// then:
		assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
	}

	@Test
	public void mintingRejectsInvalidNewSupply() {
		long halfwayToOverflow = ((1L << 63) - 1) / 2;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.totalSupply()).willReturn(halfwayToOverflow + 1);

		// when:
		var status = subject.mint(misc, halfwayToOverflow + 1);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT, status);
	}

	@Test
	public void wipingRejectsDeletedToken() {
		given(token.isDeleted()).willReturn(true);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment,false);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	public void mintingRejectsDeletedToken() {
		given(token.isDeleted()).willReturn(true);

		// when:
		var status = subject.mint(misc, 1L);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	public void validBurnChangesTokenSupplyAndAdjustsTreasury() {
		// setup:
		long oldSupply = 123;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.totalSupply()).willReturn(oldSupply);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.burn(misc, oldSupply);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		// and:
		verify(token).adjustTotalSupplyBy(-oldSupply);
		// and:
		verify(hederaLedger).updateTokenXfers(misc, treasury, -oldSupply);
		// and:
		verify(tokenRelsLedger).set(
				argThat(treasuryMisc::equals),
				argThat(TOKEN_BALANCE::equals),
				longThat(l -> l == (treasuryBalance - oldSupply)));
	}

	@Test
	public void validMintChangesTokenSupplyAndAdjustsTreasury() {
		// setup:
		long oldTotalSupply = 1_000;
		long adjustment = 500;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.totalSupply()).willReturn(oldTotalSupply);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.mint(misc, adjustment);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		// and:
		verify(tokens).getForModify(fromTokenId(misc));
		verify(token).adjustTotalSupplyBy(adjustment);
		verify(tokens).replace(fromTokenId(misc), token);
		// and:
		verify(hederaLedger).updateTokenXfers(misc, treasury, adjustment);
		// and:
		verify(tokenRelsLedger).set(
				argThat(treasuryMisc::equals),
				argThat(TOKEN_BALANCE::equals),
				longThat(l -> l == (treasuryBalance + adjustment)));
	}

	@Test
	public void burningRejectsAmountMoreThanFound() {
		long amount = 1;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.totalSupply()).willReturn(amount);
		given(token.decimals()).willReturn(1);

		// when:
		var status = subject.burn(misc, amount + 1);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT, status);
	}

	@Test
	public void freezingRejectsDeletedToken() {
		givenTokenWithFreezeKey(true);
		given(token.isDeleted()).willReturn(true);

		// when:
		var status = subject.freeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	public void unfreezingInvalidWithoutFreezeKey() {
		// when:
		var status = subject.unfreeze(treasury, misc);

		// then:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, status);
	}

	@Test
	public void performsValidFreeze() {
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
	public void adjustingRejectsDeletedToken() {
		given(token.isDeleted()).willReturn(true);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_WAS_DELETED, status);
	}

	@Test
	public void refusesToAdjustFrozenRelationship() {
		given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(true);
		// when:
		var status = subject.adjustBalance(treasury, misc, -1);

		// then:
		assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
	}

	@Test
	public void refusesToAdjustRevokedKycRelationship() {
		given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(false);
		// when:
		var status = subject.adjustBalance(treasury, misc, -1);

		// then:
		assertEquals(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN, status);
	}

	@Test
	public void refusesInvalidAdjustment() {
		// when:
		var status = subject.adjustBalance(treasury, misc, -treasuryBalance - 1);

		// then:
		assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
	}

	@Test
	public void performsValidAdjustment() {
		given(tokens.get(fromTokenId(misc))).willReturn(token);

		// when:
		subject.adjustBalance(treasury, misc, -1);

		// then:
		verify(tokenRelsLedger).set(treasuryMisc, TOKEN_BALANCE, treasuryBalance - 1);
	}

	@Test
	public void rollbackReclaimsIdAndClears() {
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
	public void commitAndRollbackThrowIseIfNoPendingCreation() {
		// expect:
		assertThrows(IllegalStateException.class, subject::commitCreation);
		assertThrows(IllegalStateException.class, subject::rollbackCreation);
	}

	@Test
	public void commitPutsToMapAndClears() {
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
	public void happyPathWorksWithAutoRenew() {
		// setup:
		var expected = new MerkleToken(
				thisSecond + autoRenewPeriod,
				totalSupply,
				decimals,
				symbol,
				name,
				freezeDefault,
				accountsKycGrantedByDefault,
				new EntityId(treasury.getShardNum(), treasury.getRealmNum(), treasury.getAccountNum()));
		expected.setAutoRenewAccount(EntityId.ofNullableAccountId(autoRenewAccount));
		expected.setAutoRenewPeriod(autoRenewPeriod);
		expected.setAdminKey(TOKEN_ADMIN_KT.asJKeyUnchecked());
		expected.setFreezeKey(TOKEN_FREEZE_KT.asJKeyUnchecked());
		expected.setKycKey(TOKEN_KYC_KT.asJKeyUnchecked());
		expected.setWipeKey(MISC_ACCOUNT_KT.asJKeyUnchecked());
		expected.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked());

		// given:
		var req = fullyValidAttempt()
				.setExpiry(Timestamp.newBuilder().setSeconds(0))
				.setAutoRenewAccount(autoRenewAccount)
				.setAutoRenewPeriod(enduring(autoRenewPeriod))
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(OK, result.getStatus());
		assertEquals(created, result.getCreated().get());
		// and:
		assertEquals(created, subject.pendingId);
		assertEquals(expected, subject.pendingCreation);
	}

	@Test
	public void happyPathWorksWithExplicitExpiry() {
		// setup:
		var expected = new MerkleToken(
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

		// given:
		var req = fullyValidAttempt().build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(OK, result.getStatus());
		assertEquals(created, result.getCreated().get());
		// and:
		assertEquals(created, subject.pendingId);
		assertEquals(expected, subject.pendingCreation);
	}

	@Test
	public void rejectsInvalidAutoRenewAccount() {
		given(accountsLedger.exists(autoRenewAccount)).willReturn(false);

		// given:
		var req = fullyValidAttempt()
				.setAutoRenewAccount(autoRenewAccount)
				.setAutoRenewPeriod(enduring(1000L))
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(INVALID_AUTORENEW_ACCOUNT, result.getStatus());
	}

	@Test
	public void rejectsMissingTreasury() {
		given(accountsLedger.exists(treasury)).willReturn(false);
		// and:
		var req = fullyValidAttempt()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result.getStatus());
	}

	@Test
	public void rejectsDeletedTreasuryAccount() {
		given(accountsLedger.get(treasury, IS_DELETED)).willReturn(true);

		// and:
		var req = fullyValidAttempt()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result.getStatus());
	}

	@Test
	public void allowsZeroInitialSupplyAndDecimals() {
		// given:
		var req = fullyValidAttempt()
				.setInitialSupply(0L)
				.setDecimals(0)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
	}

	@Test
	public void allowsToCreateTokenWithTheBiggestAmountInLong() {
		// given:
		var req = fullyValidAttempt()
				.setInitialSupply(9)
				.setDecimals(18)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
	}

	@Test
	public void forcesToTrueAccountsKycGrantedByDefaultWithoutKycKey() {
		// given:
		var req = fullyValidAttempt()
				.clearKycKey()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
		assertTrue(subject.pendingCreation.accountsKycGrantedByDefault());
	}

	TokenCreateTransactionBody.Builder fullyValidAttempt() {
		return TokenCreateTransactionBody.newBuilder()
				.setExpiry(Timestamp.newBuilder().setSeconds(expiry))
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
				.setFreezeDefault(freezeDefault);
	}

	private Duration enduring(long secs) {
		return Duration.newBuilder().setSeconds(secs).build();
	}
}
