package com.hedera.services.sigs.sourcing;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ScopedSigBytesProviderTest {
	SignedTxnAccessor accessor;

	ScopedSigBytesProvider subject;

	@Test
	void usesStandardForNonScheduleTxn() throws InvalidProtocolBufferException {
		givenSubject(withoutLinkedSchedule());

		// expect:
		assertThat(subject.delegate, instanceOf(SigMapPubKeyToSigBytes.class));
		// and:
		assertSame(subject.payerSigBytesFor(null), subject.otherPartiesSigBytesFor(null));
		assertSame(subject.otherPartiesSigBytesFor(null), subject.allPartiesSigBytesFor(null));
	}

	@Test
	void usesScheduledForScheduleCreate() throws InvalidProtocolBufferException {
		givenSubject(createdScheduled(withoutLinkedSchedule()));

		// expect:
		assertThat(subject.delegate, instanceOf(ScheduledPubKeyToSigBytes.class));
		// and:
		assertSame(subject.payerSigBytesFor(null), subject.otherPartiesSigBytesFor(null));
		assertSame(subject.otherPartiesSigBytesFor(null), subject.allPartiesSigBytesFor(null));
	}

	@Test
	void usesScheduledForScheduleSign() throws InvalidProtocolBufferException {
		// setup:
		var schid = IdUtils.asSchedule("0.0.75231");

		givenSubject(signingScheduled(schid));

		// expect:
		assertThat(subject.delegate, instanceOf(ScheduledPubKeyToSigBytes.class));
		// and:
		assertSame(subject.payerSigBytesFor(null), subject.otherPartiesSigBytesFor(null));
		assertSame(subject.otherPartiesSigBytesFor(null), subject.allPartiesSigBytesFor(null));
	}

	private void givenSubject(TransactionBody txn) throws InvalidProtocolBufferException {
		accessor = new SignedTxnAccessor(Transaction.newBuilder()
				.setSignedTransactionBytes(SignedTransaction.newBuilder()
						.setBodyBytes(txn.toByteString())
						.build().toByteString())
				.build());
		subject = new ScopedSigBytesProvider(accessor);
	}

	private TransactionBody withoutLinkedSchedule() {
		return TransactionBody.newBuilder()
				.setMemo("You won't want to hear this.")
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(123L)
										.setAccountID(AccountID.newBuilder().setAccountNum(75231)))))
				.build();
	}

	private TransactionBody createdScheduled(TransactionBody inner) {
		return TransactionBody.newBuilder()
				.setMemo("You won't want to hear this.")
				.setScheduleCreate(ScheduleCreateTransactionBody.newBuilder()
						.setTransactionBody(inner.toByteString()))
				.build();
	}

	private TransactionBody signingScheduled(ScheduleID schid) {
		return TransactionBody.newBuilder()
				.setMemo("You won't want to hear this.")
				.setScheduleSign(ScheduleSignTransactionBody.newBuilder()
						.setScheduleID(schid))
				.build();
	}

}
