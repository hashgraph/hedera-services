package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RenewalRecordsHelperTest {
	private long fee = 1_234L;
	private long newExpiry = 1_234_567L + 7776000L;
	private final Instant instantNow = Instant.ofEpochSecond(1_234_567L);
	private final AccountID removedId = IdUtils.asAccount("1.2.3");
	private final AccountID funding = IdUtils.asAccount("0.0.98");
	private final MerkleEntityId keyId = MerkleEntityId.fromAccountId(removedId);

	@Mock
	private ServicesContext ctx;
	@Mock
	private RecordStreamManager recordStreamManager;

	private RenewalRecordsHelper subject;

	@BeforeEach
	void setUp() {
		subject = new RenewalRecordsHelper(ctx, recordStreamManager, new MockGlobalDynamicProps());
	}

	@Test
	void mustBeInCycleToStream() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.streamCryptoRenewal(keyId, 1L, 2L));
	}

	@Test
	void streamsExpectedRemovalRecord() {
		// setup:
		final var removalTime = instantNow.plusNanos(1);
		final var rso = expectedRso(cryptoRemovalRecord(removedId, removalTime, removedId), 1);

		// when:
		subject.beginRenewalCycle(instantNow);
		// and:
		subject.streamCryptoRemoval(keyId);

		// then:
		verify(ctx).updateRecordRunningHash(any());
		verify(recordStreamManager).addRecordStreamObject(rso);

		// and when:
		subject.endRenewalCycle();

		// then:
		assertNull(subject.getCycleStart());
		assertEquals(0, subject.getConsensusNanosIncr());
	}

	@Test
	void streamsExpectedRenewalRecord() {
		// setup:
		final var renewalTime = instantNow.plusNanos(1);
		final var rso = expectedRso(
				cryptoRenewalRecord(removedId, renewalTime, removedId, fee, newExpiry, funding), 1);

		// when:
		subject.beginRenewalCycle(instantNow);
		// and:
		subject.streamCryptoRenewal(keyId, fee, newExpiry);

		// then:
		verify(ctx).updateRecordRunningHash(any());
		verify(recordStreamManager).addRecordStreamObject(rso);

		// and when:
		subject.endRenewalCycle();

		// then:
		assertNull(subject.getCycleStart());
		assertEquals(0, subject.getConsensusNanosIncr());
	}

	private RecordStreamObject expectedRso(TransactionRecord record, int nanosOffset) {
		return new RecordStreamObject(record, Transaction.getDefaultInstance(), instantNow.plusNanos(nanosOffset));
	}

	private TransactionRecord cryptoRemovalRecord(AccountID accountRemoved, Instant removedAt, AccountID autoRenewAccount) {
		TransactionReceipt receipt = TransactionReceipt.newBuilder()
				.setAccountID(accountRemoved)
				.build();

		TransactionID transactionID = TransactionID.newBuilder()
				.setAccountID(autoRenewAccount)
				.build();

		return TransactionRecord.newBuilder()
				.setReceipt(receipt)
				.setConsensusTimestamp(asTimestamp(removedAt))
				.setTransactionID(transactionID)
				.setMemo(String.format("Entity %s was automatically deleted.", asLiteralString(accountRemoved)))
				.setTransactionFee(0L)
				.build();
	}

	private TransactionRecord cryptoRenewalRecord(
			AccountID accountRenewed,
			Instant renewedAt,
			AccountID autoRenewAccount,
			long fee,
			long newExpirationTime,
			AccountID feeCollector
	) {
		TransactionReceipt receipt = TransactionReceipt.newBuilder().setAccountID(accountRenewed).build();
		TransactionID transactionID = TransactionID.newBuilder().setAccountID(autoRenewAccount).build();
		String memo = String.format("Entity %s was automatically renewed. New expiration time: %d.",
				asLiteralString(accountRenewed),
				newExpirationTime);
		AccountAmount payerAmount = AccountAmount.newBuilder()
				.setAccountID(autoRenewAccount)
				.setAmount(-1 * fee)
				.build();
		AccountAmount payeeAmount = AccountAmount.newBuilder()
				.setAccountID(feeCollector)
				.setAmount(fee)
				.build();
		TransferList transferList = TransferList.newBuilder()
				.addAccountAmounts(payeeAmount)
				.addAccountAmounts(payerAmount)
				.build();
		return TransactionRecord.newBuilder()
				.setReceipt(receipt)
				.setConsensusTimestamp(asTimestamp(renewedAt))
				.setTransactionID(transactionID)
				.setMemo(memo)
				.setTransactionFee(fee)
				.setTransferList(transferList)
				.build();
	}
}
