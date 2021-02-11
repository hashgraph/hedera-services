package com.hedera.services.sigs.factories;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
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
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.pk;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.sig;
import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

public class SigFactoryCreatorTest {
	FCMap<MerkleEntityId, MerkleSchedule> scheduledTxns;

	SignedTxnAccessor accessor;
	ScheduleID toSign = IdUtils.asSchedule("0.0.75231");
	MerkleSchedule scheduled;

	SigFactoryCreator subject;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		scheduledTxns = (FCMap<MerkleEntityId, MerkleSchedule>) mock(FCMap.class);

		subject = new SigFactoryCreator(() -> scheduledTxns);
	}

	@Test
	public void usesEmptyBytesForScheduledIfIdMissing() {
		// setup:
		var expectedSig = new TransactionSignature(
				dataFrom(SigFactoryCreator.MISSING_SCHEDULED_TXN_BYTES, sig),
				0, sig.length,
				pk, 0, pk.length,
				sig.length, 0);
		var schid = fromScheduleId(toSign);

		given(scheduledTxns.containsKey(schid)).willReturn(false);

		givenScopedTxn(signingScheduled(toSign));

		// when:
		var factory = subject.createScopedFactory(accessor);

		// then:
		assertThat(factory, instanceOf(ScheduleBodySigningSigFactory.class));
		// and:
		Assertions.assertEquals(factory.createForScheduled(
				ByteString.copyFrom(pk),
				ByteString.copyFrom(sig)), expectedSig);
	}

	@Test
	public void usesLinkedForScheduleSign() {
		// setup:
		var innerTxn = withoutLinkedSchedule();
		var expectedSig = new TransactionSignature(
				dataFrom(innerTxn.toByteArray(), sig),
				0, sig.length,
				pk, 0, pk.length,
				sig.length, innerTxn.toByteArray().length);
		scheduled = mock(MerkleSchedule.class);
		var schid = fromScheduleId(toSign);

		given(scheduled.transactionBody()).willReturn(innerTxn.toByteArray());
		given(scheduledTxns.containsKey(schid)).willReturn(true);
		given(scheduledTxns.get(schid)).willReturn(scheduled);

		givenScopedTxn(signingScheduled(toSign));

		// when:
		var factory = subject.createScopedFactory(accessor);

		// then:
		assertThat(factory, instanceOf(ScheduleBodySigningSigFactory.class));
		// and:
		Assertions.assertEquals(factory.createForScheduled(
				ByteString.copyFrom(pk),
				ByteString.copyFrom(sig)), expectedSig);
	}

	@Test
	public void usesEmbeddedForScheduleCreate() {
		// setup:
		var innerTxn = withoutLinkedSchedule();
		var expScheduleSig = new TransactionSignature(
				dataFrom(innerTxn.toByteArray(), sig),
				0, sig.length,
				pk, 0, pk.length,
				sig.length, innerTxn.toByteArray().length);

		givenScopedTxn(createdScheduled(innerTxn));

		// when:
		var factory = subject.createScopedFactory(accessor);

		// then:
		assertThat(factory, instanceOf(ScheduleBodySigningSigFactory.class));
		// and:
		Assertions.assertEquals(factory.createForScheduled(
				ByteString.copyFrom(pk),
				ByteString.copyFrom(sig)), expScheduleSig);
	}

	@Test
	public void usesBodyBytesForNonScheduleTxn() {
		givenScopedTxn(withoutLinkedSchedule());

		// when:
		var factory = subject.createScopedFactory(accessor);

		// then:
		assertThat(factory, instanceOf(BodySigningSigFactory.class));
	}

	private byte[] dataFrom(byte[] signed, byte[] sig) {
		byte[] data = new byte[signed.length + sig.length];
		System.arraycopy(sig, 0, data, 0, sig.length);
		System.arraycopy(signed, 0, data, sig.length, signed.length);
		return data;
	}

	private void givenScopedTxn(TransactionBody txn) {
		accessor = SignedTxnAccessor.uncheckedFrom(Transaction.newBuilder()
				.setSignedTransactionBytes(SignedTransaction.newBuilder()
						.setBodyBytes(txn.toByteString()).build().toByteString())
				.build());
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
}
