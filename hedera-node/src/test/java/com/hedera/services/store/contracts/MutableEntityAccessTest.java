package com.hedera.services.store.contracts;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class MutableEntityAccessTest {
	@Mock
	private HederaLedger ledger;
	@Mock
	private Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> supplierBytecode;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecodeStorage;
	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	@Mock
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private SizeLimitedStorage storage;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private MutableEntityAccess subject;

	private final long autoRenewSecs = Instant.now().getEpochSecond();
	private final AccountID id = IdUtils.asAccount("0.0.1234");
	private final long balance = 1234L;
	private final EntityId proxy = EntityId.MISSING_ENTITY_ID;
	private static final JKey key = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());

	private final UInt256 contractStorageKey = UInt256.ONE;
	private final UInt256 contractStorageValue = UInt256.MAX_VALUE;

	private final Bytes bytecode = Bytes.of("contract-code".getBytes());
	private final VirtualBlobKey expectedBytecodeKey = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE,
			(int) id.getAccountNum());
	private final VirtualBlobValue expectedBytecodeValue = new VirtualBlobValue(bytecode.toArray());

	@BeforeEach
	void setUp() {
		given(ledger.getTokenRelsLedger()).willReturn(tokenRelsLedger);
		given(ledger.getAccountsLedger()).willReturn(accountsLedger);
		given(ledger.getNftsLedger()).willReturn(nftsLedger);

		subject = new MutableEntityAccess(ledger, txnCtx, storage, tokensLedger, supplierBytecode);
	}

	@Test
	void recordsViaSizeLimitedStorage() {
		subject.recordNewKvUsageTo(accountsLedger);

		verify(storage).recordNewKvUsageTo(accountsLedger);
	}

	@Test
	void flushesAsExpected() {
		subject.flushStorage();

		verify(storage).validateAndCommit();
	}

	@Test
	void setsSelfInLedger() {
		verify(ledger).setMutableEntityAccess(subject);
	}

	@Test
	void returnsTokensLedgerChangeSetForManagedChanges() {
		final var mockChanges = "N?A";
		given(tokensLedger.changeSetSoFar()).willReturn(mockChanges);
		assertEquals(mockChanges, subject.currentManagedChangeSet());
	}

	@Test
	void commitsIfContractOpActive() {
		givenActive(ContractCall);
		subject.commit();
		verify(tokensLedger).commit();
	}

	@Test
	void doesntCommitIfNonContractOpActive() {
		givenActive(TokenMint);
		subject.commit();
		verify(tokensLedger, never()).commit();
	}

	@Test
	void rollbackIfNonContractOpActive() {
		givenActive(TokenMint);
		subject.rollback();
		verify(tokensLedger, never()).rollback();
	}

	@Test
	void doesntRollbackIfNonContractOpActive() {
		givenActive(TokenMint);
		subject.rollback();
		verify(tokensLedger, never()).rollback();
	}

	@Test
	void warnsIfTokensLedgerMustBeRolledBackBeforeBeginning() {
		given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
		givenActive(ContractCreate);
		given(tokensLedger.isInTransaction()).willReturn(true);

		subject.begin();

		verify(tokensLedger).rollback();
		verify(tokensLedger).begin();
		verify(storage).beginSession();
		assertThat(
				logCaptor.warnLogs(),
				contains(Matchers.startsWith("Tokens ledger had to be rolled back")));
	}

	@Test
	void beginsLedgerTxnIfContractCreateIsActive() {
		givenActive(ContractCreate);
		subject.begin();
		verify(tokensLedger).begin();
	}

	@Test
	void beginsLedgerTxnIfContractCallIsActive() {
		givenActive(ContractCall);
		subject.begin();
		verify(tokensLedger).begin();
	}

	@Test
	void doesntBeginLedgerTxnIfNonContractOpIsActive() {
		givenActive(HederaFunctionality.TokenMint);
		subject.begin();
		verify(tokensLedger, never()).begin();
	}

	@Test
	void delegatesLedgerAccess() {
		final var worldLedgers = subject.worldLedgers();

		assertSame(tokenRelsLedger, worldLedgers.tokenRels());
		assertSame(accountsLedger, worldLedgers.accounts());
		assertSame(nftsLedger, worldLedgers.nfts());
	}

	@Test
	void customizesAccount() {
		// when:
		subject.customize(id, new HederaAccountCustomizer());

		// then:
		verify(ledger).customizePotentiallyDeleted(eq(id), any());
	}

	@Test
	void delegatesDetachmentTest() {
		given(ledger.isDetached(id)).willReturn(true);

		assertTrue(subject.isDetached(id));
	}

	@Test
	void adjustsBalance() {
		// when:
		subject.adjustBalance(id, balance);

		// then:
		verify(ledger).adjustBalance(id, balance);
	}

	@Test
	void getsBalance() {
		// given:
		given(ledger.getBalance(id)).willReturn(balance);

		// when:
		final var result = subject.getBalance(id);

		//then:
		assertEquals(balance, result);
		// and:
		verify(ledger).getBalance(id);
	}

	@Test
	void checksIfDeleted() {
		// given:
		given(ledger.isDeleted(id)).willReturn(true);

		// when:
		assertTrue(subject.isDeleted(id));

		// and:
		verify(ledger).isDeleted(id);
	}

	@Test
	void checksIfExtant() {
		// given:
		given(ledger.exists(id)).willReturn(true);

		// when:
		assertTrue(subject.isExtant(id));

		// and:
		verify(ledger).exists(id);
	}

	@Test
	void getsKey() {
		// given:
		given(ledger.key(id)).willReturn(key);

		// when:
		final var result = subject.getKey(id);

		// then:
		assertEquals(key, result);
		// and:
		verify(ledger).key(id);
	}

	@Test
	void getsMemo() {
		final var memo = "memo";

		given(ledger.memo(id)).willReturn(memo);

		// when:
		final var result = subject.getMemo(id);

		// then:
		assertEquals(memo, result);
		// and:
		verify(ledger).memo(id);
	}

	@Test
	void getsExpiry() {
		final var expiry = 5678L;

		given(ledger.expiry(id)).willReturn(expiry);

		final var result = subject.getExpiry(id);

		assertEquals(expiry, result);
		// and:
		verify(ledger).expiry(id);
	}

	@Test
	void getsAutoRenew() {
		// given:
		given(ledger.autoRenewPeriod(id)).willReturn(autoRenewSecs);

		// when:
		final var result = subject.getAutoRenew(id);

		// then:
		assertEquals(autoRenewSecs, result);
		// and:
		verify(ledger).autoRenewPeriod(id);
	}

	@Test
	void getsProxy() {
		// given:
		given(ledger.proxy(id)).willReturn(proxy);

		// when:
		final var result = subject.getProxy(id);

		// then:
		assertEquals(proxy, result);
		// and:
		verify(ledger).proxy(id);
	}

	@Test
	void putsNonZeroContractStorageValue() {
		subject.putStorage(id, contractStorageKey, contractStorageValue);

		verify(storage).putStorage(id, contractStorageKey, contractStorageValue);
	}

	@Test
	void getsExpectedContractStorageValue() {
		// and:
		given(storage.getStorage(id, contractStorageKey)).willReturn(UInt256.MAX_VALUE);

		// when:
		final var result = subject.getStorage(id, contractStorageKey);

		// then:
		assertEquals(UInt256.MAX_VALUE, result);
	}

	@Test
	void storesBlob() {
		// given:
		given(supplierBytecode.get()).willReturn(bytecodeStorage);

		// when:
		subject.storeCode(id, bytecode);

		// then:
		verify(bytecodeStorage).put(expectedBytecodeKey, expectedBytecodeValue);
	}

	@Test
	void fetchesEmptyBytecode() {
		given(supplierBytecode.get()).willReturn(bytecodeStorage);

		assertNull(subject.fetchCodeIfPresent(id));
	}

	@Test
	void fetchesBytecode() {
		given(supplierBytecode.get()).willReturn(bytecodeStorage);
		given(bytecodeStorage.get(expectedBytecodeKey)).willReturn(expectedBytecodeValue);

		final var result = subject.fetchCodeIfPresent(id);

		assertEquals(bytecode, result);
		verify(bytecodeStorage).get(expectedBytecodeKey);
	}

	private void givenActive(final HederaFunctionality function) {
		given(accessor.getFunction()).willReturn(function);
		given(txnCtx.accessor()).willReturn(accessor);
	}
}