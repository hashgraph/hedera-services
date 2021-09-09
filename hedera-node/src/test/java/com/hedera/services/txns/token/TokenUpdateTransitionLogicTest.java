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

import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;

class TokenUpdateTransitionLogicTest {
	private final TokenID target = IdUtils.asToken("1.2.666");
	private final NftId nftId = new NftId(target.getShardNum(), target.getRealmNum(), target.getTokenNum(), -1);
	private final AccountID oldTreasuryId = IdUtils.asAccount("1.2.4");
	private final AccountID newTreasuryId = IdUtils.asAccount("1.2.5");
	private final AccountID newAutoRenew = IdUtils.asAccount("5.2.1");
	private final AccountID oldAutoRenew = IdUtils.asAccount("4.2.1");
	private final String symbol = "SYMBOL";
	private final String name = "Name";
	private final JKey adminKey = new JEd25519Key("w/e".getBytes());
	long thisSecond = 1_234_567L;
	private final Instant now = Instant.ofEpochSecond(thisSecond);
	private TransactionBody tokenUpdateTxn;
	private MerkleToken merkleToken;
	private Token token;
	private Account oldTreasury = mock(Account.class);
	private Account newTreasury = mock(Account.class);
	private CopyOnWriteIds treasuryAssociatedTokens = mock(CopyOnWriteIds.class);
	private CopyOnWriteIds newTreasuryAssociatedTokens = mock(CopyOnWriteIds.class);

	private TokenRelationship currentTreasuryRel;


	private OptionValidator validator;
	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private TypedTokenStore tokenStore;
	private AccountStore accountStore;

