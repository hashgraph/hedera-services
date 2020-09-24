package com.hedera.services.ledger;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.NonZeroNetTransfersException;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingTokenRels;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.tokens.HederaTokenStore;
import com.hedera.services.tokens.TokenStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenRefTransferList;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfersTransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.FUNDS_RECEIVED_RECORD_THRESHOLD;
import static com.hedera.services.ledger.properties.AccountProperty.FUNDS_SENT_RECORD_THRESHOLD;
import static com.hedera.services.ledger.properties.AccountProperty.HISTORY_RECORDS;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.PAYER_RECORDS;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.legacy.core.jproto.JKey.mapKey;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.refWith;
import static com.hedera.test.utils.IdUtils.tokenWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.same;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

@RunWith(JUnitPlatform.class)
public class HederaLedgerTest {
	long thisSecond = 1_234_567L;
	final long NEXT_ID = 1_000_000L;
	final long MISC_BALANCE = 1_234L;
	final long RAND_BALANCE = 2_345L;
	final long GENESIS_BALANCE = 50_000_000_000L;
	final long miscFrozenTokenBalance = 500L;
	final HederaAccountCustomizer noopCustomizer = new HederaAccountCustomizer();
	final AccountID misc = AccountID.newBuilder().setAccountNum(1_234).build();
	final AccountID rand = AccountID.newBuilder().setAccountNum(2_345).build();
	final AccountID deleted = AccountID.newBuilder().setAccountNum(3_456).build();
	final AccountID genesis = AccountID.newBuilder().setAccountNum(2).build();

	TokenID frozenId = IdUtils.tokenWith(111);
	MerkleToken frozenToken;
	String frozenSymbol = "FREEZE";
	TokenID tokenId = IdUtils.tokenWith(222);
	MerkleToken token;
	String otherSymbol = "FLOW";
	MerkleAccount account;
	String missingSymbol = "DIE";
	TokenID missingId = IdUtils.tokenWith(333);

