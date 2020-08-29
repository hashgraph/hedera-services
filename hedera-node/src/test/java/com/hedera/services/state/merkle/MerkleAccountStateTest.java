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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;

import static com.hedera.services.state.merkle.MerkleAccountState.MAX_NUM_TOKEN_BALANCES;
import static com.hedera.services.state.merkle.MerkleAccountState.NO_TOKEN_BALANCES;
import static com.hedera.test.utils.IdUtils.tokenWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyInt;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.Mockito.inOrder;

@RunWith(JUnitPlatform.class)
class MerkleAccountStateTest {
	JKey key;
	long expiry = 1_234_567L;
	long balance = 555_555L;
	long autoRenewSecs = 234_567L;
	long senderThreshold = 1_234L;
	long receiverThreshold = 4_321L;
	String memo = "A memo";
	boolean deleted = true;
	boolean smartContract = true;
	boolean receiverSigRequired = true;
	EntityId proxy;
	long firstToken = 555, secondToken = 666, thirdToken = 777;
	long firstBalance = 123, secondBalance = 234, thirdBalance = 345;
	long[] tokenBalances = new long[] {
		firstToken, firstBalance, secondToken, secondBalance, thirdToken, thirdBalance
	};

	JKey otherKey;
	long otherExpiry = 7_234_567L;
	long otherBalance = 666_666L;
	long otherAutoRenewSecs = 432_765L;
	long otherSenderThreshold = 4_321L;
	long otherReceiverThreshold = 1_234L;
	String otherMemo = "Another memo";
	boolean otherDeleted = false;
	boolean otherSmartContract = false;
	boolean otherReceiverSigRequired = false;
	EntityId otherProxy;
	long otherFirstBalance = 321, otherSecondBalance = 432, otherThirdBalance = 543;
	long[] otherTokenBalances = new long[] {
			firstToken, otherFirstBalance, secondToken, otherSecondBalance, thirdToken, otherThirdBalance
	};

	DomainSerdes serdes;

	MerkleAccountState subject;
	MerkleAccountState release070Subject;
	MerkleAccountState otherSubject;

