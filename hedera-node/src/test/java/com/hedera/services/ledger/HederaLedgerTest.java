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
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.tokens.HederaTokenStore;
import com.hedera.services.tokens.TokenStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfers;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;
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
import static com.hedera.services.legacy.core.jproto.JKey.mapKey;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asAccount;
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

	TokenTransfers multipleValidTokenTransfers = TokenTransfers.newBuilder()
			.addAllTransfers(List.of(
					IdUtils.fromRef(frozenSymbol, misc, +1_000), IdUtils.fromId(frozenId, rand, -1_000),
					IdUtils.fromRef(otherSymbol, misc, +1_000), IdUtils.fromId(tokenId, rand, -1_000)
			)).build();
	TokenTransfers missingSymbolTokenTransfers = TokenTransfers.newBuilder()
			.addAllTransfers(List.of(
					IdUtils.fromRef(frozenSymbol, misc, +1_000), IdUtils.fromId(frozenId, rand, -1_000),
					IdUtils.fromRef(missingSymbol, misc, +1_000), IdUtils.fromId(tokenId, rand, -1_000)
			)).build();
	TokenTransfers missingIdTokenTransfers = TokenTransfers.newBuilder()
			.addAllTransfers(List.of(
					IdUtils.fromRef(frozenSymbol, misc, +1_000), IdUtils.fromId(frozenId, rand, -1_000),
					IdUtils.fromId(missingId, misc, +1_000), IdUtils.fromId(tokenId, rand, -1_000)
			)).build();
	TokenTransfers unmatchedTokenTransfers = TokenTransfers.newBuilder()
			.addAllTransfers(List.of(
					IdUtils.fromRef(frozenSymbol, misc, +1_000), IdUtils.fromId(frozenId, rand, -1_000),
					IdUtils.fromRef(frozenSymbol, misc, +2_000), IdUtils.fromId(frozenId, rand, -1_000)
			)).build();

	FCMapBackingAccounts backingAccounts;
	FCMap<MerkleEntityId, MerkleAccount> backingMap;

	HederaLedger subject;

	HederaTokenStore tokenStore;
	EntityIdSource ids;
	ExpiringCreations creator;
	AccountRecordsHistorian historian;
	TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

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

		ledger = mock(TransactionalLedger.class);
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

		subject = new HederaLedger(tokenStore, ids, creator, historian, ledger);
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
	public void rejectsMissingId() {
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
	public void rejectsMissingSymbol() {
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
	public void happyPathTransfers() {
		given(tokenStore.adjustBalance(any(), any(), anyLong()))
				.willAnswer(invocationOnMock -> {
					AccountID aId = invocationOnMock.getArgument(0);
					TokenID tId = invocationOnMock.getArgument(1);
					long amount = invocationOnMock.getArgument(2);
					subject.updateTokenXfers(tId, aId, amount);
					return OK;
				});

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
		given(tokenStore.adjustBalance(any(), any(), anyLong()))
				.willAnswer(invocationOnMock -> {
					AccountID aId = invocationOnMock.getArgument(0);
					TokenID tId = invocationOnMock.getArgument(1);
					long amount = invocationOnMock.getArgument(2);
					subject.updateTokenXfers(tId, aId, amount);
					return OK;
				});

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
		verify(tokenStore).setLedger(ledger);
		verify(tokenStore).setHederaLedger(subject);
	}

	private void setupWithLiveLedger() {
		ledger = new TransactionalLedger<>(
				AccountProperty.class,
				() -> new MerkleAccount(),
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		FCMap<MerkleEntityId, MerkleToken> tokens =
				new FCMap<>(new MerkleEntityId.Provider(), MerkleToken.LEGACY_PROVIDER);
		tokenStore = new HederaTokenStore(
				ids,
				new MockGlobalDynamicProps(),
				() -> tokens);
		subject = new HederaLedger(tokenStore, ids, creator, historian, ledger);
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
		ledger = new TransactionalLedger<>(
				AccountProperty.class,
				() -> new MerkleAccount(),
				backingAccounts,
				new ChangeSummaryManager<>());
		subject = new HederaLedger(tokenStore, ids, creator, historian, ledger);
	}

	@Test
	public void backingFcRootHashDoesDependsOnDeleteOrder() {
		// when:
		setupWithLiveFcBackedLedger();
		ledger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR);
		commitNewSpawns(50, 100);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] firstPreHash = backingMap.getRootHash().getValue();
		commitDestructions(50, 55);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] firstPostHash = backingMap.getRootHash().getValue();

		// and:
		setupWithLiveFcBackedLedger();
		ledger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR);
		commitNewSpawns(50, 100);
		CryptoFactory.getInstance().digestTreeSync(backingMap);
		byte[] secondPreHash = backingMap.getRootHash().getValue();
		ledger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR.reversed());
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
		ledger.setKeyComparator(HederaLedger.ACCOUNT_ID_COMPARATOR);
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
		verify(ledger).destroy(genesis);
	}

	@Test
	public void indicatesNoChangeSetIfNotInTx() {
		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(ledger, never()).changeSetSoFar();
		assertEquals(HederaLedger.NO_ACTIVE_TXN_CHANGE_SET, summary);
	}

	@Test
	public void delegatesChangeSetIfInTxn() {
		// setup:
		String zeroingGenesis = "{0.0.2: [BALANCE -> 0]}";

		given(ledger.isInTransaction()).willReturn(true);
		given(ledger.changeSetSoFar()).willReturn(zeroingGenesis);

		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(ledger).changeSetSoFar();
		assertEquals(zeroingGenesis, summary);
	}

	@Test
	public void delegatesGet() {
		// setup:
		MerkleAccount fakeGenesis = new MerkleAccount();

		given(ledger.get(genesis)).willReturn(fakeGenesis);

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
		verify(ledger, times(2)).exists(any());
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
		System.out.println(ledger.changeSetSoFar());

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
		verify(ledger).dropPendingTokenChanges();
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
		System.out.println(ledger.changeSetSoFar());
		subject.commit();
		System.out.println(ledger.changeSetSoFar());
		// and:
		subject.begin();
		System.out.println(ledger.changeSetSoFar());
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		System.out.println(ledger.changeSetSoFar());

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
		System.out.println(ledger.changeSetSoFar());

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
		System.out.println(ledger.changeSetSoFar());

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
		System.out.println(ledger.changeSetSoFar());
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
		System.out.println(ledger.changeSetSoFar());
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
		System.out.println(ledger.changeSetSoFar());
		subject.rollback();
		System.out.println(ledger.changeSetSoFar());
		// and:
		subject.begin();
		System.out.println(ledger.changeSetSoFar());
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		System.out.println(ledger.changeSetSoFar());
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
		System.out.println(tokenStore.createProvisionally(stdWith("MINE", "MINE", a), a).getStatus());
		tA = tokenStore.createProvisionally(stdWith("MINE", "MINE", a), a).getCreated().get();
		tokenStore.commitCreation();
		tB = tokenStore.createProvisionally(stdWith("YOURS", "YOURS", b), b).getCreated().get();
		tokenStore.commitCreation();
		// and:
		subject.doTransfer(d, a, 1_000L);
		subject.delete(d, b);
		subject.adjustBalance(c, 1_000L);
		subject.adjustBalance(genesis, -1_000L);
		subject.doTransfers(TxnUtils.withAdjustments(a, -500L, b, 250L, c, 250L));
		System.out.println(ledger.changeSetSoFar());
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
		System.out.println(ledger.changeSetSoFar());

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

	private TokenCreation stdWith(String symbol, String tokenName, AccountID account) {
		var key = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		return TokenCreation.newBuilder()
				.setAdminKey(key)
				.setFreezeKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
				.setSymbol(symbol)
				.setName(tokenName)
				.setFloat(0)
				.setTreasury(account)
				.setDivisibility(0)
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
		verify(ledger).get(genesis, FUNDS_RECEIVED_RECORD_THRESHOLD);
	}

	@Test
	public void delegatesToCorrectSendThreshProperty() {
		// when:
		subject.fundsSentRecordThreshold(genesis);

		// then:
		verify(ledger).get(genesis, FUNDS_SENT_RECORD_THRESHOLD);
	}

	@Test
	public void delegatesToCorrectContractProperty() {
		// when:
		subject.isSmartContract(genesis);

		// then:
		verify(ledger).get(genesis, IS_SMART_CONTRACT);
	}

	@Test
	public void delegatesToCorrectDeletionProperty() {
		// when:
		subject.isDeleted(genesis);

		// then:
		verify(ledger).get(genesis, IS_DELETED);
	}

	@Test
	public void delegatesToCorrectExpiryProperty() {
		// when:
		subject.expiry(genesis);

		// then:
		verify(ledger).get(genesis, EXPIRY);
	}

	@Test
	public void throwsOnNetTransfersIfNotInTxn() {
		// setup:
		doThrow(IllegalStateException.class).when(ledger).throwIfNotInTxn();

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
		verify(ledger).set(
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
		verify(ledger).set(
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
		verify(ledger).set(
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
		verify(ledger).set(
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
		verify(ledger).set(
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
		given(ledger.existsPending(IdUtils.asAccount(String.format("0.0.%d", NEXT_ID)))).willReturn(true);

		// when:
		AccountID created = subject.create(rand, 1_000L, customizer);

		// then:
		assertEquals(NEXT_ID, created.getAccountNum());
		verify(ledger).set(rand, BALANCE, RAND_BALANCE - 1_000L);
		verify(ledger).create(created);
		verify(ledger).set(created, BALANCE, 1_000L);
		verify(customizer).customize(created, ledger);
	}

	@Test
	public void performsUnconditionalSpawn() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);
		AccountID contract = asAccount("1.2.3");
		long balance = 1_234L;
		// and:
		given(ledger.existsPending(contract)).willReturn(true);

		// when:
		subject.spawn(contract, balance, customizer);

		// then:
		verify(ledger).create(contract);
		verify(ledger).set(contract, BALANCE, balance);
		verify(customizer).customize(contract, ledger);
	}

	@Test
	public void deletesGivenAccount() {
		// when:
		subject.delete(rand, misc);

		// expect:
		verify(ledger).set(rand, BALANCE, 0L);
		verify(ledger).set(misc, BALANCE, MISC_BALANCE + RAND_BALANCE);
		verify(ledger).set(rand, IS_DELETED, true);
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
		verify(customizer).customize(rand, ledger);

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
		verify(ledger, never()).set(any(), any(), any());
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
		verify(ledger, never()).set(any(), any(), any());
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
		verify(ledger, never()).set(any(), any(), any());
	}

	@Test
	public void doesReasonableTransfers() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, -2, genesis, 1);

		// expect:
		subject.doTransfers(accountAmounts);

		// then:
		verify(ledger).set(misc, BALANCE, MISC_BALANCE + 1);
		verify(ledger).set(rand, BALANCE, RAND_BALANCE - 2);
		verify(ledger).set(genesis, BALANCE, GENESIS_BALANCE + 1);
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
		verify(ledger).set(genesis, BALANCE, GENESIS_BALANCE - amount);
		verify(ledger).set(misc, BALANCE, MISC_BALANCE + amount);
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
		verify(ledger, never()).set(any(), any(), any());
	}

	@Test
	public void makesPossibleAdjustment() {
		// setup:
		long amount = -1 * GENESIS_BALANCE / 2;

		// when:
		subject.adjustBalance(genesis, amount);

		// then:
		verify(ledger).set(genesis, BALANCE, GENESIS_BALANCE + amount);
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
		verify(ledger, never()).set(any(), any(), any());
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
		InOrder inOrder = inOrder(ledger);

		// when:
		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		// then:
		inOrder.verify(ledger).begin();
		inOrder.verify(ledger).commit();
		inOrder.verify(ledger).begin();
		inOrder.verify(ledger).rollback();
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
		when(ledger.get(id, EXPIRY)).thenReturn(1_234_567_890L);
		when(ledger.get(id, BALANCE)).thenReturn(balance);
		when(ledger.get(id, IS_DELETED)).thenReturn(false);
		when(ledger.get(id, IS_SMART_CONTRACT)).thenReturn(false);
		when(ledger.get(id, FUNDS_SENT_RECORD_THRESHOLD)).thenReturn(1L);
		when(ledger.get(id, FUNDS_RECEIVED_RECORD_THRESHOLD)).thenReturn(2L);
		when(ledger.exists(id)).thenReturn(true);
		// and:
		for (TokenID tId : tokenInfo.keySet()) {
			var info = tokenInfo.get(tId);
			when(ledger.get(
					argThat(id::equals),
					argThat(BALANCE::equals),
					argThat(s -> s.id().equals(tId)))).thenReturn(info.balance);
		}
	}

	private void addDeletedAccountToLedger(AccountID id, HederaAccountCustomizer customizer) {
		when(ledger.get(id, BALANCE)).thenReturn(0L);
		when(ledger.get(id, IS_DELETED)).thenReturn(true);
	}

	private void addPayerRecords(AccountID id, FCQueue<ExpirableTxnRecord> records) {
		when(ledger.get(id, PAYER_RECORDS)).thenReturn(records);
	}

	private void addRecords(AccountID id, FCQueue<ExpirableTxnRecord> records) {
		when(ledger.get(id, HISTORY_RECORDS)).thenReturn(records);
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
}
