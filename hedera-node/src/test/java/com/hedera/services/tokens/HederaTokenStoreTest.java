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
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.ledger.properties.TokenScopedPropertyValue;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_FROZEN;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CARELESS_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;

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
	long tokenFloat = 1_000_000;
	long adjustment = 1;
	int divisibility = 10;
	TokenID misc = IdUtils.asToken("3.2.1");
	TokenRef miscRef = IdUtils.asIdRef(misc);
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
	int MAX_TOKEN_SYMBOL_LENGTH = 10;
	int MAX_TOKEN_NAME_LENGTH = 100;

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

		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(sponsor)).willReturn(created);

		account = mock(MerkleAccount.class);

		hederaLedger = mock(HederaLedger.class);

		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
		tokenRelsLedger = (TransactionalLedger<Map.Entry<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>)
				mock(TransactionalLedger.class);
		given(accountsLedger.exists(treasury)).willReturn(true);
		given(accountsLedger.exists(autoRenewAccount)).willReturn(true);
		given(accountsLedger.exists(newAutoRenewAccount)).willReturn(true);
		given(accountsLedger.exists(sponsor)).willReturn(true);
		given(accountsLedger.get(treasury, IS_DELETED)).willReturn(false);
		given(accountsLedger.get(autoRenewAccount, IS_DELETED)).willReturn(false);
		given(accountsLedger.get(newAutoRenewAccount, IS_DELETED)).willReturn(false);
		given(accountsLedger.getTokenRef(treasury)).willReturn(account);

		tokens = (FCMap<MerkleEntityId, MerkleToken>) mock(FCMap.class);
		given(tokens.get(fromTokenId(created))).willReturn(token);
		given(tokens.containsKey(fromTokenId(misc))).willReturn(true);
		given(tokens.get(fromTokenId(misc))).willReturn(token);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(modifiableToken);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxTokensPerAccount()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(properties.maxTokenSymbolLength()).willReturn(MAX_TOKEN_SYMBOL_LENGTH);
		given(properties.maxTokensNameLength()).willReturn(MAX_TOKEN_NAME_LENGTH);

		subject = new HederaTokenStore(ids, TEST_VALIDATOR, properties, () -> tokens, tokenRelsLedger);
		subject.setAccountsLedger(accountsLedger);
		subject.setHederaLedger(hederaLedger);
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
		var outcome = subject.delete(miscRef);

		// then:
		assertEquals(OK, outcome);
	}

	@Test
	public void rejectsDeletionMissingAdminKey() {
		// given:
		given(token.adminKey()).willReturn(Optional.empty());

		// when:
		var outcome = subject.delete(miscRef);

		// then:
		assertEquals(TOKEN_IS_IMMUTABlE, outcome);
	}

	@Test
	public void rejectsMissingDeletion() {
		// given:
		var mockSubject = mock(TokenStore.class);

		given(mockSubject.resolve(miscRef)).willReturn(TokenStore.MISSING_TOKEN);
		willCallRealMethod().given(mockSubject).delete(miscRef);

		// when:
		var outcome = mockSubject.delete(miscRef);

		// then:
		assertEquals(INVALID_TOKEN_REF, outcome);
		verify(mockSubject, never()).apply(any(), any());
	}

	@Test
	public void getDelegates() {
		// expect:
		assertSame(token, subject.get(misc));
	}

	@Test
	public void throwsIseIfSymbolMissing() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.lookup("nope"));
	}

	@Test
	public void nameExistsReturnsFalseWhenNonExisting() {
		// expect:
		assertFalse(subject.nameExists("non-existing"));
	}

	@Test
	public void doesntIncludesPendingInSymbolLookup() {
		// setup:
		var aToken = mock(MerkleToken.class);
		subject.pendingCreation = aToken;
		subject.pendingId = pending;

		given(aToken.symbol()).willReturn(symbol);

		// expect:
		assertFalse(subject.symbolExists(symbol));
	}

	@Test
	public void doesntIncludesPendingInNameExists() {
		// setup:
		var aToken = mock(MerkleToken.class);
		subject.pendingCreation = aToken;
		subject.pendingId = pending;

		given(aToken.name()).willReturn(name);

		// expect:
		assertFalse(subject.nameExists(name));
	}

	@Test
	public void initializesLookupTables() {
		// setup:
		var aToken = mock(MerkleToken.class);
		var bToken = mock(MerkleToken.class);
		// and:
		tokens = new FCMap<>();
		tokens.put(fromTokenId(misc), aToken);
		tokens.put(fromTokenId(pending), bToken);

		given(aToken.symbol()).willReturn("misc");
		given(bToken.symbol()).willReturn("pending");
		given(aToken.name()).willReturn("name1");
		given(bToken.name()).willReturn("name2");

		// when:
		subject = new HederaTokenStore(ids, TEST_VALIDATOR, properties, () -> tokens, tokenRelsLedger);

		// then:
		assertEquals(2, subject.symbolKeyedIds.size());
		assertEquals(misc, subject.lookup("misc"));
		assertEquals(pending, subject.lookup("pending"));

		assertEquals(2, subject.nameKeyedIds.size());
		assertTrue(subject.nameExists("name1"));
		assertTrue(subject.nameExists("name2"));
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
		var status = subject.associate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(TOKEN_WAS_DELETED, status);
	}

	@Test
	public void associatingRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		var status = subject.associate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(INVALID_TOKEN_REF, status);
	}

	@Test
	public void associatingRejectsMissingAccounts() {
		given(accountsLedger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.associate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void dissociatingRejectsUnassociatedTokens() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.isAssociatedWith(misc)).willReturn(false);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.dissociate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
	}

	@Test
	public void associatingRejectsAlreadyAssociatedTokens() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.isAssociatedWith(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.associate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
	}

	@Test
	public void associatingRejectsIfCappedAssociationsEvenAfterPurging() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		given(tokens.isAssociatedWith(misc)).willReturn(false);
		given(tokens.purge(any(), any())).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.associate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		verify(tokens, never()).associate(misc);
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
	}

	@Test
	public void associatingHappyPathWorks() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		var key = BackingTokenRels.asTokenRel(sponsor, misc);

		given(tokens.isAssociatedWith(misc)).willReturn(false);
		given(tokens.purge(any(), any())).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);
		// and:
		given(token.hasKycKey()).willReturn(true);
		given(token.hasFreezeKey()).willReturn(true);
		given(token.accountsAreFrozenByDefault()).willReturn(true);

		// when:
		var status = subject.associate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).associate(misc);
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).create(key);
		verify(tokenRelsLedger).set(key, TokenRelProperty.IS_FROZEN, true);
		verify(tokenRelsLedger).set(key, TokenRelProperty.IS_KYC_GRANTED, false);
	}

	@Test
	public void dissociatingHappyPathWorks() {
		// setup:
		var tokens = mock(MerkleAccountTokens.class);
		var key = BackingTokenRels.asTokenRel(sponsor, misc);

		given(tokens.isAssociatedWith(misc)).willReturn(true);
		given(hederaLedger.getAssociatedTokens(sponsor)).willReturn(tokens);

		// when:
		var status = subject.dissociate(sponsor, List.of(miscRef));

		// expect:
		assertEquals(OK, status);
		// and:
		verify(tokens).disassociate(misc);
		verify(hederaLedger).setAssociatedTokens(sponsor, tokens);
		verify(tokenRelsLedger).destroy(key);
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
		given(accountsLedger.getTokenRef(sponsor)).willReturn(account);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(account.getTokenBalance(misc)).willReturn(balance);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, true);

		// expect:
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -adjustment);
	}

	@Test
	public void wipingWorksWithoutWipeKeyIfCheckSkipped() {
		// setup:
		long balance = 1_234L;
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);
		given(token.hasWipeKey()).willReturn(false);
		given(accountsLedger.getTokenRef(sponsor)).willReturn(account);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(account.getTokenBalance(misc)).willReturn(balance);
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, true);

		// expect:
		assertEquals(OK, status);
		verify(hederaLedger).updateTokenXfers(misc, sponsor, -adjustment);
		verify(token).adjustFloatBy(-adjustment);
		verify(accountsLedger).set(argThat(sponsor::equals), argThat(BALANCE::equals), captor.capture());
		// and:
		assertEquals(misc, captor.getValue().id());
		assertSame(token, captor.getValue().token());
		assertEquals(-adjustment, (long) captor.getValue().value());
	}

	@Test
	public void wipingUpdatesTokenXfersAsExpected() {
		// setup:
		long balance = 1_234L;
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);
		given(token.hasWipeKey()).willReturn(true);
		given(accountsLedger.getTokenRef(sponsor)).willReturn(account);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// and:
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.getTokenBalance(misc)).willReturn(balance);

		// when:
		var status = subject.wipe(sponsor, misc, adjustment, false);

		// expect:
		assertEquals(OK, status);
		// and:
		verify(hederaLedger).updateTokenXfers(misc, sponsor, -adjustment);
		verify(token).adjustFloatBy(-adjustment);
		verify(accountsLedger).set(argThat(sponsor::equals), argThat(BALANCE::equals), captor.capture());
		// and:
		assertEquals(misc, captor.getValue().id());
		assertSame(token, captor.getValue().token());
		assertEquals(-adjustment, (long) captor.getValue().value());
	}

	@Test
	public void wipingFailsWithInvalidWipingAmount() {
		// setup:
		long balance = 1_234L;
		long wipe = 1_235L;

		given(token.hasWipeKey()).willReturn(true);
		given(accountsLedger.getTokenRef(sponsor)).willReturn(account);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.getTokenBalance(misc)).willReturn(balance);

		// when:
		var status = subject.wipe(sponsor, misc, wipe, false);

		// expect:
		assertEquals(INVALID_WIPING_AMOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -wipe);
	}

	@Test
	public void wipingFailsWithNegativeWipingAmount() {
		// setup:
		long balance = 1_234L;
		long wipe = -1_111L;

		given(token.hasWipeKey()).willReturn(true);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));

		// when:
		var status = subject.wipe(sponsor, misc, wipe, false);

		// expect:
		assertEquals(INVALID_WIPING_AMOUNT, status);
		verify(hederaLedger, never()).updateTokenXfers(misc, sponsor, -wipe);
	}

	@Test
	public void wipingFailsWithZeroWipingAmount() {
		// setup:
		long wipe = 0;

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
		op = op.toBuilder().setExpiry(expiry - 1).build();

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
		assertEquals(TOKEN_IS_IMMUTABlE, outcome);
	}

	@Test
	public void canExtendImmutableExpiry() {
		given(token.hasAdminKey()).willReturn(false);
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// given:
		var op = updateWith(NO_KEYS, false, false, false);
		op = op.toBuilder().setExpiry(expiry + 1_234).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(OK, outcome);
	}

	@Test
	public void updateRejectsInvalidSymbol() {
		// given:
		var op = updateWith(NO_KEYS, true, false, false);
		op = op.toBuilder().setSymbol("notok").build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_TOKEN_SYMBOL, outcome);
	}

	@Test
	public void updateRejectsTokenNameTooLong() {
		// setup:
		String tooLongName = IntStream.range(0, MAX_TOKEN_NAME_LENGTH + 1)
				.mapToObj(ignore -> "A")
				.collect(Collectors.joining(""));
		// given:
		var op = updateWith(NO_KEYS, true, false, false);
		op = op.toBuilder().setName(tooLongName).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(TOKEN_NAME_TOO_LONG, outcome);
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
		op = op.toBuilder().setAutoRenewPeriod(-1L).build();

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
		assertEquals(INVALID_TOKEN_REF, outcome);
	}

	@Test
	public void updateRejectsBadAdminKey() {
		givenUpdateTarget(NO_KEYS);
		// and:
		var op = updateWith(EnumSet.of(KeyType.ADMIN), false, false, false, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_ADMIN_KEY, outcome);
	}

	@Test
	public void updateRejectsBadKycKey() {
		givenUpdateTarget(EnumSet.of(KeyType.KYC));
		// and:
		var op = updateWith(EnumSet.of(KeyType.KYC), false, false, false, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_KYC_KEY, outcome);
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
	public void updateRejectsBadWipeKey() {
		givenUpdateTarget(EnumSet.of(KeyType.WIPE));
		// and:
		var op = updateWith(EnumSet.of(KeyType.WIPE), false, false, false, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_WIPE_KEY, outcome);
	}

	@Test
	public void updateRejectsBadSupplyKey() {
		givenUpdateTarget(EnumSet.of(KeyType.SUPPLY));
		// and:
		var op = updateWith(EnumSet.of(KeyType.SUPPLY), false, false, false, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_SUPPLY_KEY, outcome);
	}

	@Test
	public void updateRejectsBadFreezeKey() {
		givenUpdateTarget(EnumSet.of(KeyType.FREEZE));
		// and:
		var op = updateWith(EnumSet.of(KeyType.FREEZE), false, false, false, true);

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(INVALID_FREEZE_KEY, outcome);
	}

	@Test
	public void updateHappyPathIgnoresZeroExpiry() {
		// setup:
		subject.symbolKeyedIds.put(symbol, misc);
		subject.nameKeyedIds.put(name, misc);

		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);
		op = op.toBuilder().setExpiry(0).build();

		// when:
		var outcome = subject.update(op, thisSecond);

		// then:
		assertEquals(OK, outcome);
		verify(token, never()).setExpiry(anyLong());

	}

	@Test
	public void updateHappyPathWorksForEverythingWithNewExpiry() {
		// setup:
		subject.symbolKeyedIds.put(symbol, misc);

		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);
		// and:
		givenUpdateTarget(ALL_KEYS);
		// and:
		var op = updateWith(ALL_KEYS, true, true, true);
		op = op.toBuilder().setExpiry(newExpiry).build();

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
		assertFalse(subject.symbolKeyedIds.containsKey(symbol));
		assertEquals(subject.symbolKeyedIds.get(newSymbol), misc);
		assertFalse(subject.nameKeyedIds.containsKey(name));
		assertEquals(subject.nameKeyedIds.get(newName), misc);
	}

	@Test
	public void updateHappyPathWorksWithNewAutoRenewAccount() {
		// setup:
		subject.symbolKeyedIds.put(symbol, misc);

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
		WIPE, FREEZE, SUPPLY, KYC, ADMIN
	}

	private static EnumSet<KeyType> NO_KEYS = EnumSet.noneOf(KeyType.class);
	private static EnumSet<KeyType> ALL_KEYS = EnumSet.allOf(KeyType.class);

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
			boolean setInvalidKeys
	) {
		return updateWith(keys, useNewSymbol, useNewName, useNewTreasury, false, false, setInvalidKeys);
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
		var op = TokenUpdateTransactionBody.newBuilder().setToken(miscRef);
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
			op.setAutoRenewPeriod(newAutoRenewPeriod);
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

	private void givenUpdateTargetRenewal() {
		given(token.hasAutoRenewAccount()).willReturn(true);
		given(token.autoRenewAccount()).willReturn(EntityId.ofNullableAccountId(autoRenewAccount));
		given(token.autoRenewPeriod()).willReturn(autoRenewPeriod);
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
	public void burningRejectsNegativeAmount() {
		given(token.hasSupplyKey()).willReturn(true);

		// when:
		var status = subject.burn(misc, -1L);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT, status);
	}

	@Test
	public void mintingRejectsInvalidNewSupply() {
		long halfwayToOverflow = (1L << 62) / 2;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.tokenFloat()).willReturn(halfwayToOverflow);
		given(token.divisibility()).willReturn(1);

		// when:
		var status = subject.mint(misc, halfwayToOverflow);

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
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);
		long oldSupply = 123;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.tokenFloat()).willReturn(oldSupply);
		given(token.divisibility()).willReturn(1);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, -oldSupply * 10)).willReturn(OK);
		// and:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.burn(misc, oldSupply);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		// and:
		verify(accountsLedger).set(argThat(treasury::equals), argThat(BALANCE::equals), captor.capture());
		// and:
		assertEquals(misc, captor.getValue().id());
		assertSame(token, captor.getValue().token());
		assertEquals(-oldSupply * 10, (long) captor.getValue().value());
		// and:
		verify(token).adjustFloatBy(-oldSupply);
		// and:
		verify(hederaLedger).updateTokenXfers(misc, treasury, -oldSupply * 10);
	}

	@Test
	public void validMintChangesTokenSupplyAndAdjustsTreasury() {
		// setup:
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);
		long oldFloat = 1_000;
		long adjustment = 500;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.tokenFloat()).willReturn(oldFloat);
		given(token.divisibility()).willReturn(1);
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(treasury));
		// and:
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, adjustment * 10)).willReturn(OK);
		// and:
		given(tokens.getForModify(fromTokenId(misc))).willReturn(token);

		// when:
		var status = subject.mint(misc, adjustment);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		// and:
		verify(accountsLedger).set(argThat(treasury::equals), argThat(BALANCE::equals), captor.capture());
		// and:
		assertEquals(misc, captor.getValue().id());
		assertSame(token, captor.getValue().token());
		assertEquals(adjustment * 10, (long) captor.getValue().value());
		// and:
		verify(tokens).getForModify(fromTokenId(misc));
		verify(token).adjustFloatBy(adjustment);
		verify(tokens).replace(fromTokenId(misc), token);
		// and:
		verify(hederaLedger).updateTokenXfers(misc, treasury, adjustment * 10);
	}


	@Test
	public void burningRejectsInvalidNewSupply() {
		long halfwayToOverflow = (1L << 62) / 2;

		given(token.hasSupplyKey()).willReturn(true);
		given(token.tokenFloat()).willReturn(halfwayToOverflow);
		given(token.divisibility()).willReturn(1);

		// when:
		var status = subject.burn(misc, halfwayToOverflow + 1);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT, status);
	}

	@Test
	public void grantingRejectsSaturatedAccountIfExplicitGrantRequired() {
		givenTokenWithKycKey(false);
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.grantKyc(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
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
	public void freezingPermitsSaturatedAccountIfNoExplicitFreezeRequired() {
		givenTokenWithFreezeKey(true);
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.freeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void unfreezingInvalidWithoutFreezeKey() {
		// when:
		var status = subject.unfreeze(treasury, misc);

		// then:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, status);
	}


	@Test
	public void unfreezingRejectsSaturatedAccountIfExplicitUnfreezeRequired() {
		givenTokenWithFreezeKey(true);
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.unfreeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void freezingPermitsForSaturatedAccountIfFreezeIsImplicit() {
		givenTokenWithFreezeKey(true);
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.freeze(treasury, misc);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void performsValidFreeze() {
		// setup:
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);

		givenTokenWithFreezeKey(false);
		given(account.hasRelationshipWith(misc)).willReturn(true);

		// when:
		subject.freeze(treasury, misc);

		// then:
		verify(accountsLedger).set(argThat(treasury::equals), argThat(IS_FROZEN::equals), captor.capture());
		// and:
		assertEquals(misc, captor.getValue().id());
		assertSame(token, captor.getValue().token());
		assertEquals(true, (boolean) captor.getValue().value());
	}

	private void givenTokenWithFreezeKey(boolean freezeDefault) {
		given(token.freezeKey()).willReturn(Optional.of(CARELESS_SIGNING_PAYER_KT.asJKeyUnchecked()));
		given(token.accountsAreFrozenByDefault()).willReturn(freezeDefault);
	}

	private void givenTokenWithKycKey(boolean accountsKycGrantedByDefault) {
		given(token.kycKey()).willReturn(Optional.of(CARELESS_SIGNING_PAYER_KT.asJKeyUnchecked()));
		given(token.accountsKycGrantedByDefault()).willReturn(accountsKycGrantedByDefault);
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
	public void adjustingRejectsSaturatedAccount() {
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void allowsNewTokenForUndersaturatedAccount() {
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);
		given(account.validityOfAdjustment(misc, token, 1)).willReturn(OK);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(OK, status);
		verify(account, never()).hasRelationshipWith(misc);
	}

	@Test
	public void allowsAdjustingOldRelationships() {
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, 1)).willReturn(OK);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(OK, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void refusesInvalidAdjustment() {
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, -1)).willReturn(SETTING_NEGATIVE_ACCOUNT_BALANCE);

		// when:
		var status = subject.adjustBalance(treasury, misc, -1);

		// then:
		assertEquals(SETTING_NEGATIVE_ACCOUNT_BALANCE, status);
	}

	@Test
	public void performsValidAdjustment() {
		// setup:
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);

		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, -1)).willReturn(OK);
		given(tokens.get(fromTokenId(misc))).willReturn(token);

		// when:
		subject.adjustBalance(treasury, misc, -1);

		// then:
		verify(accountsLedger).set(argThat(treasury::equals), argThat(BALANCE::equals), captor.capture());
		// and:
		assertEquals(misc, captor.getValue().id());
		assertSame(token, captor.getValue().token());
		assertEquals(-1, (long) captor.getValue().value());
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
		assertTrue(subject.symbolKeyedIds.containsKey(symbol));
		assertTrue(subject.nameKeyedIds.containsKey(name));
		assertEquals(created, subject.symbolKeyedIds.get(symbol));
		assertEquals(created, subject.nameKeyedIds.get(name));
	}

	@Test
	public void happyPathWorksWithAutoRenew() {
		// setup:
		var expected = new MerkleToken(
				thisSecond + autoRenewPeriod,
				tokenFloat,
				divisibility,
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
				.setExpiry(0)
				.setAutoRenewAccount(autoRenewAccount)
				.setAutoRenewPeriod(autoRenewPeriod)
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
				tokenFloat,
				divisibility,
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
				.setAutoRenewPeriod(1000L)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(INVALID_AUTORENEW_ACCOUNT, result.getStatus());
	}

	@Test
	public void rejectsInvalidExpiry() {
		// given:
		var req = fullyValidAttempt()
				.setExpiry(thisSecond - 1)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(INVALID_EXPIRATION_TIME, result.getStatus());
	}

	@Test
	public void rejectsInvalidAutoRenewPeriod() {
		// given:
		var req = fullyValidAttempt()
				.setAutoRenewAccount(autoRenewAccount)
				.setAutoRenewPeriod(Long.MAX_VALUE)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(INVALID_RENEWAL_PERIOD, result.getStatus());
	}

	@Test
	public void rejectsSymbolTooLong() {
		// given:
		var req = fullyValidAttempt()
				.setSymbol(IntStream.range(0, MAX_TOKEN_SYMBOL_LENGTH + 1)
						.mapToObj(ignore -> "A")
						.collect(Collectors.joining("")))
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG, result.getStatus());
	}

	@Test
	public void rejectsNameTooLong() {
		// given:
		var req = fullyValidAttempt()
				.setName(IntStream.range(0, MAX_TOKEN_NAME_LENGTH + 1)
						.mapToObj(ignore -> "A")
						.collect(Collectors.joining("")))
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_NAME_TOO_LONG, result.getStatus());
	}

	@Test
	public void rejectsDuplicateSymbol() {
		// setup:
		subject.symbolKeyedIds.put("OOPS", misc);

		// given:
		var req = fullyValidAttempt()
				.setSymbol("OOPS")
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_SYMBOL_ALREADY_IN_USE, result.getStatus());
	}

	@Test
	public void rejectsDuplicateTokenName() {
		// setup:
		subject.nameKeyedIds.put("TOKENNAME", misc);

		// given:
		var req = fullyValidAttempt()
				.setSymbol("TOKENNAME")
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_NAME_ALREADY_IN_USE, result.getStatus());
	}

	@Test
	public void rejectsMissingSymbol() {
		// given:
		var req = fullyValidAttempt()
				.clearSymbol()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.MISSING_TOKEN_SYMBOL, result.getStatus());
	}

	@Test
	public void rejectsMissingTokenName() {
		// given:
		var req = fullyValidAttempt()
				.clearName()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.MISSING_TOKEN_NAME, result.getStatus());
	}

	@Test
	public void rejectsNonAlphanumericSymbol() {
		// given:
		var req = fullyValidAttempt()
				.setSymbol("!!!")
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_SYMBOL, result.getStatus());
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
	public void allowsZeroFloatAndDivisibility() {
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
	public void rejectsJustOverflowingFloat() {
		int divisibility = 1;
		long initialFloat = 1L << 62;

		// given:
		var req = fullyValidAttempt()
				.setInitialSupply(initialFloat)
				.setDecimals(divisibility)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_DECIMALS, result.getStatus());
	}

	@Test
	public void rejectsOverflowingFloat() {
		int divisibility = 1 << 30;
		long initialFloat = 1L << 34;

		// given:
		var req = fullyValidAttempt()
				.setInitialSupply(initialFloat)
				.setDecimals(divisibility)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_DECIMALS, result.getStatus());
	}

	@Test
	public void rejectsInvalidDivisibility() {
		// given:
		var req = fullyValidAttempt()
				.setDecimals(1 << 30)
				.setInitialSupply(1L << 34)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_DECIMALS, result.getStatus());
	}

	@Test
	public void rejectsOverflowingDivisibility() {
		// given:
		var req = fullyValidAttempt()
				.setDecimals(19)
				.setInitialSupply(0L)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_DECIMALS, result.getStatus());
	}

	@Test
	public void rejectsInvalidAmountForDivisibility() {
		// given:
		var req = fullyValidAttempt()
				.setDecimals(18)
				.setInitialSupply(10)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_DECIMALS, result.getStatus());
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

	@Test
	public void rejectsFreezeDefaultWithoutFreezeKey() {
		// given:
		var req = fullyValidAttempt()
				.clearFreezeKey()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor, thisSecond);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY, result.getStatus());
	}

	TokenCreateTransactionBody.Builder fullyValidAttempt() {
		return TokenCreateTransactionBody.newBuilder()
				.setExpiry(expiry)
				.setAdminKey(adminKey)
				.setKycKey(kycKey)
				.setFreezeKey(freezeKey)
				.setWipeKey(wipeKey)
				.setSupplyKey(supplyKey)
				.setSymbol(symbol)
				.setName(name)
				.setInitialSupply(tokenFloat)
				.setTreasury(treasury)
				.setDecimals(divisibility)
				.setFreezeDefault(freezeDefault);
	}
}