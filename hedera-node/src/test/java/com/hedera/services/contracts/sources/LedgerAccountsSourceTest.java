package com.hedera.services.contracts.sources;

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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.AccountState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigInteger;

import static com.hedera.services.ledger.properties.AccountProperty.*;
import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LedgerAccountsSourceTest {
	long balance = 1_234_567L;
	long autoRenew = 1_234L;
	long expiry = 1_234_567L;
	long sendThreshold = 666L;
	long receiveThreshold = 777;
	AccountID proxy = IdUtils.asAccount("1.2.3");
	AccountID target = IdUtils.asAccount("1.2.13257");
	byte[] key = EntityIdUtils.asSolidityAddress(1, 2, 13257);

	HederaLedger ledger;

	LedgerAccountsSource subject;

	@BeforeEach
	void setup() {
		var props = mock(GlobalDynamicProperties.class);
		ledger = mock(HederaLedger.class);

		subject = new LedgerAccountsSource(ledger, props);
	}

	@Test
	public void deleteIsntSupported() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.delete(new byte[0]));
	}

	@Test
	public void flushesIsFalse() {
		// expect:
		assertFalse(subject.flush());
	}

	@Test
	public void logsExpectedForNullAccount() {
		// setup:
		byte[] key = EntityIdUtils.asSolidityAddress(0, 0, 2);
		Logger log = mock(Logger.class);
		LedgerAccountsSource.log = log;

		// expect:
		assertDoesNotThrow(() -> subject.put(key, null));
		// and:
		verify(log).warn("Ignoring null state put to account {}!", "0.0.2");
	}

	@Test
	public void getsNullForMissingKey() {
		given(ledger.exists(target)).willReturn(false);

		// then:
		assertNull(subject.get(key));
	}

	@Test
	public void getsExpectedForPresentKey() {
		// setup:
		boolean deleted = true;
		boolean smartContract = true;
		boolean receiverSigRequired = true;
		MerkleAccount account = mock(MerkleAccount.class);
		given(account.getAutoRenewSecs()).willReturn(autoRenew);
		given(account.getExpiry()).willReturn(expiry);
		given(account.getProxy()).willReturn(EntityId.ofNullableAccountId(IdUtils.asAccount("1.2.3")));
		given(account.isReceiverSigRequired()).willReturn(receiverSigRequired);
		given(account.isDeleted()).willReturn(deleted);
		given(account.isSmartContract()).willReturn(smartContract);
		given(account.getBalance()).willReturn(balance);

		given(ledger.exists(target)).willReturn(true);
		given(ledger.get(target)).willReturn(account);

		// when:
		var evmState = subject.get(key);

		// then:
		assertEquals(autoRenew, evmState.getAutoRenewPeriod());
		assertEquals(BigInteger.valueOf(balance), evmState.getBalance());
		assertEquals(1, evmState.getShardId());
		assertEquals(2, evmState.getRealmId());
		assertEquals(13257, evmState.getAccountNum());
		assertEquals(1, evmState.getProxyAccountShard());
		assertEquals(2, evmState.getProxyAccountRealm());
		assertEquals(3, evmState.getProxyAccountNum());
		assertEquals(BigInteger.ZERO, evmState.getNonce());
		assertEquals(deleted, evmState.isDeleted());
		assertEquals(evmState.isSmartContract(), smartContract);
	}

	@Test
	public void updatesExtantAccountOnPut() {
		// setup:
		long newExpiry = 1_234_567L;
		boolean newDeleted = true;
		long oldBalance = 1_000L;
		long newBalance = 1_234;
		// and:
		var evmState = new AccountState(BigInteger.ZERO, BigInteger.valueOf(newBalance));
		evmState.setDeleted(newDeleted);
		evmState.setExpirationTime(newExpiry);
		// and:
		InOrder inOrder = inOrder(ledger);
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> txnLedger = mock(TransactionalLedger.class);

		given(ledger.getBalance(target)).willReturn(oldBalance);
		given(ledger.exists(target)).willReturn(true);

		// when:
		subject.put(key, evmState);

		// then:
		inOrder.verify(ledger).adjustBalance(target, newBalance - oldBalance);
		inOrder.verify(ledger).customize(argThat(target::equals), captor.capture());
		// and:
		captor.getValue().customize(target, txnLedger);
		verify(txnLedger).set(target, IS_DELETED, newDeleted);
		verify(txnLedger).set(target, EXPIRY, newExpiry);
	}

	@Test
	public void createsNewAccountWithDefaultThresholds() throws DecoderException {
		// setup:
		long newBalance = 1_234;
		Key legacyPlaceholder = Key.newBuilder().setContractID(EntityIdUtils.asContract(target)).build();
		JKey expectedKey = JKey.mapKey(legacyPlaceholder);
		// and:
		var evmState = new AccountState(BigInteger.ZERO, BigInteger.valueOf(newBalance));
		evmState.setDeleted(false);
		evmState.setExpirationTime(expiry);
		evmState.setAutoRenewPeriod(autoRenew);
		evmState.setProxyAccountShard(1);
		evmState.setProxyAccountRealm(2);
		evmState.setProxyAccountNum(3);
		evmState.setBalance(BigInteger.valueOf(newBalance));
		// and:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> txnLedger = mock(TransactionalLedger.class);

		given(ledger.exists(target)).willReturn(false);

		// when:
		subject.put(key, evmState);

		// then:
		verify(ledger).spawn(argThat(target::equals), longThat(l -> l == newBalance), captor.capture());
		// and:
		captor.getValue().customize(target, txnLedger);
		verify(txnLedger).set(target, EXPIRY, expiry);
		verify(txnLedger).set(
				argThat(target::equals),
				argThat(KEY::equals),
				argThat(k -> expectedKey.toString().equals(k.toString())));
		verify(txnLedger).set(target, IS_SMART_CONTRACT, true);
		verify(txnLedger).set(target, AUTO_RENEW_PERIOD, autoRenew);
		verify(txnLedger).set(target, PROXY, EntityId.ofNullableAccountId(proxy));
		verify(txnLedger).set(target, MEMO, "");
	}

	@Test
	public void createsNewAccountWithGivenThresholds() throws DecoderException {
		// setup:
		long newBalance = 1_234;
		Key legacyPlaceholder = Key.newBuilder().setContractID(EntityIdUtils.asContract(target)).build();
		JKey expectedKey = JKey.mapKey(legacyPlaceholder);
		// and:
		var evmState = new AccountState(BigInteger.ZERO, BigInteger.valueOf(newBalance));
		evmState.setDeleted(false);
		evmState.setExpirationTime(expiry);
		evmState.setAutoRenewPeriod(autoRenew);
		evmState.setProxyAccountShard(1);
		evmState.setProxyAccountRealm(2);
		evmState.setProxyAccountNum(3);
		evmState.setBalance(BigInteger.valueOf(newBalance));
		evmState.setSenderThreshold(sendThreshold);
		evmState.setReceiverThreshold(receiveThreshold);
		// and:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> txnLedger = mock(TransactionalLedger.class);

		given(ledger.exists(target)).willReturn(false);

		// when:
		subject.put(key, evmState);

		// then:
		verify(ledger).spawn(argThat(target::equals), longThat(l -> l == newBalance), captor.capture());
		// and:
		captor.getValue().customize(target, txnLedger);
		verify(txnLedger).set(target, EXPIRY, expiry);
		verify(txnLedger).set(
				argThat(target::equals),
				argThat(KEY::equals),
				argThat(k -> expectedKey.toString().equals(k.toString())));
		verify(txnLedger).set(target, IS_SMART_CONTRACT, true);
		verify(txnLedger).set(target, AUTO_RENEW_PERIOD, autoRenew);
		verify(txnLedger).set(target, PROXY, EntityId.ofNullableAccountId(proxy));
		verify(txnLedger).set(target, MEMO, "");
	}
}
