package com.hedera.services.state.merkle;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ReplScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class MerkleScheduleTest {
	byte[] fpk = "firstPretendKey".getBytes();
	byte[] spk = "secondPretendKey".getBytes();
	byte[] tpk = "thirdPretendKey".getBytes();

	long expiry = 1_234_567L, otherExpiry = 1_567_234L;
	byte[] transactionBody, otherTransactionBody;
	String entityMemo = "Just some memo again", otherEntityMemo = "Yet another memo";
	EntityId payer = new EntityId(4, 5, 6), otherPayer = new EntityId(4, 5, 5);
	EntityId schedulingAccount = new EntityId(1, 2, 3);
	RichInstant schedulingTXValidStart = new RichInstant(123, 456);
	RichInstant otherSchedulingTXValidStart = new RichInstant(456, 789);
	JKey adminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	JKey otherAdminKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
	List<byte[]> signatories;

	MerkleSchedule subject;
	MerkleSchedule other;

	@BeforeEach
	public void setup() {
		otherTransactionBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(MiscUtils.asTimestamp(Instant.ofEpochSecond(2L))))
				.build()
				.toByteArray();

		signatories = new ArrayList<>();
		signatories.addAll(List.of(fpk, spk, tpk));

		subject = MerkleSchedule.from(bodyBytes, expiry);

		serdes = mock(DomainSerdes.class);
		MerkleSchedule.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		MerkleSchedule.serdes = new DomainSerdes();
	}

	@Test
	public void factoryWorks() {
		// expect:
		assertFalse(subject.isDeleted());
		assertFalse(subject.isExecuted());
		assertEquals(payer, subject.payer());
		assertEquals(expiry, subject.expiry());
		assertEquals(schedulingAccount, subject.schedulingAccount());
		assertEquals(entityMemo, subject.memo().get());
		assertEquals(adminKey.toString(), subject.adminKey().get().toString());
		assertEquals(schedulingTXValidStart, subject.schedulingTXValidStart());
		assertEquals(scheduledTxn, subject.scheduledTxn());
		assertEquals(expectedSignedTxn(), subject.replAsScheduledTransaction());
		assertArrayEquals(bodyBytes, subject.bodyBytes());
	}

	@Test
	public void factoryTranslatesImpossibleParseError() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> MerkleSchedule.from("NONSENSE".getBytes(), 0L));
	}

	@Test
	void translatesInvariantFailure() {
		assertThrows(IllegalStateException.class, subject::scheduledTransactionId);
	}

	@Test
	void notaryWorks() {
		// given:
		assertFalse(subject.hasValidEd25519Signature(fpk));
		assertFalse(subject.hasValidEd25519Signature(spk));
		assertFalse(subject.hasValidEd25519Signature(tpk));

		// when:
		subject.witnessValidEd25519Signature(fpk);
		subject.witnessValidEd25519Signature(tpk);

		// then:
		assertTrue(subject.hasValidEd25519Signature(fpk));
		assertFalse(subject.hasValidEd25519Signature(spk));
		assertTrue(subject.hasValidEd25519Signature(tpk));
	}

	@Test
	void witnessOnlyTrueIfNewSignatory() {
		// expect:
		assertTrue(subject.witnessValidEd25519Signature(fpk));
		assertFalse(subject.witnessValidEd25519Signature(fpk));
	}

	@Test
	public void releaseIsNoop() {
		// expect:
		assertDoesNotThrow(subject::release);
	}

	@Test
	public void signatoriesArePublished() {
		// given:
		subject.witnessValidEd25519Signature(fpk);
		subject.witnessValidEd25519Signature(spk);
		subject.witnessValidEd25519Signature(tpk);

		// expect:
		assertTrue(subject.signatories().containsAll(signatories));
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(serdes, out);

		// given:
		subject.witnessValidEd25519Signature(fpk);
		subject.witnessValidEd25519Signature(spk);
		subject.markDeleted();

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeLong(expiry);
		inOrder.verify(out).writeByteArray(bodyBytes);
		inOrder.verify(out).writeBoolean(false);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeInt(2);
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, fpk)));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, spk)));
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);
		// and:
		subject.witnessValidEd25519Signature(fpk);
		subject.witnessValidEd25519Signature(spk);
		subject.markExecuted();

		given(fin.readLong()).willReturn(subject.expiry());
		given(fin.readInt()).willReturn(2);
		given(fin.readByteArray(Integer.MAX_VALUE)).willReturn(bodyBytes);
		given(fin.readByteArray(MerkleSchedule.NUM_ED25519_PUBKEY_BYTES))
				.willReturn(fpk)
				.willReturn(spk);
		given(fin.readBoolean())
				.willReturn(true)
				.willReturn(false);
		// and:
		var read = new MerkleSchedule();

		// when:
		read.deserialize(fin, MerkleToken.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	public void nonessentialFieldsDontAffectIdentity() {
		// given:
		var diffBodyBytes = parentTxn.toBuilder()
				.setTransactionID(parentTxn.getTransactionID().toBuilder()
						.setAccountID(otherPayer.toGrpcAccountId())
						.setTransactionValidStart(MiscUtils.asTimestamp(otherSchedulingTXValidStart.toJava())))
				.setReplScheduleCreate(parentTxn.getReplScheduleCreate().toBuilder()
						.setPayerAccountID(otherPayer.toGrpcAccountId()))
				.build().toByteArray();
		other = MerkleSchedule.from(diffBodyBytes, expiry + 1);
		other.markExecuted();
		other.markDeleted();
		other.witnessValidEd25519Signature(fpk);

		// expect:
		assertEquals(subject, other);
		// and:
		assertEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void differentAdminKeysNotIdentical() {
		// setup:
		var bodyBytesDiffAdminKey = parentTxn.toBuilder()
				.setReplScheduleCreate(parentTxn.getReplScheduleCreate().toBuilder()
						.setAdminKey(MiscUtils.asKeyUnchecked(otherAdminKey)))
				.build().toByteArray();

		// given:
		other = MerkleSchedule.from(bodyBytesDiffAdminKey, expiry);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void differentMemosNotIdentical() {
		// setup:
		var bodyBytesDiffMemo = parentTxn.toBuilder()
				.setReplScheduleCreate(parentTxn.getReplScheduleCreate().toBuilder()
						.setMemo(otherEntityMemo))
				.build().toByteArray();

		// given:
		other = MerkleSchedule.from(bodyBytesDiffMemo, expiry);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void differentScheduledTxnNotIdentical() {
		// setup:
		var bodyBytesDiffScheduledTxn = parentTxn.toBuilder()
				.setReplScheduleCreate(parentTxn.getReplScheduleCreate().toBuilder()
						.setScheduledTransactionBody(scheduledTxn.toBuilder().setMemo("Slightly different!")))
				.build().toByteArray();

		// given:
		other = MerkleSchedule.from(bodyBytesDiffScheduledTxn, expiry);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void validToString() {
		// given:
		subject.witnessValidEd25519Signature(fpk);
		subject.witnessValidEd25519Signature(spk);
		subject.witnessValidEd25519Signature(tpk);
		subject.markDeleted();
		subject.markExecuted();

		// and:
		var expected = "MerkleSchedule{"
				+ "expiry=" + expiry + ", "
				+ "executed=" + true + ", "
				+ "deleted=" + true + ", "
				+ "transactionBody=" + hex(transactionBody) + ", "
				+ "memo=" + entityMemo + ", "
				+ "payer=" + payer.toAbbrevString() + ", "
				+ "schedulingAccount=" + schedulingAccount + ", "
				+ "schedulingTXValidStart=" + schedulingTXValidStart
				+ ", " + "signatories=[" + signatoriesToString() + "], "
				+ "adminKey=" + describe(adminKey) + "}";

		// expect:
		assertEquals(expected, subject.toString());
	}

	@Test
	public void validEqualityChecks() {
		// expect:
		assertEquals(subject, subject);
		// and:
		assertNotEquals(subject, null);
		// and:
		assertNotEquals(subject, new Object());
	}

	@Test
	public void validVersion() {
		// expect:
		assertEquals(MerkleSchedule.MERKLE_VERSION, subject.getVersion());
	}

	@Test
	public void validRuntimeConstructableID() {
		// expect:
		assertEquals(MerkleSchedule.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void validIsLeaf() {
		// expect:
		assertTrue(subject.isLeaf());
	}

	@Test
	public void copyWorks() {
		// setup:
		subject.markDeleted();
		subject.markExecuted();
		subject.witnessValidEd25519Signature(tpk);

		// given:
		var copySubject = subject.copy();

		// expect:
		assertTrue(copySubject.isDeleted());
		assertTrue(copySubject.isExecuted());
		assertTrue(copySubject.hasValidEd25519Signature(tpk));
		// and:
		assertEquals(subject.toString(), copySubject.toString());
		assertNotSame(subject.signatories(), copySubject.signatories());
		// and:
		assertEquals(payer, copySubject.payer());
		assertEquals(expiry, copySubject.expiry());
		assertEquals(schedulingAccount, copySubject.schedulingAccount());
		assertEquals(entityMemo, copySubject.memo().get());
		assertEquals(adminKey.toString(), copySubject.adminKey().get().toString());
		assertEquals(schedulingTXValidStart, copySubject.schedulingTXValidStart());
		assertEquals(scheduledTxn, copySubject.scheduledTxn());
		assertEquals(expectedSignedTxn(), copySubject.replAsScheduledTransaction());
		assertArrayEquals(bodyBytes, copySubject.bodyBytes());
	}

	private String signatoriesToString() {
		return signatories.stream().map(Hex::encodeHexString).collect(Collectors.joining(", "));
	}

	private static final long fee = 123L;
	private static final String scheduledTxnMemo = "Wait for me!";
	private static final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(fee)
			.setMemo(scheduledTxnMemo)
			.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
					.setDeleteAccountID(IdUtils.asAccount("0.0.2"))
					.setTransferAccountID(IdUtils.asAccount("0.0.75231")))
			.build();

	ReplScheduleCreateTransactionBody creation = ReplScheduleCreateTransactionBody.newBuilder()
			.setAdminKey(MiscUtils.asKeyUnchecked(adminKey))
			.setPayerAccountID(payer.toGrpcAccountId())
			.setMemo(entityMemo)
			.setScheduledTransactionBody(scheduledTxn)
			.build();
	TransactionBody parentTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(MiscUtils.asTimestamp(schedulingTXValidStart.toJava()))
					.setAccountID(schedulingAccount.toGrpcAccountId())
					.build())
			.setReplScheduleCreate(creation)
			.build();
	private final byte[] bodyBytes = parentTxn.toByteArray();

	private Transaction expectedSignedTxn() {
		TransactionID expectedId = TransactionID.newBuilder()
				.setAccountID(schedulingAccount.toGrpcAccountId())
				.setTransactionValidStart(asTimestamp(schedulingTXValidStart.toJava()))
				.setScheduled(true)
				.build();
		return Transaction.newBuilder()
				.setSignedTransactionBytes(
						SignedTransaction.newBuilder()
								.setBodyBytes(
										TransactionBody.newBuilder()
												.mergeFrom(MiscUtils.asOrdinary(scheduledTxn))
												.setTransactionID(expectedId)
												.build().toByteString())
								.build().toByteString())
				.build();
	}

	public static TransactionBody scheduleCreateTxnWith(
			Key scheduleAdminKey,
			String scheduleMemo,
			AccountID payer,
			AccountID scheduler,
			Timestamp validStart
	) {
		ReplScheduleCreateTransactionBody creation = ReplScheduleCreateTransactionBody.newBuilder()
				.setAdminKey(scheduleAdminKey)
				.setPayerAccountID(payer)
				.setMemo(scheduleMemo)
				.setScheduledTransactionBody(scheduledTxn)
				.build();
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(validStart)
						.setAccountID(scheduler)
						.build())
				.setReplScheduleCreate(creation)
				.build();
	}
}
