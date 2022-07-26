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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.utility.CommonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.describe;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MerkleScheduleTest {
	private static final byte[] fpk = "firstPretendKey".getBytes();
	private static final byte[] spk = "secondPretendKey".getBytes();
	private static final byte[] tpk = "thirdPretendKey".getBytes();
	private static final long expiry = 1_234_567L;
	private static final String entityMemo = "Just some memo again";
	private static final String otherEntityMemo = "Yet another memo";
	private static final EntityId payer = new EntityId(4, 5, 6);
	private static final EntityId otherPayer = new EntityId(4, 5, 5);
	private static final EntityId schedulingAccount = new EntityId(1, 2, 3);
	private static final Instant resolutionTime = Instant.ofEpochSecond(1_234_567L);
	private static final Timestamp grpcResolutionTime = RichInstant.fromJava(resolutionTime).toGrpc();
	private static final RichInstant schedulingTXValidStart = new RichInstant(123, 456);
	private static final RichInstant otherSchedulingTXValidStart = new RichInstant(456, 789);
	private static final JKey adminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	private static final JKey otherAdminKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
	private static final int number = 123_456;

	private List<byte[]> signatories;
	private MerkleSchedule subject;

	@BeforeEach
	void setup() {
		signatories = new ArrayList<>();
		signatories.addAll(List.of(fpk, spk, tpk));

		subject = MerkleSchedule.from(bodyBytes, expiry);
		subject.setKey(EntityNum.fromInt(number));
	}

	public static void assertEqualSchedules(final MerkleSchedule a, final MerkleSchedule b) {
		assertEquals(a.expiry(), b.expiry());
		assertArrayEquals(a.bodyBytes(), b.bodyBytes());
		assertEquals(a.isDeleted(), b.isDeleted());
		assertEquals(a.isExecuted(), b.isExecuted());
		assertEquals(a.getResolutionTime(), b.getResolutionTime());
		final var aSigs = a.signatories();
		final var bSigs = b.signatories();
		assertEquals(aSigs.size(), bSigs.size());
		for (int i = 0, n = aSigs.size(); i < n; i++) {
			assertArrayEquals(aSigs.get(i), bSigs.get(i));
		}
		assertEquals(a.getKey(), b.getKey());
	}

	@Test
	void recognizesMissingFunction() {
		final var noneBodyBytes = parentTxn.toBuilder()
				.setScheduleCreate(ScheduleCreateTransactionBody.getDefaultInstance())
				.build()
				.toByteArray();

		subject = MerkleSchedule.from(noneBodyBytes, expiry);

		assertEquals(HederaFunctionality.NONE, subject.scheduledFunction());
	}

	@Test
	void factoryWorks() {
		assertFalse(subject.isDeleted());
		assertFalse(subject.isExecuted());
		assertEquals(payer, subject.payer());
		assertEquals(expiry, subject.expiry());
		assertEquals(schedulingAccount, subject.schedulingAccount());
		assertEquals(entityMemo, subject.memo().get());
		assertEquals(adminKey.toString(), subject.adminKey().get().toString());
		assertEquals(schedulingTXValidStart, subject.schedulingTXValidStart());
		assertEquals(scheduledTxn, subject.scheduledTxn());
		assertEquals(ordinaryVersionOfScheduledTxn, subject.ordinaryViewOfScheduledTxn());
		assertEquals(expectedSignedTxn(), subject.asSignedTxn());
		assertArrayEquals(bodyBytes, subject.bodyBytes());
		assertEquals(number, subject.getKey().intValue());
		assertEquals(HederaFunctionality.CryptoDelete, subject.scheduledFunction());
	}

	@Test
	void factoryTranslatesImpossibleParseError() {
		final var bytes = "NONSENSE".getBytes();
		final var iae = assertThrows(IllegalArgumentException.class,
				() -> MerkleSchedule.from(bytes, 0L));
		assertEquals("Argument bodyBytes=0x4e4f4e53454e5345 was not a TransactionBody!", iae.getMessage());
	}

	@Test
	void translatesInvariantFailure() {
		subject = new MerkleSchedule();

		assertThrows(IllegalStateException.class, subject::scheduledTransactionId);
	}

	@Test
	void understandsSchedulerIsFallbackPayer() {
		assertEquals(subject.payer(), subject.effectivePayer());

		subject.setPayer(null);

		assertEquals(subject.schedulingAccount(), subject.effectivePayer());
	}

	@Test
	void checksResolutionAsExpected() {
		assertThrows(IllegalStateException.class, subject::deletionTime);
		assertThrows(IllegalStateException.class, subject::executionTime);

		subject.markExecuted(resolutionTime);
		assertEquals(grpcResolutionTime, subject.executionTime());

		subject.markDeleted(resolutionTime);
		assertEquals(grpcResolutionTime, subject.deletionTime());
	}

	@Test
	void notaryWorks() {
		assertFalse(subject.hasValidSignatureFor(fpk));
		assertFalse(subject.hasValidSignatureFor(spk));
		assertFalse(subject.hasValidSignatureFor(tpk));

		subject.witnessValidSignature(fpk);
		subject.witnessValidSignature(tpk);

		assertTrue(subject.hasValidSignatureFor(fpk));
		assertFalse(subject.hasValidSignatureFor(spk));
		assertTrue(subject.hasValidSignatureFor(tpk));
	}

	@Test
	void witnessOnlyTrueIfNewSignatory() {
		assertTrue(subject.witnessValidSignature(fpk));
		assertFalse(subject.witnessValidSignature(fpk));
	}

	@Test
	void releaseIsNoop() {
		assertDoesNotThrow(subject::release);
	}

	@Test
	void signatoriesArePublished() {
		subject.witnessValidSignature(fpk);
		subject.witnessValidSignature(spk);
		subject.witnessValidSignature(tpk);

		assertTrue(subject.signatories().containsAll(signatories));
	}

	@Test
	void nonessentialFieldsDontAffectIdentity() {
		final var diffBodyBytes = parentTxn.toBuilder()
				.setTransactionID(parentTxn.getTransactionID().toBuilder()
						.setAccountID(otherPayer.toGrpcAccountId())
						.setTransactionValidStart(MiscUtils.asTimestamp(otherSchedulingTXValidStart.toJava())))
				.setScheduleCreate(parentTxn.getScheduleCreate().toBuilder()
						.setPayerAccountID(otherPayer.toGrpcAccountId()))
				.build().toByteArray();
		final var other = MerkleSchedule.from(diffBodyBytes, expiry + 1);
		other.markExecuted(resolutionTime);
		other.markDeleted(resolutionTime);
		other.witnessValidSignature(fpk);

		assertEquals(subject, other);
		assertEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void differentAdminKeysNotIdentical() {
		final var bodyBytesDiffAdminKey = parentTxn.toBuilder()
				.setScheduleCreate(parentTxn.getScheduleCreate().toBuilder()
						.setAdminKey(MiscUtils.asKeyUnchecked(otherAdminKey)))
				.build().toByteArray();
		final var other = MerkleSchedule.from(bodyBytesDiffAdminKey, expiry);

		assertNotEquals(subject, other);
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void differentMemosNotIdentical() {
		final var bodyBytesDiffMemo = parentTxn.toBuilder()
				.setScheduleCreate(parentTxn.getScheduleCreate().toBuilder()
						.setMemo(otherEntityMemo))
				.build().toByteArray();
		final var other = MerkleSchedule.from(bodyBytesDiffMemo, expiry);

		assertNotEquals(subject, other);
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void differentScheduledTxnNotIdentical() {
		final var bodyBytesDiffScheduledTxn = parentTxn.toBuilder()
				.setScheduleCreate(parentTxn.getScheduleCreate().toBuilder()
						.setScheduledTransactionBody(scheduledTxn.toBuilder().setMemo("Slightly different!")))
				.build().toByteArray();
		final var other = MerkleSchedule.from(bodyBytesDiffScheduledTxn, expiry);

		assertNotEquals(subject, other);
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void validToString() {
		subject.witnessValidSignature(fpk);
		subject.witnessValidSignature(spk);
		subject.witnessValidSignature(tpk);
		subject.markDeleted(resolutionTime);

		final var expected = "MerkleSchedule{"
				+ "number=123456 <-> 0.0.123456, "
				+ "scheduledTxn=" + scheduledTxn + ", "
				+ "expiry=" + expiry + ", "
				+ "executed=" + false + ", "
				+ "deleted=" + true + ", "
				+ "memo=" + entityMemo + ", "
				+ "payer=" + payer.toAbbrevString() + ", "
				+ "schedulingAccount=" + schedulingAccount + ", "
				+ "schedulingTXValidStart=" + schedulingTXValidStart
				+ ", " + "signatories=[" + signatoriesToString() + "], "
				+ "adminKey=" + describe(adminKey) + ", "
				+ "resolutionTime=" + RichInstant.fromJava(resolutionTime).toString()
				+ "}";

		assertEquals(expected, subject.toString());
	}

	@Test
	void validEqualityChecks() {
		assertEquals(subject, subject);
		assertNotEquals(null, subject);
		assertNotEquals(new Object(), subject);
	}

	@Test
	void validVersion() {
		assertEquals(MerkleSchedule.CURRENT_VERSION, subject.getVersion());
	}

	@Test
	void validRuntimeConstructableID() {
		assertEquals(MerkleSchedule.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void validIsLeaf() {
		assertTrue(subject.isLeaf());
	}

	@Test
	void copyWorks() {
		subject.markDeleted(resolutionTime);
		subject.witnessValidSignature(tpk);
		final var copySubject = subject.copy();

		assertTrue(copySubject.isDeleted());
		assertFalse(copySubject.isExecuted());
		assertTrue(copySubject.hasValidSignatureFor(tpk));

		assertEquals(subject.toString(), copySubject.toString());
		assertNotSame(subject.signatories(), copySubject.signatories());

		assertEquals(grpcResolutionTime, copySubject.deletionTime());
		assertEquals(payer, copySubject.payer());
		assertEquals(expiry, copySubject.expiry());
		assertEquals(schedulingAccount, copySubject.schedulingAccount());
		assertEquals(entityMemo, copySubject.memo().get());
		assertEquals(adminKey.toString(), copySubject.adminKey().get().toString());
		assertEquals(schedulingTXValidStart, copySubject.schedulingTXValidStart());
		assertEquals(scheduledTxn, copySubject.scheduledTxn());
		assertEquals(expectedSignedTxn(), copySubject.asSignedTxn());
		assertArrayEquals(bodyBytes, copySubject.bodyBytes());
		assertTrue(subject.isImmutable());
	}

	private String signatoriesToString() {
		return signatories.stream().map(CommonUtils::hex).collect(Collectors.joining(", "));
	}

	private static final long fee = 123L;
	private static final String scheduledTxnMemo = "Wait for me!";
	private static final TransactionID parentTransactionId = TransactionID.newBuilder()
					.setTransactionValidStart(MiscUtils.asTimestamp(schedulingTXValidStart.toJava()))
					.setAccountID(schedulingAccount.toGrpcAccountId())
					.build();
	private static final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(fee)
			.setMemo(scheduledTxnMemo)
			.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
					.setDeleteAccountID(IdUtils.asAccount("0.0.2"))
					.setTransferAccountID(IdUtils.asAccount("0.0.75231")))
			.build();

	private static final TransactionBody ordinaryVersionOfScheduledTxn = MiscUtils.asOrdinary(scheduledTxn, parentTransactionId);

	private static final ScheduleCreateTransactionBody creation = ScheduleCreateTransactionBody.newBuilder()
			.setAdminKey(MiscUtils.asKeyUnchecked(adminKey))
			.setPayerAccountID(payer.toGrpcAccountId())
			.setMemo(entityMemo)
			.setScheduledTransactionBody(scheduledTxn)
			.build();
	private static final TransactionBody parentTxn = TransactionBody.newBuilder()
			.setTransactionID(parentTransactionId)
			.setScheduleCreate(creation)
			.build();
	private static final byte[] bodyBytes = parentTxn.toByteArray();

	private static Transaction expectedSignedTxn() {
		final var expectedId = TransactionID.newBuilder()
				.setAccountID(schedulingAccount.toGrpcAccountId())
				.setTransactionValidStart(asTimestamp(schedulingTXValidStart.toJava()))
				.setScheduled(true);
		return Transaction.newBuilder()
				.setSignedTransactionBytes(
						SignedTransaction.newBuilder()
								.setBodyBytes(
										TransactionBody.newBuilder()
												.mergeFrom(MiscUtils.asOrdinary(scheduledTxn, parentTransactionId))
												.setTransactionID(expectedId)
												.build().toByteString())
								.build().toByteString())
				.build();
	}

}
