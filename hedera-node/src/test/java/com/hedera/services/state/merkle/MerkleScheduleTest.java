package com.hedera.services.state.merkle;

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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.Arrays;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.MerkleSchedule.UPPER_BOUND_MEMO_UTF8_BYTES;
import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class MerkleScheduleTest {
	byte[] fpk = "firstPretendKey".getBytes();
	byte[] spk = "secondPretendKey".getBytes();
	byte[] tpk = "thirdPretendKey".getBytes();

	final int TX_BYTES = 64;
	byte[] transactionBody, otherTransactionBody;
	String entityMemo, otherEntityMemo;
	EntityId payer, otherPayer;
	EntityId schedulingAccount, otherSchedulingAccount;
	RichInstant schedulingTXValidStart, otherSchedulingTXValidStart;
	JKey adminKey, otherKey;

	long expiry = 1_234_567L, otherExpiry = 1_567_234L;

	MerkleSchedule subject;
	MerkleSchedule other;

	@BeforeEach
	public void setup() {
		transactionBody = TxnUtils.randomUtf8Bytes(TX_BYTES * 2);
		otherTransactionBody = TxnUtils.randomUtf8Bytes(TX_BYTES);

		entityMemo = "Just some memo again";
		otherEntityMemo = "Yet another memo";

		payer = new EntityId(4, 5, 6);
		otherPayer = new EntityId(4, 5, 5);

		schedulingAccount = new EntityId(1, 2, 3);
		otherSchedulingAccount = new EntityId(1, 2, 2);

		schedulingTXValidStart = new RichInstant(123, 456);
		otherSchedulingTXValidStart = new RichInstant(456, 789);

		adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
		otherKey = new JEd25519Key("not-a-real-other-key".getBytes());

		subject = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
		this.setOptionalElements(subject);

		serdes = mock(DomainSerdes.class);
		MerkleSchedule.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		MerkleSchedule.serdes = new DomainSerdes();
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
	public void deleteIsNoop() {
		// expect:
		assertDoesNotThrow(subject::release);
	}

	@Test
	public void validGetters() {
		// expect:
		assertEquals(transactionBody, subject.transactionBody());
		assertEquals(entityMemo, subject.memo().get());
		assertEquals(expiry, subject.expiry());
		assertEquals(payer, subject.payer());
		assertEquals(schedulingAccount, subject.schedulingAccount());
		assertEquals(schedulingTXValidStart, subject.schedulingTXValidStart());
		assertTrue(subject.hasAdminKey());
		assertTrue(equalUpToDecodability(adminKey, subject.adminKey().get()));
		assertTrue(subject.hasPayer());
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

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeLong(expiry);
		inOrder.verify(out).writeInt(transactionBody.length);
		inOrder.verify(out).writeByteArray(transactionBody);
		inOrder.verify(serdes).writeNullableSerializable(payer, out);
		inOrder.verify(out).writeSerializable(schedulingAccount, true);
		inOrder.verify(out).writeLong(schedulingTXValidStart.getSeconds());
		inOrder.verify(out).writeInt(schedulingTXValidStart.getNanos());
		inOrder.verify(serdes).writeNullable(
				argThat(adminKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(out).writeInt(2);
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, fpk)));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, spk)));
		inOrder.verify(serdes).writeNullableString(entityMemo, out);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);
		// and:
		subject.witnessValidEd25519Signature(fpk);
		subject.witnessValidEd25519Signature(spk);

		given(serdes.deserializeKey(fin))
				.willReturn(adminKey);
		given(serdes.readNullable(argThat(fin::equals), any(IoReadingFunction.class)))
				.willReturn(adminKey);
		given(fin.readLong())
				.willReturn(subject.expiry())
				.willReturn(schedulingTXValidStart.getSeconds());
		given(fin.readInt())
				.willReturn(transactionBody.length)
				.willReturn(schedulingTXValidStart.getNanos())
				.willReturn(2);
		given(fin.readByteArray(transactionBody.length))
				.willReturn(transactionBody);
		given(fin.readByteArray(MerkleSchedule.NUM_ED25519_PUBKEY_BYTES))
				.willReturn(fpk)
				.willReturn(spk);
		given(serdes.readNullableSerializable(any()))
				.willReturn(payer);
		given(fin.readSerializable())
				.willReturn(schedulingAccount);
		given(serdes.readNullableString(any(), eq(UPPER_BOUND_MEMO_UTF8_BYTES)))
				.willReturn(entityMemo);

		// and:
		var read = new MerkleSchedule();

		// when:
		read.deserialize(fin, MerkleToken.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	public void failDifferentTransactionBody() {
		// given:
		other = new MerkleSchedule(otherTransactionBody, schedulingAccount, schedulingTXValidStart);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void failDifferentSchedulingAccount() {
		// given:
		other = new MerkleSchedule(transactionBody, otherSchedulingAccount, schedulingTXValidStart);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void failDifferentSchedulingTxValidStart() {
		// given:
		other = new MerkleSchedule(transactionBody, schedulingAccount, otherSchedulingTXValidStart);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void failDifferentAdminKey() {
		// given:
		other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
		setOptionalElements(other);

		// when:
		other.setAdminKey(otherKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void failDifferentPayer() {
		// given:
		other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
		setOptionalElements(other);

		// when:
		other.setPayer(otherPayer);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void failDifferentExpiry() {
		// given:
		other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
		setOptionalElements(other);

		// when:
		other.setExpiry(otherExpiry);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void failDifferentMemo() {
		// given:
		other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
		setOptionalElements(other);
		other.setMemo(otherEntityMemo);

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
		// and:
		var expSigsEntry = "signatories=[" + Hex.encodeHexString(fpk) + ", " + Hex.encodeHexString(spk) + "], ";
		// and:
		var expected = "MerkleSchedule{"
				+ "expiry=" + expiry + ", "
				+ "transactionBody=" + hex(transactionBody) + ", "
				+ "memo=" + entityMemo + ", "
				+ "payer=" + payer.toAbbrevString() + ", "
				+ "schedulingAccount=" + schedulingAccount + ", "
				+ "schedulingTXValidStart=" + schedulingTXValidStart +", "
				+ expSigsEntry
				+ "adminKey=" + describe(adminKey) + "}";

		// expect:
		assertEquals(expected, subject.toString());
	}

	@Test
	public void validHashCode() {
		// given:
		var defaultSubject = new MerkleAccountState();
		// and:
		var identicalSubject = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);
		setOptionalElements(identicalSubject);

		// and:
		other = new MerkleSchedule(transactionBody, schedulingAccount, schedulingTXValidStart);

		// expect:
		assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identicalSubject.hashCode());
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
	public void throwsLegacyProvider() {
		// expect:
		assertThrows(UnsupportedOperationException.class,
				() -> MerkleSchedule.LEGACY_PROVIDER.deserialize(null));
	}

	@Test
	public void validCopy() {
		// given:
		var copySubject = subject.copy();

		// expect:
		assertNotSame(copySubject, subject);
		assertEquals(subject, copySubject);
	}

	private void setOptionalElements(MerkleSchedule schedule) {
		schedule.setMemo(entityMemo);
		schedule.setPayer(payer);
		schedule.setExpiry(expiry);
		schedule.setAdminKey(adminKey);
	}
}