	TokenTransfersTransactionBody multipleValidTokenTransfers = TokenTransfersTransactionBody.newBuilder()
			.addTokenTransfers(TokenRefTransferList.newBuilder()
					.setToken(refWith(frozenSymbol))
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.addTokenTransfers(TokenRefTransferList.newBuilder()
					.setToken(refWith(otherSymbol))
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.build();

	TokenTransfersTransactionBody missingSymbolTokenTransfers = TokenTransfersTransactionBody.newBuilder()
			.addTokenTransfers(TokenRefTransferList.newBuilder()
					.setToken(refWith(missingSymbol))
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.build();
	TokenTransfersTransactionBody missingIdTokenTransfers = TokenTransfersTransactionBody.newBuilder()
			.addTokenTransfers(TokenRefTransferList.newBuilder()
					.setToken(refWith(missingId))
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.build();
	TokenTransfersTransactionBody unmatchedTokenTransfers = TokenTransfersTransactionBody.newBuilder()
			.addTokenTransfers(TokenRefTransferList.newBuilder()
					.setToken(refWith(otherSymbol))
					.addAllTransfers(List.of(
							adjustFrom(misc, +2_000),
							adjustFrom(rand, -1_000)
					)))
			.build();

	FCMapBackingAccounts backingAccounts;
	FCMap<MerkleEntityId, MerkleAccount> backingMap;

	HederaLedger subject;

	HederaTokenStore tokenStore;
	EntityIdSource ids;
	ExpiringCreations creator;
	AccountRecordsHistorian historian;
	TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	TransactionalLedger<Map.Entry<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;

	@BeforeEach
	private void setupWithMockLedger() {
		ids = new EntityIdSource() {
			long nextId = NEXT_ID;

			@Override
			public AccountID newAccountId(AccountID newAccountSponsor) {
				return AccountID.newBuilder().setAccountNum(nextId++).build();
			}

			@Override
			public FileID newFileId(AccountID newFileSponsor) {
				return FileID.newBuilder().setFileNum(nextId++).build();
			}

			@Override
			public TokenID newTokenId(AccountID sponsor) {
				return TokenID.newBuilder().setTokenNum(nextId++).build();
			}

			@Override
			public void reclaimLastId() {
				nextId--;
			}
		};

		var freezeKey = new JEd25519Key("w/e".getBytes());

		account = mock(MerkleAccount.class);

		frozenToken = mock(MerkleToken.class);
		given(frozenToken.freezeKey()).willReturn(Optional.of(freezeKey));
		given(frozenToken.accountsAreFrozenByDefault()).willReturn(true);
		token = mock(MerkleToken.class);
		given(token.freezeKey()).willReturn(Optional.empty());

		accountsLedger = mock(TransactionalLedger.class);
		tokenRelsLedger = mock(TransactionalLedger.class);
		creator = mock(ExpiringCreations.class);
		addToLedger(misc, MISC_BALANCE, noopCustomizer, Map.of(
				frozenId,
				new TokenInfo(miscFrozenTokenBalance, frozenToken)));
		addToLedger(rand, RAND_BALANCE, noopCustomizer);
		addToLedger(genesis, GENESIS_BALANCE, noopCustomizer);
		addDeletedAccountToLedger(deleted, noopCustomizer);
		historian = mock(AccountRecordsHistorian.class);

		tokenStore = mock(HederaTokenStore.class);
		given(tokenStore.exists(frozenId)).willReturn(true);
		given(tokenStore.exists(tokenId)).willReturn(true);
		given(tokenStore.exists(missingId)).willReturn(false);
		given(tokenStore.symbolExists(frozenSymbol)).willReturn(true);
		given(tokenStore.lookup(frozenSymbol)).willReturn(frozenId);
		given(tokenStore.symbolExists(otherSymbol)).willReturn(true);
		given(tokenStore.lookup(otherSymbol)).willReturn(tokenId);
		given(tokenStore.symbolExists(missingSymbol)).willReturn(false);
		given(tokenStore.resolve(TokenRef.newBuilder().setSymbol(missingSymbol).build()))
				.willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.resolve(TokenRef.newBuilder().setTokenId(missingId).build()))
				.willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.resolve(TokenRef.newBuilder().setTokenId(frozenId).build()))
				.willReturn(frozenId);
		given(tokenStore.resolve(TokenRef.newBuilder().setSymbol(frozenSymbol).build()))
				.willReturn(frozenId);
		given(tokenStore.resolve(TokenRef.newBuilder().setTokenId(tokenId).build()))
				.willReturn(tokenId);
		given(tokenStore.resolve(TokenRef.newBuilder().setSymbol(otherSymbol).build()))
				.willReturn(tokenId);

		subject = new HederaLedger(tokenStore, ids, creator, historian, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);
	}

	@Test
	public void requiresAllNetZeroTransfers() {
		given(tokenStore.adjustBalance(any(), any(), anyLong()))
				.willAnswer(invocationOnMock -> {
					AccountID aId = invocationOnMock.getArgument(0);
					TokenID tId = invocationOnMock.getArgument(1);
					long amount = invocationOnMock.getArgument(2);
					subject.updateTokenXfers(tId, aId, amount);
					return OK;
				});

		// when:
		var outcome = subject.doAtomicZeroSumTokenTransfers(unmatchedTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, outcome);
		// and:
		assertTrue(netXfers.isEmpty());
	}

	@Test
	public void atomicZeroSumTokenTransferRejectsMissingId() {
		given(tokenStore.adjustBalance(any(), any(), anyLong())).willReturn(OK);

		// when:
		var outcome = subject.doAtomicZeroSumTokenTransfers(missingIdTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
		// and:
		assertTrue(netXfers.isEmpty());
	}

	@Test
	public void atomicZeroSumTokenTransferRejectsMissingSymbol() {
		given(tokenStore.adjustBalance(any(), any(), anyLong())).willReturn(OK);

		// when:
		var outcome = subject.doAtomicZeroSumTokenTransfers(missingSymbolTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
		// and:
		assertTrue(netXfers.isEmpty());
	}

	@Test
	public void happyPathZeroSumTokenTransfers() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		// when:
		var outcome = subject.doAtomicZeroSumTokenTransfers(multipleValidTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(OK, outcome);
		// and:
		assertEquals(frozenId, netXfers.get(0).getToken());
		assertEquals(
				List.of(aa(misc, 1_000), aa(rand, -1_000)),
				netXfers.get(0).getTransfersList());
		assertEquals(tokenId, netXfers.get(1).getToken());
		assertEquals(
				List.of(aa(misc, 1_000), aa(rand, -1_000)),
				netXfers.get(1).getTransfersList());
	}

	@Test
	public void tokenTransferRejectsForMissingId() {
		// setup
		given(tokenStore.exists(tokenId)).willReturn(false);

		// when:
		var outcome = subject.doTokenTransfer(tokenId, misc, rand, 1_000, false);

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
	}

	@Test
	public void tokenTransferSkipTokenCheckWorks() {
		// setup
		given(subject.adjustTokenBalance(misc, tokenId, -1_000)).willReturn(OK);
		given(subject.adjustTokenBalance(rand, tokenId, 1_000)).willReturn(OK);

		// when:
		var outcome = subject.doTokenTransfer(tokenId, misc, rand, 1_000, true);

		// then:
		assertEquals(OK, outcome);
		verify(tokenStore, never()).exists(tokenId);
	}

	@Test
	public void tokenTransferRevertsChangesOnFirstAdjust() {
		// setup
		given(tokenStore.adjustBalance(misc, tokenId, -555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.doTokenTransfer(tokenId, misc, rand, 555, true);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		assertEquals(0, subject.numTouches);
		verify(tokenStore, times(1)).adjustBalance(any(), any(), anyLong());
		verify(tokenRelsLedger).rollback();
	}

	@Test
	public void tokenTransferRevertsChangesOnSecondAdjust() {
		// setup
		given(tokenStore.adjustBalance(misc, tokenId, -555))
				.willReturn(OK);
		given(tokenStore.adjustBalance(rand, tokenId, 555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.doTokenTransfer(tokenId, misc, rand, 555, true);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		assertEquals(0, subject.numTouches);
		verify(tokenStore).adjustBalance(misc, tokenId, -555);
		verify(tokenStore).adjustBalance(rand, tokenId, 555);
		verify(tokenRelsLedger).rollback();
	}

	@Test
	public void tokenTransferHappyPath() {
		// setup
		givenAdjustBalanceUpdatingTokenXfers(misc, tokenId, -555);
		givenAdjustBalanceUpdatingTokenXfers(rand, tokenId, 555);

		// given
		var outcome = subject.doTokenTransfer(tokenId, misc, rand, 555, true);
		var netXfers = subject.netTokenTransfersInTxn();

		assertEquals(OK, outcome);
		assertEquals(tokenId, netXfers.get(0).getToken());
		assertEquals(List.of(aa(misc, -555), aa(rand, 555)),
				netXfers.get(0).getTransfersList());
	}

	@Test
	public void getsTokenBalanceInScope() {
		// given:
		var balance = subject.getTokenBalance(misc, frozenId);

		// expect:
		assertEquals(miscFrozenTokenBalance, balance);
	}

	@Test
	public void refusesToAdjustWrongly() {
		given(tokenStore.adjustBalance(misc, tokenId, 555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.adjustTokenBalance(misc, tokenId, 555);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		assertEquals(0, subject.numTouches);
	}

	@Test
	public void adjustsIfValid() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		// given:
		var status = subject.adjustTokenBalance(misc, tokenId, 555);

		// expect:
		assertEquals(OK, status);
		// and:
		assertEquals(
				AccountAmount.newBuilder().setAccountID(misc).setAmount(555).build(),
				subject.netTokenTransfers.get(tokenId).getAccountAmounts(0));
	}

	@Test
	public void injectsLedgerToTokenStore() {
		// expect:
		verify(tokenStore).setAccountsLedger(accountsLedger);
		verify(tokenStore).setHederaLedger(subject);
	}

	private void setupWithLiveLedger() {
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				() -> new MerkleAccount(),
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		FCMap<MerkleEntityId, MerkleToken> tokens =
				new FCMap<>(new MerkleEntityId.Provider(), MerkleToken.LEGACY_PROVIDER);
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				() -> new MerkleTokenRelStatus(),
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
		tokenStore = new HederaTokenStore(
				ids,
				TestContextValidator.TEST_VALIDATOR,
				new MockGlobalDynamicProps(),
				() -> tokens,
				tokenRelsLedger);
		subject = new HederaLedger(tokenStore, ids, creator, historian, accountsLedger);
	}

	private void setupWithLiveFcBackedLedger() {
		backingMap = new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
		backingAccounts = new FCMapBackingAccounts(() -> backingMap);
		MerkleAccount genesisAccount = new MerkleAccount();
		try {
			genesisAccount.setBalance(50_000_000_000L);
			new HederaAccountCustomizer()
					.key(new JContractIDKey(0, 0, 2))
					.customizing(genesisAccount);
		} catch (Exception impossible) {
		}
		backingAccounts.put(genesis, genesisAccount);
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				() -> new MerkleAccount(),
				backingAccounts,
				new ChangeSummaryManager<>());
		subject = new HederaLedger(tokenStore, ids, creator, historian, accountsLedger);
	}

	@Test
	public void backingFcRootHashDoesDependsOnDeleteOrder() {
		// when:
		setupWithLiveFcBackedLedger();
		accountsLedger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR);
		commitNewSpawns(50, 100);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] firstPreHash = backingMap.getRootHash().getValue();
		commitDestructions(50, 55);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] firstPostHash = backingMap.getRootHash().getValue();

		// and:
		setupWithLiveFcBackedLedger();
		accountsLedger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR);
		commitNewSpawns(50, 100);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] secondPreHash = backingMap.getRootHash().getValue();
		accountsLedger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR.reversed());
		commitDestructions(50, 55);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] secondPostHash = backingMap.getRootHash().getValue();

		// then:
		assertTrue(Arrays.equals(firstPreHash, secondPreHash));
		assertFalse(Arrays.equals(firstPostHash, secondPostHash));
	}

	@Test
	public void backingFcRootHashDependsOnUpdateOrder() {
		// when:
		setupWithLiveFcBackedLedger();
		accountsLedger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR);
		commitNewSpawns(50, 100);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] firstHash = backingMap.getRootHash().getValue();