	@BeforeEach
	public void setup() {
		key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
		proxy = new EntityId(1L, 2L, 3L);
		// and:
		otherKey = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
		otherProxy = new EntityId(3L, 2L, 1L);

		release070Subject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				NO_TOKEN_BALANCES);
		subject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		serdes = mock(DomainSerdes.class);
		MerkleAccountState.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		MerkleAccountState.serdes = new DomainSerdes();
	}

	@Test
	public void getsTokenBalanceIfPresent()	 {
		// expect:
		assertEquals(firstBalance, subject.getTokenBalance(tokenWith(firstToken)));
		assertEquals(secondBalance, subject.getTokenBalance(tokenWith(secondToken)));
		assertEquals(thirdBalance, subject.getTokenBalance(tokenWith(thirdToken)));
	}

	@Test
	public void updatesTokenBalanceIfPresent()	 {
		// given:
		subject.setTokenBalance(tokenWith(firstToken), firstBalance + 1);

		// expect:
		assertEquals(firstBalance + 1, subject.getTokenBalance(tokenWith(firstToken)));
	}

	@Test
	public void createsFirstTokenIfMissing() {
		// given:
		subject.setTokenBalance(tokenWith(firstToken - 1), firstBalance + 1);

		// expect:
		assertEquals(firstBalance + 1, subject.getTokenBalance(tokenWith(firstToken - 1)));
	}

	@Test
	public void createsSecondTokenIfMissing() {
		// given:
		subject.setTokenBalance(tokenWith(secondToken - 1), secondBalance + 1);

		// expect:
		assertEquals(secondBalance + 1, subject.getTokenBalance(tokenWith(secondToken - 1)));
	}

	@Test
	public void createsThirdTokenIfMissing() {
		// given:
		subject.setTokenBalance(tokenWith(thirdToken - 1), thirdBalance + 1);

		// expect:
		assertEquals(thirdBalance + 1, subject.getTokenBalance(tokenWith(thirdToken - 1)));
	}

	@Test
	public void createsFourthTokenIfMissing() {
		// given:
		subject.setTokenBalance(tokenWith(thirdToken + 1), thirdBalance + 2);

		// expect:
		assertEquals(thirdBalance + 2, subject.getTokenBalance(tokenWith(thirdToken + 1)));
	}

	@Test
	public void throwsOnMissingToken() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.getTokenBalance(TokenID.getDefaultInstance()));
	}

	@Test
	public void throwsOnNegativeBalance() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> subject.setTokenBalance(tokenWith(firstToken), -1));
	}

	@Test
	public void getsLogicalInsertIndexIfMissing()	 {
		// expect:
		assertEquals(-1, subject.logicalIndexOf(tokenWith(firstToken - 1)));
		assertEquals(-2, subject.logicalIndexOf(tokenWith(secondToken - 1)));
		assertEquals(-3, subject.logicalIndexOf(tokenWith(thirdToken - 1)));
		assertEquals(-4, subject.logicalIndexOf(tokenWith(thirdToken + 1)));
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals("MerkleAccountState{" +
						"key=" + MiscUtils.describe(key) + ", " +
						"expiry=" + expiry + ", " +
						"balance=" + balance + ", " +
						"autoRenewSecs=" + autoRenewSecs + ", " +
						"senderThreshold=" + senderThreshold + ", " +
						"receiverThreshold=" + receiverThreshold + ", " +
						"memo=" + memo + ", " +
						"deleted=" + deleted + ", " +
						"smartContract=" + smartContract + ", " +
						"receiverSigRequired=" + receiverSigRequired + ", " +
						"proxy=" + proxy + ", " +
						"tokenBalances=[555, 123, 666, 234, 777, 345]" + "}",
				subject.toString());
	}

	@Test
	public void release070DeserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var newSubject = new MerkleAccountState();

		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(senderThreshold)
				.willReturn(receiverThreshold);
		given(in.readLongArray(4 * MAX_NUM_TOKEN_BALANCES))
				.willThrow(IllegalStateException.class);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);

		// when:
		newSubject.deserialize(in, MerkleAccountState.RELEASE_070_VERSION);

		// then:
		assertEquals(release070Subject, newSubject);
	}

	@Test
	public void release090DeserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var newSubject = new MerkleAccountState();

		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(senderThreshold)
				.willReturn(receiverThreshold);
		given(in.readLongArray(4 * MAX_NUM_TOKEN_BALANCES))
				.willReturn(tokenBalances);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);

		// when:
		newSubject.deserialize(in, MerkleAccountState.RELEASE_080_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(serdes, out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(serdes).writeNullable(argThat(key::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(out).writeLong(expiry);
		inOrder.verify(out).writeLong(balance);
		inOrder.verify(out).writeLong(autoRenewSecs);
		inOrder.verify(out).writeLong(senderThreshold);
		inOrder.verify(out).writeLong(receiverThreshold);
		inOrder.verify(out).writeNormalisedString(memo);
		inOrder.verify(out, times(3)).writeBoolean(true);
		inOrder.verify(serdes).writeNullableSerializable(proxy, out);
		inOrder.verify(out).writeLongArray(tokenBalances);
	}

	@Test
	public void copyWorks() {
		// given:
		var copySubject = subject.copy();

		// expect:
		assertNotSame(copySubject, subject);
		assertNotSame(subject.tokenBalances, copySubject.tokenBalances);
		assertEquals(subject, copySubject);
	}

	@Test
	public void equalsWorksWithRadicalDifferences() {
		// expect:
		assertEquals(subject, subject);
		assertNotEquals(subject, null);
		assertNotEquals(subject, new Object());
	}

	@Test
	public void equalsWorksForKey() {
		// given:
		otherSubject = new MerkleAccountState(
				otherKey,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForExpiry() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				otherExpiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForBalance() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, otherBalance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForAutoRenewSecs() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, otherAutoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForSenderThreshold() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, otherSenderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForReceiverThreshold() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, otherReceiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForTokenBalances() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				otherTokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForMemo() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				otherMemo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForDeleted() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				otherDeleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForSmartContract() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, otherSmartContract, receiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForReceiverSigRequired() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, otherReceiverSigRequired,
				proxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void equalsWorksForProxy() {
		// given:
		otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				otherProxy,
				tokenBalances);

		// expect:
		assertNotEquals(subject, otherSubject);
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleAccountState.RELEASE_080_VERSION, subject.getVersion());
		assertEquals(MerkleAccountState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	public void objectContractMet() {
		// given:
		var defaultSubject = new MerkleAccountState();
		// and:
		var identicalSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				tokenBalances);
		// and:
		otherSubject = new MerkleAccountState(
				otherKey,
				otherExpiry, otherBalance, otherAutoRenewSecs, otherSenderThreshold, otherReceiverThreshold,
				otherMemo,
				otherDeleted, otherSmartContract, otherReceiverSigRequired,
				otherProxy,
				otherTokenBalances);

		// expect:
		assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
		assertNotEquals(subject.hashCode(), otherSubject.hashCode());
		assertEquals(subject.hashCode(), identicalSubject.hashCode());
	}
}
