package com.hedera.services.utils.accessors;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
@ExtendWith(MockitoExtension.class)
class CryptoUpdateAccessorTest {
	@Mock
	AliasManager aliasManager;

	private final long targetNum = 345L;
	private final long proxyNum = 456L;
	private final long autoRenewDuration = 7776000L;
	private final long expirationTime = Instant.now().getEpochSecond() + autoRenewDuration;
	private final int maxAutoAssociations = 10;
	private final EntityNum targetEntity = EntityNum.fromLong(targetNum);
	private final EntityNum proxyEntity = EntityNum.fromLong(proxyNum);
	private final AccountID target = asAccount("0.0." + targetNum);
	private final AccountID aliasedTarget = asAccountWithAlias("The Ring has awoken");
	private final AccountID proxy = asAccount("0.0." + proxyNum);
	private final AccountID aliasedProxy = asAccountWithAlias("It has heard its masters call");
	private final Key aKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private final String memo = "May it be a light to you in dark places";

	private CryptoUpdateAccessor subject;
	private SwirldTransaction accountUpdateTxn;

	@Test
	void fetchesDataAsExpected() throws InvalidProtocolBufferException {
		setUpWith(target, proxy);
		when(aliasManager.unaliased(target)).thenReturn(targetEntity);
		when(aliasManager.unaliased(proxy)).thenReturn(proxyEntity);

		subject = new CryptoUpdateAccessor(accountUpdateTxn, aliasManager);

		validate();
	}

	@Test
	void fetchesAliasedDataAsExpected() throws InvalidProtocolBufferException {
		setUpWith(aliasedTarget, aliasedProxy);
		when(aliasManager.unaliased(aliasedTarget)).thenReturn(targetEntity);
		when(aliasManager.unaliased(aliasedProxy)).thenReturn(proxyEntity);

		subject = new CryptoUpdateAccessor(accountUpdateTxn, aliasManager);

		validate();
	}

	@Test
	void fetchesMissingAliasAsExpected() throws InvalidProtocolBufferException {
		setUpWith(aliasedTarget, aliasedProxy);
		when(aliasManager.unaliased(aliasedTarget)).thenReturn(EntityNum.MISSING_NUM);
		when(aliasManager.unaliased(aliasedProxy)).thenReturn(EntityNum.MISSING_NUM);

		subject = new CryptoUpdateAccessor(accountUpdateTxn, aliasManager);

		assertEquals(0L, subject.getTarget().getAccountNum());
		assertEquals(0L, subject.getProxy().getAccountNum());
	}

	private void validate() {
		assertEquals(target, subject.getTarget());
		assertEquals(maxAutoAssociations, subject.getMaxAutomaticTokenAssociations());
		assertTrue(subject.hasKey());
		assertEquals(aKey, subject.getKey());
		assertEquals(proxy, subject.getProxy());
		assertEquals(autoRenewDuration, subject.getAutoRenewPeriod());
		assertTrue(subject.hasProxy());
		assertFalse(subject.getReceiverSigRequired());
		assertEquals(proxy, subject.getProxy());
		assertEquals(memo, subject.getMemo());
		assertTrue(subject.hasAutoRenewPeriod());
		assertTrue(subject.hasExpirationTime());
		assertEquals(expirationTime, subject.getExpirationTime().getSeconds());
		assertTrue(subject.hasReceiverSigRequiredWrapper());
		assertTrue(subject.getReceiverSigRequiredWrapperValue());
	}

	private void setUpWith(AccountID target, AccountID proxy) {
		final var txnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(target).build())
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
						.setAccountIDToUpdate(target)
						.setProxyAccountID(proxy)
						.setExpirationTime(Timestamp.newBuilder().setSeconds(expirationTime).build())
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewDuration))
						.setMaxAutomaticTokenAssociations(Int32Value.of(maxAutoAssociations))
						.setReceiverSigRequiredWrapper(BoolValue.of(true))
						.setKey(aKey)
						.setMemo(StringValue.of(memo))
						.build())
				.build();
		accountUpdateTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build().toByteArray());
	}
}
