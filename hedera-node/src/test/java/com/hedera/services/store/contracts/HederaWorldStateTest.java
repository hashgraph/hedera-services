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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.InProgressChildRecord;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaWorldStateTest {
	@Mock
	private WorldLedgers worldLedgers;
	@Mock
	private EntityIdSource ids;
	@Mock
	private EntityAccess entityAccess;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private ContractAliases aliases;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	final long balance = 1_234L;
	final Id sponsor = new Id(0, 0, 1);
	final Id contract = new Id(0, 0, 2);
	final AccountID accountId = IdUtils.asAccount("0.0.12345");
	final Bytes code = Bytes.of("0x60606060".getBytes());
	private static final Bytes TOKEN_CALL_REDIRECT_CONTRACT_BINARY_WITH_ZERO_ADDRESS = Bytes.fromHexString(
			"6080604052348015600f57600080fd5b506000610167905077618dc65e0000000000000000000000000000000000000000600052366000602037600080366018016008845af43d806000803e8160008114605857816000f35b816000fdfea2646970667358221220d8378feed472ba49a0005514ef7087017f707b45fb9bf56bb81bb93ff19a238b64736f6c634300080b0033");
	private HederaWorldState subject;

	@BeforeEach
	void setUp() {
		CodeCache codeCache = new CodeCache(0, entityAccess);
	 	subject = new HederaWorldState(ids, entityAccess, codeCache, sigImpactHistorian, recordsHistorian, dynamicProperties);
	}

	@Test
	void getsProvisionalContractCreations() {
		var provisionalContractCreations = subject.persistProvisionalContractCreations();
		assertEquals(0, provisionalContractCreations.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	void customizeSponsoredAccounts() {
		final var specialMemo = "memo";

		givenNonNullWorldLedgers();

		/* happy path with 0 existing accounts */
		given(entityAccess.isExtant(any())).willReturn(true);
		subject.customizeSponsoredAccounts();
		verify(entityAccess, never()).customize(any(), any()); // will do 0 iterations

		/* happy path with 1 existing account */
		final var matcherCaptor = ArgumentCaptor.forClass(Predicate.class);
		final var customizerCaptor = ArgumentCaptor.forClass(Consumer.class);
		given(entityAccess.getMemo(any())).willReturn(specialMemo);
		given(entityAccess.getProxy(any())).willReturn(EntityId.MISSING_ENTITY_ID);
		given(entityAccess.getAutoRenew(any())).willReturn(100L);
		var updater = subject.updater();
		updater.getSponsorMap().put(Address.RIPEMD160, Address.RIPEMD160);
		updater.commit();
		subject.customizeSponsoredAccounts();
		verify(entityAccess).customize(any(), any());
		verify(worldLedgers).commit(sigImpactHistorian);
		verify(recordsHistorian).customizeSuccessor(
				(Predicate<InProgressChildRecord>) matcherCaptor.capture(),
				(Consumer<InProgressChildRecord>) customizerCaptor.capture());
		assertCapturedWorkAsExpected(
				(Predicate<InProgressChildRecord>) matcherCaptor.getValue(),
				(Consumer<InProgressChildRecord>) customizerCaptor.getValue(),
				EntityIdUtils.contractIdFromEvmAddress(Address.RIPEMD160),
				specialMemo);
	}

	@Test
	void alwaysClearsSponsorMapEvenAfterUnforseeableFailure() {
		givenNonNullWorldLedgers();

		given(entityAccess.isExtant(any())).willReturn(true);
		given(entityAccess.getMemo(any())).willReturn("memo");
		given(entityAccess.getProxy(any())).willReturn(EntityId.MISSING_ENTITY_ID);
		given(entityAccess.getAutoRenew(any())).willReturn(100L);
		willThrow(RuntimeException.class).given(entityAccess).customize(any(), any());

		final var subjectUpdater = subject.updater();
		subjectUpdater.getSponsorMap().put(Address.RIPEMD160, Address.RIPEMD160);
		subjectUpdater.commit();

		assertThrows(RuntimeException.class, subject::customizeSponsoredAccounts);
		/* Now the sponsor map should be cleared */
		assertDoesNotThrow(subject::customizeSponsoredAccounts);
	}

	@Test
	void doesntFailWhenSponsoredAccountIsMissing() {
		givenNonNullWorldLedgers();

		final var subjectUpdater = subject.updater();

		subjectUpdater.getSponsorMap().put(Address.RIPEMD160, Address.RIPEMD160);
		subjectUpdater.commit();

		assertDoesNotThrow(subject::customizeSponsoredAccounts);
	}

	@Test
	void doesntFailWhenSponsorIsMissing() {
		givenNonNullWorldLedgers();

		final var subjectUpdater = subject.updater();

		subjectUpdater.getSponsorMap().put(Address.RIPEMD160, Address.BLS12_MAP_FP2_TO_G2);
		subjectUpdater.commit();

		given(entityAccess.isExtant(accountIdFromEvmAddress(Address.RIPEMD160))).willReturn(true);
		given(entityAccess.isExtant(accountIdFromEvmAddress(Address.BLS12_MAP_FP2_TO_G2))).willReturn(false);
		assertDoesNotThrow(subject::customizeSponsoredAccounts);
	}

	private void assertCapturedWorkAsExpected(
			final Predicate<InProgressChildRecord> matcher,
			final Consumer<InProgressChildRecord> customizer,
			final ContractID idToMatch,
			final String memoToCustomize
	) {
		final var targetRecord = ExpirableTxnRecord.newBuilder()
				.setReceiptBuilder(TxnReceipt.newBuilder()
						.setStatus(TxnReceipt.SUCCESS_LITERAL)
						.setContractId(EntityId.fromGrpcContractId(idToMatch)));
		final var matchingBody = TransactionBody.newBuilder()
				.setContractCreateInstance(ContractCreateTransactionBody.newBuilder());
		final var inProgress = new InProgressChildRecord(1, matchingBody, targetRecord);

		assertTrue(matcher.test(inProgress));
		customizer.accept(inProgress);
		assertEquals(memoToCustomize, matchingBody.getContractCreateInstance().getMemo());

		targetRecord.getReceiptBuilder().setContractId(null);
		assertFalse(matcher.test(inProgress));

		targetRecord.getReceiptBuilder().setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL);
		assertFalse(matcher.test(inProgress));

		targetRecord.setReceiptBuilder(null);
		assertFalse(matcher.test(inProgress));
	}

	@Test
	void usesContractKeyWhenSponsorDid() {
		final var sponsorId = AccountID.newBuilder().setAccountNum(123L).build();
		final var sponsoredId = AccountID.newBuilder().setAccountNum(321L).build();
		final var sponsorAddress = EntityIdUtils.asEvmAddress(sponsorId);
		final var sponsoredAddress = EntityIdUtils.asEvmAddress(sponsoredId);

		givenNonNullWorldLedgers();
		given(entityAccess.isExtant(any())).willReturn(true);
		given(entityAccess.getKey(sponsorId)).willReturn(new JContractIDKey(0, 0, 123L));

		final var updater = subject.updater();
		updater.getSponsorMap().put(
				Address.fromHexString(Hex.encodeHexString(sponsoredAddress)),
				Address.fromHexString(Hex.encodeHexString(sponsorAddress)));

		final ArgumentCaptor<HederaAccountCustomizer> captor = forClass(HederaAccountCustomizer.class);
		updater.commit();
		subject.customizeSponsoredAccounts();

		verify(entityAccess).customize(eq(sponsoredId), captor.capture());
		final var customizer = captor.getValue();
		final var standin = new MerkleAccount();
		customizer.customizing(standin);
		final var key = standin.getAccountKey();

		assertInstanceOf(JContractIDKey.class, key);
		assertEquals(sponsoredId.getAccountNum(), ((JContractIDKey) key).getContractID().getContractNum());
	}

	@Test
	void newContractAddress() {
		final var sponsor = mock(Address.class);
		given(sponsor.toArrayUnsafe())
				.willReturn(new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
		given(ids.newContractId(any())).willReturn(ContractID.newBuilder().setContractNum(1).build());
		var addr = subject.newContractAddress(sponsor);
		assertNotEquals(addr, sponsor);
		assertEquals(1,
				EntityIdUtils.accountIdFromEvmAddress(addr.toArrayUnsafe()).getAccountNum());
	}

	@Test
	void reclaimContractId() {
		subject.reclaimContractId();
		verify(ids).reclaimLastId();
	}

	@Test
	void updater() {
		givenNonNullWorldLedgers();
		var updater = subject.updater();
		assertNotNull(updater);
		assertTrue(updater instanceof HederaWorldState.Updater);
	}

	@Test
	void rootHash() {
		assertEquals(Hash.EMPTY, subject.rootHash());
		assertEquals(Hash.EMPTY, subject.frontierRootHash());
	}

	@Test
	void streamAccounts() {
		assertThrows(
				UnsupportedOperationException.class,
				() -> subject.streamAccounts(null, 10)
		);
	}

	@Test
	void returnsNullForDetached() {
		given(entityAccess.isExtant(accountId)).willReturn(true);
		given(entityAccess.isDeleted(accountId)).willReturn(false);
		given(entityAccess.isDetached(accountId)).willReturn(true);

		assertNull(subject.get(EntityIdUtils.asTypedEvmAddress(accountId)));
	}

	@Test
	void returnsNullForNull() {
		assertNull(subject.get(null));
	}

	@Test
	void returnsEmptyCodeIfNotPresent() {
		final var address = Address.RIPEMD160;
		final var ripeAccountId = EntityIdUtils.accountIdFromEvmAddress(address.toArrayUnsafe());
		givenWellKnownAccountWithCode(ripeAccountId, null);

		final var account = subject.get(address);

		assertTrue(account.getCode().isEmpty());
		assertFalse(account.hasCode());
	}

	@Test
	void returnsExpectedCodeIfPresent() {
		final var address = Address.RIPEMD160;
		final var ripeAccountId = EntityIdUtils.accountIdFromEvmAddress(address.toArrayUnsafe());
		givenWellKnownAccountWithCode(ripeAccountId, code);

		final var account = subject.get(address);

		assertEquals(code, account.getCode());
		assertTrue(account.hasCode());
	}

	@Test
	void getsAsExpected() {
		final var account = EntityIdUtils.accountIdFromEvmAddress(Address.RIPEMD160.toArray());
		givenWellKnownAccountWithCode(account, Bytes.EMPTY);
		given(entityAccess.getStorage(any(), any())).willReturn(UInt256.ZERO);

		final var acc = subject.get(Address.RIPEMD160);
		assertNotNull(acc);
		assertEquals(Wei.of(balance), acc.getBalance());
		assertEquals(1, acc.getProxyAccount().num());
		assertEquals(100L, acc.getAutoRenew());

		objectContractWorks(acc);

		/* non-existent accounts should resolve to null */
		given(entityAccess.isExtant(any())).willReturn(false);
		var nonExistent = subject.get(Address.RIPEMD160);
		assertNull(nonExistent);

		given(entityAccess.isExtant(any())).willReturn(true);
		given(entityAccess.isDeleted(any())).willReturn(true);
		nonExistent = subject.get(Address.RIPEMD160);
		assertNull(nonExistent);
	}

	private void givenWellKnownAccountWithCode(final AccountID account, final Bytes bytecode) {
		given(entityAccess.getProxy(account)).willReturn(new EntityId(0, 0, 1));
		given(entityAccess.getBalance(account)).willReturn(balance);
		given(entityAccess.getAutoRenew(account)).willReturn(100L);
		given(entityAccess.isExtant(any())).willReturn(true);
		given(entityAccess.isDeleted(any())).willReturn(false);
		if (bytecode != null) {
			given(entityAccess.fetchCodeIfPresent(any())).willReturn(bytecode);
		}
	}

	/*
		Object contract of HederaWorldState.WorldStateAccount tests
		Please note that the said class **cannot** be instantiated, thus - the test fragment is here
	*/
	private void objectContractWorks(HederaWorldState.WorldStateAccount acc) {
		assertNotNull(acc.getAddress());
		assertNotNull(acc.getAddressHash());
		assertFalse(acc.hasCode());
		assertEquals(Bytes.EMPTY, acc.getCode());
		assertEquals(Hash.EMPTY, acc.getCodeHash());
		assertEquals(0, acc.getNonce());

		acc.setProxyAccount(EntityId.MISSING_ENTITY_ID);
		assertEquals(EntityId.MISSING_ENTITY_ID, acc.getProxyAccount());
		acc.setMemo("otherMemo");
		assertEquals("otherMemo", acc.getMemo());
		acc.setAutoRenew(10L);
		assertEquals(10L, acc.getAutoRenew());
		acc.setExpiry(10L);
		assertEquals(10L, acc.getExpiry());
		var k = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
		acc.setKey(k);
		assertEquals(k, acc.getKey());
		final var stringified = "AccountState" + "{" +
				"address=" + Address.RIPEMD160 + ", " +
				"nonce=" + 0 + ", " +
				"balance=" + Wei.of(balance) + ", " +
				"codeHash=" + Hash.EMPTY + ", " +
				"}";
		assertEquals(stringified, acc.toString());

		assertEquals(
				Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
				acc.getOriginalStorageValue(UInt256.ONE)
		);
		assertThrows(UnsupportedOperationException.class,
				() -> acc.storageEntriesFrom(null, 10));

	}

	@Test
	void failsFastIfDeletionsHappenOnStaticWorld() {
		subject = new HederaWorldState(ids, entityAccess, new CodeCache(0, entityAccess), dynamicProperties);
		final var tbd = IdUtils.asAccount("0.0.321");
		final var tbdAddress = EntityIdUtils.asTypedEvmAddress(tbd);
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(tbdAddress)).willReturn(tbdAddress);

		var actualSubject = subject.updater();
		var mockTbdAccount = mock(Address.class);
		actualSubject.getSponsorMap().put(tbdAddress, mockTbdAccount);
		actualSubject.deleteAccount(tbdAddress);

		assertFailsWith(actualSubject::commit,  ResponseCodeEnum.FAIL_INVALID);
	}

	@Test
	void staticInnerUpdaterWorksAsExpected() {
		final var tbd = IdUtils.asAccount("0.0.321");
		final var tbdBalance = 123L;
		final var tbdAddress = EntityIdUtils.asTypedEvmAddress(tbd);
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);

		/* Please note that the subject of this test is the actual inner updater class */
		var actualSubject = subject.updater();
		assertNotNull(actualSubject.updater());
		assertEquals(0, actualSubject.getTouchedAccounts().size());

		/* delete branch */
		given(aliases.resolveForEvm(tbdAddress)).willReturn(tbdAddress);
		given(entityAccess.getBalance(tbd)).willReturn(tbdBalance).willReturn(0L);
		var mockTbdAccount = mock(Address.class);
		actualSubject.getSponsorMap().put(tbdAddress, mockTbdAccount);
		actualSubject.deleteAccount(tbdAddress);
		actualSubject.commit();
		verify(worldLedgers).commit(sigImpactHistorian);
		verify(entityAccess).adjustBalance(tbd, -tbdBalance);
		verify(sigImpactHistorian).markEntityChanged(tbd.getAccountNum());

		actualSubject.getSponsorMap().put(Address.ZERO, mockTbdAccount);
		actualSubject.revert();
		assertEquals(0, actualSubject.getSponsorMap().size());

		actualSubject.addSbhRefund(Gas.of(234L));
		assertEquals(234L, actualSubject.getSbhRefund().toLong());
		actualSubject.revert();
		assertEquals(0, actualSubject.getSbhRefund().toLong());
	}

	@Test
	void updaterGetsHederaAccount() {
		givenNonNullWorldLedgers();

		final var zeroAddress = EntityIdUtils.accountIdFromEvmAddress(Address.ZERO.toArray());
		final var updater = subject.updater();
		// and:
		given(entityAccess.isExtant(zeroAddress)).willReturn(true);
		given(entityAccess.getBalance(zeroAddress)).willReturn(balance);
		given(entityAccess.getProxy(zeroAddress)).willReturn(EntityId.MISSING_ENTITY_ID);
		given(entityAccess.getAutoRenew(zeroAddress)).willReturn(123L);
		// and:
		final var expected = subject.new WorldStateAccount(Address.ZERO, Wei.of(balance), 0, 0, new EntityId());

		// when:
		final var result = updater.getHederaAccount(Address.ZERO);

		// then:
		assertEquals(expected.getAddress(), result.getAddress());
		assertEquals(expected.getBalance(), result.getBalance());
		assertEquals(expected.getProxyAccount(), result.getProxyAccount());
		assertEquals(expected.getExpiry(), result.getExpiry());
		// and:
		verify(entityAccess).isExtant(zeroAddress);
		verify(entityAccess).getBalance(zeroAddress);
		verify(entityAccess).getProxy(zeroAddress);
		verify(entityAccess).getAutoRenew(zeroAddress);
	}

	@Test
	void updaterGetsHederaTokenAccount() {
		givenNonNullWorldLedgers();

		final var zeroAddress = EntityIdUtils.accountIdFromEvmAddress(Address.ZERO.toArray());
		final var updater = subject.updater();
		// and:
		given(entityAccess.isExtant(zeroAddress)).willReturn(true);
		given(entityAccess.isTokenAccount(EntityIdUtils.asTypedEvmAddress(zeroAddress))).willReturn(true);
		given(dynamicProperties.isRedirectTokenCallsEnabled()).willReturn(true);
		// and:
		final var expected = subject.new WorldStateAccount(Address.ZERO, Wei.of(0), 0, 0, new EntityId());

		// when:
		final var result = updater.getHederaAccount(Address.ZERO);

		// then:
		assertEquals(expected.getAddress(), result.getAddress());
		assertEquals(expected.getBalance(), result.getBalance());
		assertEquals(expected.getProxyAccount(), result.getProxyAccount());
		assertEquals(expected.getExpiry(), result.getExpiry());
		assertEquals(-1, result.getNonce());
		assertEquals(TOKEN_CALL_REDIRECT_CONTRACT_BINARY_WITH_ZERO_ADDRESS, result.getCode());
		// and:
		verify(entityAccess).isExtant(zeroAddress);
	}

	@Test
	void updaterAllocatesNewAddress() {
		givenNonNullWorldLedgers();
		given(ids.newContractId(sponsor.asGrpcAccount())).willReturn(contract.asGrpcContract());

		// when:
		final var result = subject.updater().newContractAddress(sponsor.asEvmAddress());

		// then:
		assertEquals(contract.asEvmAddress(), result);
		// and:
		verify(ids).newContractId(sponsor.asGrpcAccount());
	}

	@Test
	void updaterCreatesDeletedAccountUponCommit() {
		givenNonNullWorldLedgers();
		final var tbdAddress = contract.asEvmAddress();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(tbdAddress)).willReturn(tbdAddress);

		final var updater = subject.updater();
		updater.deleteAccount(tbdAddress);
		// and:
		given(entityAccess.getBalance(contract.asGrpcAccount())).willReturn(0L);

		// when:
		updater.commit();

		// then:
		verify(entityAccess).flushStorage();
		verify(worldLedgers).commit(sigImpactHistorian);
		verify(entityAccess).recordNewKvUsageTo(any());
		verify(entityAccess).spawn(any(), anyLong(), any());
	}

	@Test
	void updaterCommitsSuccessfully() {
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		final var newAddress = contract.asEvmAddress();
		given(aliases.resolveForEvm(newAddress)).willReturn(newAddress);

		final var actualSubject = subject.updater();
		final var evmAccount = actualSubject.createAccount(newAddress, 0, Wei.of(balance));
		final var storageKey = UInt256.ONE;
		final var storageValue = UInt256.valueOf(9_876);
		final var secondStorageKey = UInt256.valueOf(2);
		final var secondStorageValue = UInt256.ZERO;
		evmAccount.getMutable().setStorageValue(storageKey, storageValue);
		evmAccount.getMutable().setStorageValue(secondStorageKey, secondStorageValue);
		evmAccount.getMutable().setCode(code);
		// and:
		final var accountID = EntityIdUtils.accountIdFromEvmAddress(contract.asEvmAddress().toArray());
		given(entityAccess.isExtant(accountID)).willReturn(true);

		// when:
		actualSubject.commit();

		// then:
		verify(entityAccess).isExtant(accountID);
		verify(entityAccess).putStorage(accountID, storageKey, storageValue);
		verify(entityAccess).putStorage(accountID, secondStorageKey, secondStorageValue);
		// and:
		verify(entityAccess).storeCode(accountID, code);
	}

	@Test
	void onlyStoresCodeIfUpdated() {
		givenNonNullWorldLedgers();
		given(worldLedgers.aliases()).willReturn(aliases);
		final var someAddress = contract.asEvmAddress();
		given(aliases.resolveForEvm(someAddress)).willReturn(someAddress);

		final var actualSubject = subject.updater();

		final var accountId = accountIdFromEvmAddress(someAddress);
		given(entityAccess.isExtant(accountId)).willReturn(true);
		given(entityAccess.getBalance(accountId)).willReturn(balance);
		given(entityAccess.getProxy(accountId)).willReturn(EntityId.MISSING_ENTITY_ID);
		given(entityAccess.getAutoRenew(accountId)).willReturn(123L);

		actualSubject.getAccount(someAddress);
		actualSubject.commit();

		verify(entityAccess, never()).storeCode(any(), any());
	}

	@Test
	void persistNewlyCreatedContracts() {
		givenNonNullWorldLedgers();
		final var newAddress = contract.asEvmAddress();
		given(worldLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(newAddress)).willReturn(newAddress);

		final var actualSubject = subject.updater();
		actualSubject.createAccount(newAddress, 0, Wei.of(balance));
		// and:
		given(entityAccess.isExtant(contract.asGrpcAccount())).willReturn(false);
		// and:

		// when:
		actualSubject.commit();
		// and:
		final var result = subject.persistProvisionalContractCreations();

		// then:
		verify(entityAccess).isExtant(contract.asGrpcAccount());
		// and:
		assertEquals(1, result.size());
		assertEquals(contract.asGrpcContract(), result.get(0));
	}

	@Test
	void creationPredicateDoesntMatchUnlessContractIdMatchesBackingId() {
		final var receiptBuilder = TxnReceipt.newBuilder()
				.setContractId(contract.asEntityId())
				.setStatus(SUCCESS_LITERAL);
		final var recordBuilder = ExpirableTxnRecord.newBuilder()
				.setReceiptBuilder(receiptBuilder);

		assertFalse(HederaWorldState.isCreationOf(sponsor.asGrpcAccount(), recordBuilder));
	}

	private void givenNonNullWorldLedgers() {
		given(worldLedgers.wrapped()).willReturn(worldLedgers);
		given(entityAccess.worldLedgers()).willReturn(worldLedgers);
	}
}
