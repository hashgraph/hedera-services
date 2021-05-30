package com.hedera.services.contracts.sources;

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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.ethereum.core.AccountState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import javax.inject.Inject;
import java.math.BigInteger;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.longThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@ExtendWith(LogCaptureExtension.class)
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

	@Inject
	private LogCaptor logCaptor;
	@LoggingSubject
	private LedgerAccountsSource subject;

	@BeforeEach
	void setup() {
		ledger = mock(HederaLedger.class);

		subject = new LedgerAccountsSource(ledger);
	}

	@Test
	void deleteIsntSupported() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.delete(new byte[0]));
	}

	@Test
	void flushesIsFalse() {
		// expect:
		assertFalse(subject.flush());
	}

	@Test
	void logsExpectedForNullAccount() {
		// setup:
		byte[] key = EntityIdUtils.asSolidityAddress(0, 0, 2);

		// expect:
		assertDoesNotThrow(() -> subject.put(key, null));
		// and:
		assertThat(logCaptor.warnLogs(), contains("Ignoring null state put to account 0.0.2!"));
	}

	@Test
	void getsNullForMissingKey() {
		given(ledger.exists(target)).willReturn(false);

		// then:
		assertNull(subject.get(key));
	}

	@Test
	void getsNullForDetachedAccount() {
		given(ledger.exists(target)).willReturn(true);
		given(ledger.isDetached(target)).willReturn(true);

		// then:
		assertNull(subject.get(key));
	}

	@Test
	void getsExpectedForPresentKey() {
		// setup:
		boolean deleted = true;
		boolean smartContract = true;
		boolean receiverSigRequired = true;

		given(ledger.exists(target)).willReturn(true);
		given(ledger.expiry(target)).willReturn(expiry);
		given(ledger.getBalance(target)).willReturn(balance);
		given(ledger.autoRenewPeriod(target)).willReturn(autoRenew);
		given(ledger.isDeleted(target)).willReturn(deleted);
		given(ledger.isSmartContract(target)).willReturn(smartContract);
		given(ledger.isReceiverSigRequired(target)).willReturn(receiverSigRequired);
		given(ledger.proxy(target)).willReturn(EntityId.fromGrpcAccountId(IdUtils.asAccount("1.2.3")));

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
	void updatesExtantAccountOnPut() {
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
	void createsNewAccountWithDefaultThresholds() throws DecoderException {
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
		verify(txnLedger).set(target, PROXY, EntityId.fromGrpcAccountId(proxy));
		verify(txnLedger).set(target, MEMO, "");
	}

	@Test
	void createsNewAccountWithGivenThresholds() throws DecoderException {
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
		verify(txnLedger).set(target, PROXY, EntityId.fromGrpcAccountId(proxy));
		verify(txnLedger).set(target, MEMO, "");
	}
}