	private TokenUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TypedTokenStore.class);
		accountStore = mock(AccountStore.class);
		validator = mock(OptionValidator.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);
		merkleToken = mock(MerkleToken.class);
		token = mock(Token.class);

		currentTreasuryRel = mock(TokenRelationship.class);

		withAlwaysValidValidator();

		txnCtx = mock(TransactionContext.class);

		subject = new TokenUpdateTransitionLogic(
				true, validator, txnCtx, tokenStore, accountStore);
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}

	@Test
	void abortsOnInvalidIdForSafety() {
		givenValidTxnCtx(false);
		given(tokenStore.loadToken(any())).willThrow(new InvalidTransactionException(FAIL_INVALID));
		assertFailsWith(() -> subject.doStateTransition(), FAIL_INVALID);
	}

	@Test
	void abortsIfCreationFails() {
		givenValidTxnCtx(false);
		given(tokenStore.loadTokenRelationship(any(), any())).willReturn(currentTreasuryRel);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(false);
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_IS_IMMUTABLE);
	}

	@Test
	void rollsBackNewTreasuryChangesIfUpdateFails() {
		givenValidTxnCtx(true);

		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);

		given(tokenStore.loadTokenRelationship(any(), any())).willReturn(currentTreasuryRel);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
		given(token.getId()).willReturn(new Id(1, 2, 3));
		given(token.getTreasury()).willReturn(oldTreasury);

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any())).willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any())).willReturn(newTreasury);

		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));

		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);

		given(treasuryAssociatedTokens.contains(token.getId())).willReturn(true);
		given(newTreasuryAssociatedTokens.contains(token.getId())).willReturn(true);

		doThrow(new InvalidTransactionException(FAIL_INVALID))
				.when(token)
				.update(any(),
						any(),
						any(),
						any());
		// when:
		assertFailsWith(() -> subject.doStateTransition(), FAIL_INVALID);
		// then:
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
	}

	@Test
	void abortsOnUnassociatedNewTreasury() {
		givenValidTxnCtx(true);

		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);

		given(tokenStore.loadTokenRelationship(any(), any())).willReturn(currentTreasuryRel);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any())).willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any())).willReturn(newTreasury);

		given(treasuryAssociatedTokens.contains(any(Id.class))).willReturn(false);

		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);

		given(token.getId()).willReturn(new Id(1, 2, 3));
		given(token.getTreasury()).willReturn(oldTreasury);
		given(oldTreasury.getId()).willReturn(new Id(110, 14, 17));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
	}

	@Test
	void abortsOnInvalidNewTreasury() {
		givenValidTxnCtx(true);
//		givenToken(true, true);

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any())).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any())).willReturn(oldTreasury);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_ACCOUNT_ID);
	}

	@Test
	void abortsOnDetachedOldAccount() {
		givenValidTxnCtx(true);
		given(tokenStore.loadToken(any())).willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
		assertFailsWith(() -> subject.doStateTransition(), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
	}

	@Test
	void permitsExtendingExpiry() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setName("")
						.setSymbol("")
						.setAutoRenewPeriod(Duration.newBuilder()
								.setSeconds(0)
								.build())
						.build())
				.build();
		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(false);
		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).persistToken(any());
	}

	@Test
	void abortsOnNotSetAdminKey() {
		givenValidTxnCtx(true);
		// and:

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		given(token.getAdminKey()).willReturn(null);
		given(token.hasAdminKey()).willReturn(false);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_IS_IMMUTABLE);
	}

	@Test
	void abortsOnInvalidNewExpiry() {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();

		var builder = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setExpiry(expiry));
		tokenUpdateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(validator.isValidExpiry(expiry)).willReturn(false);

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		// when:
		assertFailsWith(() -> subject.doStateTransition(), INVALID_EXPIRATION_TIME);
	}

	@Test
	void abortsOnAlreadyDeletedToken() {
		givenValidTxnCtx(true);
		// and:
		given(tokenStore.loadToken(any())).willThrow(new InvalidTransactionException(TOKEN_WAS_DELETED));
		// when:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_WAS_DELETED);
	}

	@Test
	void doesntReplaceIdenticalTreasury() {
		givenValidTxnCtx(true, true);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));

		final var oldTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(any(), any())).willReturn(oldTreasuryRel);
		// when:
		subject.doStateTransition();
		verify(oldTreasuryRel, never()).setBalance(0);
	}

	@Test
	void followsHappyPathWithNewTreasury() {
		// setup:
		long oldTreasuryBalance = 1000;
		givenValidTxnCtx(true);

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(token.getId()).willReturn(Id.DEFAULT);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);

		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		given(newTreasury.getId()).willReturn(Id.fromGrpcAccount(newTreasuryId));

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any()))
				.willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any()))
				.willReturn(newTreasury);

		given(treasuryAssociatedTokens.contains(any(Id.class))).willReturn(true);
		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);

		final var oldTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, oldTreasury)).willReturn(oldTreasuryRel);
		given(oldTreasuryRel.getBalance()).willReturn(oldTreasuryBalance);
		final var newTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, newTreasury)).willReturn(newTreasuryRel);
		// and:

		// when:
		subject.doStateTransition();

		// then:
		verify(oldTreasuryRel).setBalance(0);
		verify(newTreasuryRel).setBalance(oldTreasuryBalance);
	}

	@Test
	void followsHappyPathWithNewTreasuryAndZeroBalanceOldTreasury() {
		// setup:
		long oldTreasuryBalance = 0;
		givenValidTxnCtx(true);

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(token.getId()).willReturn(Id.DEFAULT);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
		given(token.hasFreezeKey()).willReturn(true);
		given(token.hasKycKey()).willReturn(true);

		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		given(newTreasury.getId()).willReturn(Id.fromGrpcAccount(newTreasuryId));

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any()))
				.willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any()))
				.willReturn(newTreasury);

		given(treasuryAssociatedTokens.contains(any(Id.class))).willReturn(true);
		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);

		final var oldTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, oldTreasury)).willReturn(oldTreasuryRel);
		given(oldTreasuryRel.getBalance()).willReturn(oldTreasuryBalance);
		final var newTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, newTreasury)).willReturn(newTreasuryRel);

		// when:
		subject.doStateTransition();

		// then:
		verify(oldTreasuryRel, never()).setBalance(0);
		verify(newTreasuryRel, never()).setBalance(oldTreasuryBalance);
		verify(newTreasuryRel).changeFrozenState(anyBoolean());
		verify(newTreasuryRel).changeKycState(anyBoolean());
	}

	@Test
	void followsHappyPathNftWithNewTreasury() {
		// setup:
		long oldTreasuryBalance = 1;
		givenValidTxnCtx(true);

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(token.getId()).willReturn(Id.DEFAULT);
		given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		given(oldTreasury.getOwnedNfts()).willReturn(oldTreasuryBalance);
		given(newTreasury.getId()).willReturn(Id.fromGrpcAccount(newTreasuryId));


		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any()))
				.willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any()))
				.willReturn(newTreasury);

		given(treasuryAssociatedTokens.contains(any(Id.class))).willReturn(true);
		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);

		final var oldTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, oldTreasury)).willReturn(oldTreasuryRel);
		given(oldTreasuryRel.getBalance()).willReturn(oldTreasuryBalance);
		given(oldTreasuryRel.getAccount()).willReturn(oldTreasury);
		final var newTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, newTreasury)).willReturn(newTreasuryRel);
		given(newTreasuryRel.getAccount()).willReturn(newTreasury);
		// when:
		subject.doStateTransition();

		// then:
		verify(oldTreasury).setOwnedNfts(0);
		verify(newTreasury).setOwnedNfts(1);
		verify(oldTreasuryRel).setBalance(0);
		verify(newTreasuryRel).setBalance(1);
	}

	@Test
	void doesntGrantKycOrUnfreezeNewTreasuryIfNoKeyIsPresent() {
		givenValidTxnCtx(true);
		// and:

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(token.getId()).willReturn(Id.DEFAULT);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);

		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		given(newTreasury.getId()).willReturn(Id.fromGrpcAccount(newTreasuryId));

		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any()))
				.willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any()))
				.willReturn(newTreasury);

		given(treasuryAssociatedTokens.contains(any(Id.class))).willReturn(true);
		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		// when:
		final var oldTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, oldTreasury)).willReturn(oldTreasuryRel);
		given(oldTreasuryRel.getAccount()).willReturn(oldTreasury);
		final var newTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, newTreasury)).willReturn(newTreasuryRel);
		given(newTreasuryRel.getAccount()).willReturn(newTreasury);

		subject.doStateTransition();

		// then:
		verify(newTreasuryRel, never()).changeFrozenState(anyBoolean());
		verify(newTreasuryRel, never()).changeKycState(anyBoolean());
		// and:
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsExcessiveMemo() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsMissingToken() {
		givenMissingToken();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsTooLongSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// expect:
		assertEquals(INVALID_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsTooLongName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidAdminKey() {
		givenInvalidAdminKey();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidKycKey() {
		givenInvalidKycKey();

		// expect:
		assertEquals(INVALID_KYC_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidWipeKey() {
		givenInvalidWipeKey();

		// expect:
		assertEquals(INVALID_WIPE_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidSupplyKey() {
		givenInvalidSupplyKey();

		// expect:
		assertEquals(INVALID_SUPPLY_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidMemo() {
		givenValidTxnCtx();
		given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);

		// expect:
		assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsInvalidFreezeKey() {
		givenInvalidFreezeKey();

		// expect:
		assertEquals(INVALID_FREEZE_KEY, subject.semanticCheck().apply(tokenUpdateTxn));
	}

	@Test
	void rejectsTreasuryUpdateIfNonzeroBalanceForUnique() {
		// setup:
		subject = new TokenUpdateTransitionLogic(
				false, validator, txnCtx, tokenStore, accountStore);

		givenValidTxnCtx(true);
		long oldTreasuryBalance = 1;

		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.hasAdminKey()).willReturn(true);
		given(token.getTreasury()).willReturn(oldTreasury);
		given(token.getId()).willReturn(Id.DEFAULT);
		given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

		given(oldTreasury.getId()).willReturn(Id.fromGrpcAccount(oldTreasuryId));
		given(oldTreasury.getOwnedNfts()).willReturn(oldTreasuryBalance);
		given(newTreasury.getId()).willReturn(Id.fromGrpcAccount(newTreasuryId));


		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(oldTreasuryId)), any()))
				.willReturn(oldTreasury);
		given(accountStore.loadAccountOrFailWith(eq(Id.fromGrpcAccount(newTreasuryId)), any()))
				.willReturn(newTreasury);

		given(treasuryAssociatedTokens.contains(any(Id.class))).willReturn(true);
		given(oldTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);
		given(newTreasury.getAssociatedTokens()).willReturn(treasuryAssociatedTokens);

		final var oldTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, oldTreasury)).willReturn(oldTreasuryRel);
		given(oldTreasuryRel.getBalance()).willReturn(oldTreasuryBalance);
		given(oldTreasuryRel.getAccount()).willReturn(oldTreasury);
		final var newTreasuryRel = mock(TokenRelationship.class);
		given(tokenStore.loadTokenRelationship(token, newTreasury)).willReturn(newTreasuryRel);
		given(newTreasuryRel.getAccount()).willReturn(newTreasury);
		// when:
		doThrow(new InvalidTransactionException(CURRENT_TREASURY_STILL_OWNS_NFTS)).when(token).update(any(), any(), any(), any());
		assertFailsWith(() -> subject.doStateTransition(), CURRENT_TREASURY_STILL_OWNS_NFTS);
		// then:
		verify(oldTreasury, never()).setOwnedNfts(0);
		verify(newTreasury, never()).setOwnedNfts(1);
		verify(oldTreasuryRel, never()).setBalance(0);
		verify(newTreasuryRel, never()).setBalance(1);

		// then:
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(false);
	}

	private void givenToken(boolean hasKyc, boolean hasFreeze) {
		givenToken(hasKyc, hasFreeze, false);
	}

	private void givenToken(boolean hasKyc, boolean hasFreeze, boolean isUnique) {
		given(merkleToken.hasKycKey()).willReturn(hasKyc);
		given(merkleToken.hasFreezeKey()).willReturn(hasFreeze);
		if (isUnique) {
			given(merkleToken.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
		} else {
			given(merkleToken.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
		}
	}

	private void givenValidTxnCtx(boolean withNewTreasury) {
		givenValidTxnCtx(withNewTreasury, false);
	}

	private void givenValidTxnCtx(boolean withNewTreasury, boolean useDuplicateTreasury) {
		var builder = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setSymbol(symbol)
						.setAutoRenewAccount(newAutoRenew)
						.setName(name)
						.setMemo(StringValue.newBuilder().setValue("FATALITY").build())
						.setToken(target));
		if (withNewTreasury) {
			builder.getTokenUpdateBuilder()
					.setTreasury(useDuplicateTreasury ? oldTreasuryId : newTreasuryId);
		}
		tokenUpdateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(ledger.exists(newTreasuryId)).willReturn(true);
		given(ledger.exists(newAutoRenew)).willReturn(true);
		given(ledger.isDeleted(newTreasuryId)).willReturn(false);
		given(ledger.exists(oldTreasuryId)).willReturn(true);
		given(ledger.isDeleted(oldTreasuryId)).willReturn(false);
		given(ledger.isDetached(newTreasuryId)).willReturn(false);
	}

	private void givenMissingToken() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder())
				.build();
	}

	private void givenInvalidFreezeKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setFreezeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidAdminKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setAdminKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidWipeKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setWipeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidSupplyKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setSupplyKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidKycKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
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