		// and:
		setupWithLiveFcBackedLedger();
		commitNewSpawns(50, 100);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] secondHash = backingMap.getRootHash().getValue();

		// then:
		assertFalse(Arrays.equals(firstHash, secondHash));
	}

	private void commitDestructions(long seqStart, long seqEnd) {
		subject.begin();
		LongStream.rangeClosed(seqStart, seqEnd)
				.mapToObj(n -> AccountID.newBuilder().setAccountNum(n).build())
				.forEach(id -> subject.destroy(id));
		subject.commit();
	}

	private void commitNewSpawns(long seqStart, long seqEnd) {
		long initialBalance = 1_000L;

		subject.begin();
		subject.adjustBalance(genesis, (seqEnd - seqStart + 1) * -initialBalance);
		LongStream.rangeClosed(seqStart, seqEnd)
				.mapToObj(n -> AccountID.newBuilder().setAccountNum(n).build())
				.forEach(id -> subject.spawn(
						id,
						initialBalance,
						new HederaAccountCustomizer()
								.key(uncheckedMap(Key.newBuilder().setContractID(asContract(id)).build()))));
		subject.commit();
	}

	private JKey uncheckedMap(Key key) {
		try {
			return mapKey(key);
		} catch (Exception impossible) {
			throw new IllegalStateException("Impossible!");
		}
	}

	@Test
	public void delegatesDestroy() {
		// when:
		subject.destroy(genesis);

		// then:
		verify(accountsLedger).destroy(genesis);
	}

	@Test
	public void indicatesNoChangeSetIfNotInTx() {
		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(accountsLedger, never()).changeSetSoFar();
		assertEquals(HederaLedger.NO_ACTIVE_TXN_CHANGE_SET, summary);
	}

	@Test
	public void delegatesChangeSetIfInTxn() {
		// setup:
		String zeroingGenesis = "{0.0.2: [BALANCE -> 0]}";
		String creatingTreasury = "{0.0.2 <-> 0.0.1001: [TOKEN_BALANCE -> 1_000_000]}";

		given(accountsLedger.isInTransaction()).willReturn(true);
		given(accountsLedger.changeSetSoFar()).willReturn(zeroingGenesis);
		given(tokenRelsLedger.changeSetSoFar()).willReturn(creatingTreasury);

		// when:
		String summary = subject.currentChangeSet();
		System.out.println(summary);

		// then:
		verify(accountsLedger).changeSetSoFar();
		assertEquals(String.format(
				"--- ACCOUNTS ---\n%s\n--- TOKEN RELATIONSHIPS ---\n%s",
				zeroingGenesis,
				creatingTreasury), summary);
	}

	@Test
	public void delegatesGet() {
		// setup:
		MerkleAccount fakeGenesis = new MerkleAccount();

		given(accountsLedger.get(genesis)).willReturn(fakeGenesis);

		// expect:
		assertTrue(fakeGenesis == subject.get(genesis));
	}

	@Test
	public void delegatesExists() {
		// given:
		AccountID missing = asAccount("55.66.77");

		// when:
		boolean hasMissing = subject.exists(missing);
		boolean hasGenesis = subject.exists(genesis);

		// then:
		verify(accountsLedger, times(2)).exists(any());
		assertTrue(hasGenesis);
		assertFalse(hasMissing);
	}


	@Test
	public void setsSelfOnHistorian() {
		// expect:
		verify(historian).setLedger(subject);
		verify(creator).setLedger(subject);
		verify(historian).setCreator(creator);
	}

	@Test
	public void throwsOnCommittingInconsistentAdjustments() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		subject.adjustBalance(genesis, -1L);
		System.out.println(accountsLedger.changeSetSoFar());

		// then:
		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	public void delegatesTokenChangeDrop() {
		subject.numTouches = 2;
		subject.tokensTouched[0] = tokenWith(111);
		subject.tokensTouched[1] = tokenWith(222);
		// and:
		subject.netTokenTransfers.put(
				tokenWith(111),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.2"))));
		subject.netTokenTransfers.put(
				tokenWith(222),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.3"))));
		// when:
		subject.dropPendingTokenChanges();

		// then:
		verify(tokenRelsLedger).rollback();
		// and;
		assertEquals(0, subject.numTouches);
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(111)).getAccountAmountsCount());
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(222)).getAccountAmountsCount());
	}

	@Test
	public void delegatesKnowingOps() {
		// when:
		subject.grantKyc(misc, frozenId);

		// then:
		verify(tokenStore).grantKyc(misc, frozenId);

		// and when:
		subject.revokeKyc(misc, frozenId);

		// then:
		verify(tokenStore).revokeKyc(misc, frozenId);
	}

	@Test
	public void delegatesFreezeOps() {
		// when:
		subject.freeze(misc, frozenId);

		// then:
		verify(tokenStore).freeze(misc, frozenId);

		// and when:
		subject.unfreeze(misc, frozenId);

		// then:
		verify(tokenStore).unfreeze(misc, frozenId);
	}

	@Test
	public void resetsNetTransfersAfterCommit() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		System.out.println(accountsLedger.changeSetSoFar());
		subject.commit();
		System.out.println(accountsLedger.changeSetSoFar());
		// and:
		subject.begin();
		System.out.println(accountsLedger.changeSetSoFar());
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		System.out.println(accountsLedger.changeSetSoFar());

		// then:
		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	public void doesntIncludeZeroAdjustsInNetTransfers() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);
		System.out.println(accountsLedger.changeSetSoFar());

		// then:
		assertEquals(0L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	public void doesntAllowDestructionOfRealCurrency() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.destroy(a);
		System.out.println(accountsLedger.changeSetSoFar());

		// then:
		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	public void allowsDestructionOfEphemeralCurrency() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = asAccount("1.2.3");
		subject.spawn(a, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.destroy(a);
		System.out.println(accountsLedger.changeSetSoFar());
		subject.commit();

		// then:
		assertFalse(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	public void recordsCreationOfAccountDeletedInSameTxn() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);
		System.out.println(accountsLedger.changeSetSoFar());
		int numNetTransfers = subject.netTransfersInTxn().getAccountAmountsCount();
		subject.commit();

		// then:
		assertEquals(0, numNetTransfers);
		assertTrue(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	public void addsRecordsBeforeCommitting() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();

		// then:
		verify(historian).addNewRecords();
	}

	@Test
	public void resetsTokenTransferTrackingAfterRollback() {
		// setup:
		subject.begin();
		// and:
		subject.numTouches = 2;
		subject.tokensTouched[0] = tokenWith(111);
		subject.tokensTouched[1] = tokenWith(222);
		// and:
		subject.netTokenTransfers.put(
				tokenWith(111),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.2"))));
		subject.netTokenTransfers.put(
				tokenWith(222),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.3"))));

		// when:
		subject.rollback();

		// then:
		assertEquals(0, subject.numTouches);
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(111)).getAccountAmountsCount());
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(222)).getAccountAmountsCount());
	}

	@Test
	public void resetsNetTransfersAfterRollback() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		System.out.println(accountsLedger.changeSetSoFar());
		subject.rollback();
		System.out.println(accountsLedger.changeSetSoFar());
		// and:
		subject.begin();
		System.out.println(accountsLedger.changeSetSoFar());
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		System.out.println(accountsLedger.changeSetSoFar());
		System.out.println(subject.netTransfersInTxn());

		// then:
		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	public void returnsNetTransfersInBalancedTxn() {
		setupWithLiveLedger();
		// and:
		TokenID tA, tB;

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		AccountID c = subject.create(genesis, 3_000L, new HederaAccountCustomizer().memo("c"));
		AccountID d = subject.create(genesis, 4_000L, new HederaAccountCustomizer().memo("d"));
		// and:
		var rA = tokenStore.createProvisionally(stdWith("MINE", "MINE", a), a, thisSecond);
		System.out.println(rA.getStatus());
		tA = rA.getCreated().get();
		tokenStore.commitCreation();
		var rB = tokenStore.createProvisionally(stdWith("YOURS", "YOURS", b), b, thisSecond);
		System.out.println(rB.getStatus());
		tB = rB.getCreated().get();
		tokenStore.commitCreation();
		// and:
		tokenStore.associate(a, List.of(refWith(tA), refWith(tB)));
		tokenStore.associate(b, List.of(refWith(tA), refWith(tB)));
		tokenStore.associate(c, List.of(refWith(tA), refWith(tB)));
		tokenStore.associate(d, List.of(refWith(tA), refWith(tB)));
		// and:
		subject.doTransfer(d, a, 1_000L);
		subject.delete(d, b);
		subject.adjustBalance(c, 1_000L);
		subject.adjustBalance(genesis, -1_000L);
		subject.doTransfers(TxnUtils.withAdjustments(a, -500L, b, 250L, c, 250L));
		System.out.println(accountsLedger.changeSetSoFar());
		System.out.println(tokenRelsLedger.changeSetSoFar());
		// and:
		subject.adjustTokenBalance(a, tA, +10_000);
		subject.adjustTokenBalance(a, tA, -5_000);
		subject.adjustTokenBalance(a, tB, +1);
		subject.adjustTokenBalance(a, tB, -1);
		subject.adjustTokenBalance(b, tB, +10_000);
		subject.adjustTokenBalance(c, tB, +50);
		subject.adjustTokenBalance(c, tB, +50);
		subject.adjustTokenBalance(c, tB, -50);
		subject.adjustTokenBalance(c, tA, +5000);
		System.out.println(subject.freeze(a, tB));
		System.out.println(subject.adjustTokenBalance(a, tB, +1_000_000));
		System.out.println(accountsLedger.changeSetSoFar());

		// then:
		assertThat(
				subject.netTransfersInTxn().getAccountAmountsList(),
				containsInAnyOrder(
						AccountAmount.newBuilder().setAccountID(a).setAmount(1_500L).build(),
						AccountAmount.newBuilder().setAccountID(b).setAmount(5_250L).build(),
						AccountAmount.newBuilder().setAccountID(c).setAmount(4_250L).build(),
						AccountAmount.newBuilder().setAccountID(genesis).setAmount(-11_000L).build()));
		// and:
		assertThat(subject.netTokenTransfersInTxn(),
				contains(
						construct(tA, aa(a, +5_000), aa(c, +5_000)),
						construct(tB, aa(b, +10_000), aa(c, +50))
				));
	}

	private TokenCreateTransactionBody stdWith(String symbol, String tokenName, AccountID account) {
		var key = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		return TokenCreateTransactionBody.newBuilder()
				.setAdminKey(key)
				.setFreezeKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
				.setSymbol(symbol)
				.setName(tokenName)
				.setInitialSupply(0)
				.setTreasury(account)
				.setExpiry(2 * thisSecond)
				.setDecimals(0)
				.setFreezeDefault(false)
				.build();
	}

	private AccountAmount aa(AccountID account, long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
	}

	private TokenTransferList construct(TokenID token, AccountAmount... xfers) {
		return TokenTransferList.newBuilder()
				.setToken(token)
				.addAllTransfers(List.of(xfers))
				.build();
	}

	@Test
	public void recognizesPendingCreates() {
		setupWithLiveLedger();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1L, new HederaAccountCustomizer().memo("a"));

		// then:
		assertTrue(subject.isPendingCreation(a));
		assertFalse(subject.isPendingCreation(genesis));
	}

	@Test
	public void delegatesToCorrectReceiveThreshProperty() {
		// when:
		subject.fundsReceivedRecordThreshold(genesis);

		// then:
		verify(accountsLedger).get(genesis, FUNDS_RECEIVED_RECORD_THRESHOLD);
	}

	@Test
	public void delegatesToCorrectSendThreshProperty() {
		// when:
		subject.fundsSentRecordThreshold(genesis);

		// then:
		verify(accountsLedger).get(genesis, FUNDS_SENT_RECORD_THRESHOLD);
	}

	@Test
	public void delegatesToCorrectContractProperty() {
		// when:
		subject.isSmartContract(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_SMART_CONTRACT);
	}

	@Test
	public void delegatesToCorrectDeletionProperty() {
		// when:
		subject.isDeleted(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_DELETED);
	}

	@Test
	public void delegatesToGetTokens() {
		// setup:
		var tokens = new MerkleAccountTokens();

		given(accountsLedger.get(genesis, AccountProperty.TOKENS)).willReturn(tokens);

		// when:
		var actual = subject.getAssociatedTokens(genesis);

		// then:
		Assertions.assertSame(actual, tokens);
	}

	@Test
	public void delegatesToSetTokens() {
		// setup:
		var tokens = new MerkleAccountTokens();

		// when:
		subject.setAssociatedTokens(genesis, tokens);

		// then:
		verify(accountsLedger).set(genesis, TOKENS, tokens);
	}

	@Test
	public void delegatesToCorrectExpiryProperty() {
		// when:
		subject.expiry(genesis);

		// then:
		verify(accountsLedger).get(genesis, EXPIRY);
	}

	@Test
	public void throwsOnNetTransfersIfNotInTxn() {
		// setup:
		doThrow(IllegalStateException.class).when(accountsLedger).throwIfNotInTxn();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.netTransfersInTxn());
	}

	@Test
	public void purgesExpiredPayerRecords() {
		// setup:
		Consumer<ExpirableTxnRecord> cb = (Consumer<ExpirableTxnRecord>) mock(Consumer.class);
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(50L, 100L, 200L, 311L, 500L);
		List<ExpirableTxnRecord> added = new ArrayList<>(records);
		addPayerRecords(misc, records);

		// when:
		long newEarliestExpiry = subject.purgeExpiredPayerRecords(misc, 200L, cb);

		// then:
		assertEquals(311L, newEarliestExpiry);
		// and:
		verify(cb).accept(same(added.get(0)));
		verify(cb).accept(same(added.get(1)));
		verify(cb).accept(same(added.get(2)));
		// and:
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(PAYER_RECORDS::equals),
				captor.capture());
		// and:
		assertTrue(captor.getValue() == records);
		assertThat(
				((FCQueue<ExpirableTxnRecord>) captor.getValue())
						.stream()
						.map(ExpirableTxnRecord::getExpiry)
						.collect(Collectors.toList()),
				contains(311L, 500L));
	}

	@Test
	public void purgesExpiredRecords() {
		// setup:
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(50L, 100L, 200L, 311L, 500L);
		addRecords(misc, records);

		// when:
		long newEarliestExpiry = subject.purgeExpiredRecords(misc, 200L);

		// then:
		assertEquals(311L, newEarliestExpiry);
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(HISTORY_RECORDS::equals),
				captor.capture());
		// and:
		assertTrue(captor.getValue() == records);
		assertThat(
				((FCQueue<ExpirableTxnRecord>) captor.getValue())
						.stream()
						.map(ExpirableTxnRecord::getExpiry)
						.collect(Collectors.toList()),
				contains(311L, 500L));
	}

	@Test
	public void returnsMinusOneIfAllRecordsPurged() {
		// setup:
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(50L, 100L, 200L, 311L, 500L);
		addRecords(misc, records);
		HederaLedger.LedgerTxnEvictionStats.INSTANCE.reset();

		// when:
		long newEarliestExpiry = subject.purgeExpiredRecords(misc, 1_000L);

		// then:
		assertEquals(-1L, newEarliestExpiry);
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(HISTORY_RECORDS::equals),
				captor.capture());
		// and:
		assertTrue(captor.getValue() == records);
		assertTrue(((FCQueue<ExpirableTxnRecord>) captor.getValue()).isEmpty());
		// and:
		assertEquals(5, HederaLedger.LedgerTxnEvictionStats.INSTANCE.recordsPurged());
		assertEquals(1, HederaLedger.LedgerTxnEvictionStats.INSTANCE.accountsTouched());
	}

	@Test
	public void addsNewPayerRecordLast() {
		// setup:
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(100L, 50L, 200L, 311L);
		addPayerRecords(misc, records);
		// and:
		ExpirableTxnRecord newRecord = asExpirableRecords(1_000L).peek();

		// when:
		subject.addPayerRecord(misc, newRecord);

		// then:
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(PAYER_RECORDS::equals),
				captor.capture());
		// and:
		assertTrue(captor.getValue() == records);
		assertThat(
				((FCQueue<ExpirableTxnRecord>) captor.getValue())
						.stream()
						.map(ExpirableTxnRecord::getExpiry)
						.collect(Collectors.toList()),
				contains(100L, 50L, 200L, 311L, 1_000L));
	}

	@Test
	public void addsNewRecordLast() {
		// setup:
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(100L, 50L, 200L, 311L);
		addRecords(misc, records);
		// and:
		ExpirableTxnRecord newRecord = asExpirableRecords(1L).peek();

		// when:
		long newEarliestExpiry = subject.addRecord(misc, newRecord);

		// then:
		assertEquals(100L, newEarliestExpiry);
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(HISTORY_RECORDS::equals),
				captor.capture());
		// and:
		assertTrue(captor.getValue() == records);
		assertThat(
				((FCQueue<ExpirableTxnRecord>) captor.getValue())
						.stream()
						.map(ExpirableTxnRecord::getExpiry)
						.collect(Collectors.toList()),
				contains(100L, 50L, 200L, 311L, 1L));
	}

	@Test
	public void throwsOnUnderfundedCreate() {
		// expect:
		assertThrows(InsufficientFundsException.class, () ->
				subject.create(rand, RAND_BALANCE + 1, noopCustomizer));
	}

	@Test
	public void performsFundedCreate() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);
		// and:
		given(accountsLedger.existsPending(IdUtils.asAccount(String.format("0.0.%d", NEXT_ID)))).willReturn(true);

		// when:
		AccountID created = subject.create(rand, 1_000L, customizer);

		// then:
		assertEquals(NEXT_ID, created.getAccountNum());
		verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE - 1_000L);
		verify(accountsLedger).create(created);
		verify(accountsLedger).set(created, BALANCE, 1_000L);
		verify(customizer).customize(created, accountsLedger);
	}

	@Test
	public void performsUnconditionalSpawn() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);
		AccountID contract = asAccount("1.2.3");
		long balance = 1_234L;
		// and:
		given(accountsLedger.existsPending(contract)).willReturn(true);

		// when:
		subject.spawn(contract, balance, customizer);

		// then:
		verify(accountsLedger).create(contract);
		verify(accountsLedger).set(contract, BALANCE, balance);
		verify(customizer).customize(contract, accountsLedger);
	}

	@Test
	public void deletesGivenAccount() {
		// when:
		subject.delete(rand, misc);

		// expect:
		verify(accountsLedger).set(rand, BALANCE, 0L);
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + RAND_BALANCE);
		verify(accountsLedger).set(rand, IS_DELETED, true);
	}

	@Test
	public void throwsOnCustomizingDeletedAccount() {
		// expect:
		assertThrows(DeletedAccountException.class, () -> subject.customize(deleted, noopCustomizer));
	}

	@Test
	public void customizesGivenAccount() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);

		// when:
		subject.customize(rand, customizer);

		// then:
		verify(customizer).customize(rand, accountsLedger);

	}

	@Test
	public void throwsOnTransferWithDeletedFromAccount() {
		// setup:
		DeletedAccountException e = null;

		// when:
		try {
			subject.doTransfer(deleted, misc, 1L);
		} catch (DeletedAccountException aide) {
			e = aide;
		}

		// then:
		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void throwsOnTransferWithDeletedToAccount() {
		// setup:
		DeletedAccountException e = null;

		// when:
		try {
			subject.doTransfer(misc, deleted, 1L);
		} catch (DeletedAccountException aide) {
			e = aide;
		}

		// then:
		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void throwsOnTransfersWithDeleted() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, deleted, -2, genesis, 1);
		DeletedAccountException e = null;

		// expect:
		try {
			subject.doTransfers(accountAmounts);
		} catch (DeletedAccountException aide) {
			e = aide;
		}

		// then:
		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void doesReasonableTransfers() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, -2, genesis, 1);

		// expect:
		subject.doTransfers(accountAmounts);

		// then:
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + 1);
		verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE - 2);
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + 1);
	}

	@Test
	public void throwsOnImpossibleTransfers() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, 2, genesis, 3);

		// expect:
		assertThrows(NonZeroNetTransfersException.class, () -> subject.doTransfers(accountAmounts));
	}

	@Test
	public void doesReasonableTransfer() {
		// setup:
		long amount = 1_234L;

		// when:
		subject.doTransfer(genesis, misc, amount);

		// then:
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE - amount);
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + amount);
	}

	@Test
	public void throwsOnImpossibleTransferWithBrokerPayer() {
		// setup:
		long amount = GENESIS_BALANCE + 1;
		InsufficientFundsException e = null;

		// when:
		try {
			subject.doTransfer(genesis, misc, amount);
		} catch (InsufficientFundsException ibce) {
			e = ibce;
		}

		// then:
		assertEquals(messageFor(genesis, -1 * amount), e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void makesPossibleAdjustment() {
		// setup:
		long amount = -1 * GENESIS_BALANCE / 2;

		// when:
		subject.adjustBalance(genesis, amount);

		// then:
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + amount);
	}

	@Test
	public void throwsOnNegativeBalance() {
		// setup:
		long overdraftAdjustment = -1 * GENESIS_BALANCE - 1;
		InsufficientFundsException e = null;

		// when:
		try {
			subject.adjustBalance(genesis, overdraftAdjustment);
		} catch (InsufficientFundsException ibce) {
			e = ibce;
		}

		// then:
		assertEquals(messageFor(genesis, overdraftAdjustment), e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void forwardsGetBalanceCorrectly() {
		// when:
		long balance = subject.getBalance(genesis);

		// then
		assertEquals(GENESIS_BALANCE, balance);
	}

	@Test
	public void forwardsTransactionalSemantics() {
		// setup:
		subject.setTokenRelsLedger(HederaLedger.UNUSABLE_TOKEN_RELS_LEDGER);
		InOrder inOrder = inOrder(accountsLedger);

		// when:
		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		// then:
		inOrder.verify(accountsLedger).begin();
		inOrder.verify(accountsLedger).commit();
		inOrder.verify(accountsLedger).begin();
		inOrder.verify(accountsLedger).rollback();
	}

	@Test
	public void forwardsTransactionalSemanticsToRelsLedgerIfPresent() {
		// setup:
		InOrder inOrder = inOrder(tokenRelsLedger);

		// when:
		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		// then:
		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(tokenRelsLedger).commit();
		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(tokenRelsLedger).rollback();
	}

	private void addToLedger(
			AccountID id,
			long balance,
			HederaAccountCustomizer customizer
	) {
		addToLedger(id, balance, customizer, Collections.emptyMap());
	}

	private static class TokenInfo {
		final long balance;
		final MerkleToken token;

		public TokenInfo(long balance, MerkleToken token) {
			this.balance = balance;
			this.token = token;
		}
	}

	private void addToLedger(
			AccountID id,
			long balance,
			HederaAccountCustomizer customizer,
			Map<TokenID, TokenInfo> tokenInfo
	) {
		when(accountsLedger.get(id, EXPIRY)).thenReturn(1_234_567_890L);
		when(accountsLedger.get(id, BALANCE)).thenReturn(balance);
		when(accountsLedger.get(id, IS_DELETED)).thenReturn(false);
		when(accountsLedger.get(id, IS_SMART_CONTRACT)).thenReturn(false);
		when(accountsLedger.get(id, FUNDS_SENT_RECORD_THRESHOLD)).thenReturn(1L);
		when(accountsLedger.get(id, FUNDS_RECEIVED_RECORD_THRESHOLD)).thenReturn(2L);
		when(accountsLedger.exists(id)).thenReturn(true);
		// and:
		for (TokenID tId : tokenInfo.keySet()) {
			var info = tokenInfo.get(tId);
			var relationship = BackingTokenRels.asTokenRel(id, tId);
			when(tokenRelsLedger.get(relationship, TOKEN_BALANCE)).thenReturn(info.balance);
		}
	}

	private void addDeletedAccountToLedger(AccountID id, HederaAccountCustomizer customizer) {
		when(accountsLedger.get(id, BALANCE)).thenReturn(0L);
		when(accountsLedger.get(id, IS_DELETED)).thenReturn(true);
	}

	private void addPayerRecords(AccountID id, FCQueue<ExpirableTxnRecord> records) {
		when(accountsLedger.get(id, PAYER_RECORDS)).thenReturn(records);
	}

	private void addRecords(AccountID id, FCQueue<ExpirableTxnRecord> records) {
		when(accountsLedger.get(id, HISTORY_RECORDS)).thenReturn(records);
	}

	FCQueue<ExpirableTxnRecord> asExpirableRecords(long... expiries) {
		FCQueue<ExpirableTxnRecord> records = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
		for (int i = 0; i < expiries.length; i++) {
			ExpirableTxnRecord record = new ExpirableTxnRecord();
			record.setExpiry(expiries[i]);
			records.offer(record);
		}
		return records;
	}

	private void givenAdjustBalanceUpdatingTokenXfers(AccountID misc, TokenID tokenId, long i) {
		given(tokenStore.adjustBalance(misc, tokenId, i))
				.willAnswer(invocationOnMock -> {
					AccountID aId = invocationOnMock.getArgument(0);
					TokenID tId = invocationOnMock.getArgument(1);
					long amount = invocationOnMock.getArgument(2);
					subject.updateTokenXfers(tId, aId, amount);
					return OK;
				});
	}
}
