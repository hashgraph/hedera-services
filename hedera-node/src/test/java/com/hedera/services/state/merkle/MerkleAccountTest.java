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

import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.state.merkle.MerkleAccount.ChildIndices.RELEASE_090_ASSOCIATED_TOKENS;
import static com.hedera.services.state.merkle.MerkleAccount.IMMUTABLE_EMPTY_FCQ;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.will;

@RunWith(JUnitPlatform.class)
public class MerkleAccountTest {
	JKey key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
	long expiry = 1_234_567L;
	long balance = 555_555L;
	long autoRenewSecs = 234_567L;
	long senderThreshold = 1_234L;
	long receiverThreshold = 4_321L;
	String memo = "A memo";
	boolean deleted = true;
	boolean smartContract = true;
	boolean receiverSigRequired = true;
	EntityId proxy = new EntityId(1L, 2L, 3L);
	long firstToken = 555, secondToken = 666, thirdToken = 777;
	long firstBalance = 123, secondBalance = 234, thirdBalance = 345;
	long firstFlags = 0, secondFlags = 0, thirdFlags = 0;
	long[] tokenRels = new long[] {
			firstToken, firstBalance, firstFlags,
			secondToken, secondBalance, secondFlags,
			thirdToken, thirdBalance, thirdFlags
	};
	long otherFirstBalance = 321, otherSecondBalance = 432, otherThirdBalance = 543;
	long otherFirstFlags = 0, otherSecondFlags = 0, otherThirdFlags = 0;
	long[] otherTokenRels = new long[] {
			firstToken, otherFirstBalance, otherFirstFlags,
			secondToken, otherSecondBalance, otherSecondFlags,
			thirdToken, otherThirdBalance, otherThirdFlags
	};

	JKey otherKey = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
	long otherExpiry = 7_234_567L;
	long otherBalance = 666_666L;
	long otherAutoRenewSecs = 432_765L;
	long otherSenderThreshold = 4_321L;
	long otherReceiverThreshold = 1_234L;
	String otherMemo = "Another memo";
	boolean otherDeleted = false;
	boolean otherSmartContract = false;
	boolean otherReceiverSigRequired = false;
	EntityId otherProxy = new EntityId(3L, 2L, 1L);
	JKey adminKey = TOKEN_ADMIN_KT.asJKeyUnchecked();
	MerkleToken unfrozenToken = new MerkleToken(
			Long.MAX_VALUE, 100, 1,
			"UnfrozenToken", "UnfrozenTokenName", false, false,
			new EntityId(1, 2, 3));

	MerkleAccountState state;
	MerkleAccountState otherState;
	FCQueue<ExpirableTxnRecord> payerRecords;
	MerkleAccountTokens tokens;
	DomainSerdes serdes;

	MerkleAccount subject;
	MerkleAccountState delegate;

	@BeforeEach
	public void setup() {
		serdes = mock(DomainSerdes.class);
		MerkleAccount.serdes = serdes;

		payerRecords = mock(FCQueue.class);
		given(payerRecords.copy()).willReturn(payerRecords);
		given(payerRecords.isImmutable()).willReturn(false);

		tokens = mock(MerkleAccountTokens.class);
		given(tokens.copy()).willReturn(tokens);

		delegate = mock(MerkleAccountState.class);

		state = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy);
		otherState = new MerkleAccountState(
				otherKey,
				otherExpiry, otherBalance, otherAutoRenewSecs,
				otherMemo,
				otherDeleted, otherSmartContract, otherReceiverSigRequired,
				otherProxy);

		subject = new MerkleAccount(List.of(state, payerRecords, tokens));
	}

	@AfterEach
	public void cleanup() {
		MerkleAccount.serdes = new DomainSerdes();
	}

	@Test
	public void immutableAccountThrowsIse() {
		// setup:
		MerkleAccount.stackDump = () -> {};

		// given:
		var original = new MerkleAccount();

		// when:
		original.copy();

		// then:
		assertThrows(IllegalStateException.class, () -> original.copy());

		// cleanup:
		MerkleAccount.stackDump = Thread::dumpStack;
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(
				MerkleAccount.ChildIndices.NUM_081_CHILDREN,
				subject.getMinimumChildCount(MerkleAccount.MERKLE_VERSION - 2));
		assertEquals(
				MerkleAccount.ChildIndices.NUM_090_ALPHA_CHILDREN,
				subject.getMinimumChildCount(MerkleAccount.MERKLE_VERSION - 1));
		assertEquals(
				MerkleAccount.ChildIndices.NUM_090_CHILDREN,
				subject.getMinimumChildCount(MerkleAccount.MERKLE_VERSION));
		assertEquals(MerkleAccount.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleAccount.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertFalse(subject.isLeaf());
	}

	@Test
	public void toStringWorks() {
		given(payerRecords.size()).willReturn(3);
		given(tokens.readableTokenIds()).willReturn("[1.2.3, 2.3.4]");

		// expect:
		assertEquals(
				"MerkleAccount{state=" + state.toString()
						+ ", # records=" + 3
						+ ", tokens=" + "[1.2.3, 2.3.4]"
						+ "}",
				subject.toString());
	}

	@Test
	public void gettersDelegate() {
		// expect:
		assertEquals(state.expiry(), subject.getExpiry());
		assertEquals(state.balance(), subject.getBalance());
		assertEquals(state.autoRenewSecs(), subject.getAutoRenewSecs());
		assertEquals(state.isReleased(), subject.isReleased());
		assertEquals(state.isSmartContract(), subject.isSmartContract());
		assertEquals(state.isReceiverSigRequired(), subject.isReceiverSigRequired());
		assertEquals(state.memo(), subject.getMemo());
		assertEquals(state.proxy(), subject.getProxy());
		assertTrue(equalUpToDecodability(state.key(), subject.getKey()));
		assertSame(tokens, subject.tokens());
	}

	@Test
	public void settersDelegate() throws NegativeAccountBalanceException {
		// given:
		subject = new MerkleAccount(List.of(delegate, IMMUTABLE_EMPTY_FCQ, IMMUTABLE_EMPTY_FCQ));

		// when:
		subject.setExpiry(otherExpiry);
		subject.setBalance(otherBalance);
		subject.setAutoRenewSecs(otherAutoRenewSecs);
		subject.setDeleted(otherDeleted);
		subject.setSmartContract(otherSmartContract);
		subject.setReceiverSigRequired(otherReceiverSigRequired);
		subject.setMemo(otherMemo);
		subject.setProxy(otherProxy);
		subject.setKey(otherKey);

		// then:
		verify(delegate).setExpiry(otherExpiry);
		verify(delegate).setAutoRenewSecs(otherAutoRenewSecs);
		verify(delegate).setDeleted(otherDeleted);
		verify(delegate).setSmartContract(otherSmartContract);
		verify(delegate).setReceiverSigRequired(otherReceiverSigRequired);
		verify(delegate).setMemo(otherMemo);
		verify(delegate).setProxy(otherProxy);
		verify(delegate).setKey(otherKey);
		verify(delegate).setHbarBalance(otherBalance);
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new MerkleAccount();
		var two = new MerkleAccount(List.of(state, payerRecords, tokens));
		var three = two.copy();

		// then:
		verify(payerRecords).copy();
		verify(tokens).copy();
		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertNotEquals(two, one);
		assertEquals(one, one);
		assertEquals(two, three);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}

	@Test
	public void copyConstructorFastCopiesMutableFcqs() {
		given(payerRecords.isImmutable()).willReturn(false);

		// when:
		var copy = subject.copy();

		// then:
		verify(payerRecords).copy();
		// and:
		assertEquals(payerRecords, copy.records());
	}

	@Test
	public void throwsOnNegativeBalance() {
		// expect:
		assertThrows(NegativeAccountBalanceException.class, () -> subject.setBalance(-1L));
	}

	@Test
	public void initializeTranslatesFromRelease090AlphaStates() {
		// setup:
		var accountState = new MerkleAccountState();
		var accountTokens = new MerkleAccountTokens();

		// given:
		subject.addDeserializedChildren(
				List.of(accountState, IMMUTABLE_EMPTY_FCQ, IMMUTABLE_EMPTY_FCQ, accountTokens),
				MerkleAccount.RELEASE_090_ALPHA_VERSION);

		// when:
		subject.initialize(null);

		// then:
		assertSame(accountState, subject.getChild(MerkleAccount.ChildIndices.STATE));
		assertSame(IMMUTABLE_EMPTY_FCQ, subject.getChild(MerkleAccount.ChildIndices.RELEASE_090_RECORDS));
		assertSame(accountTokens, subject.getChild(RELEASE_090_ASSOCIATED_TOKENS));
	}

	@Test
	public void initializeTranslatesFromRelease081States() {
		// setup:
		var accountState = new MerkleAccountState();

		// given:
		subject.addDeserializedChildren(
				List.of(accountState, IMMUTABLE_EMPTY_FCQ, IMMUTABLE_EMPTY_FCQ),
				MerkleAccount.RELEASE_081_VERSION);

		// when:
		subject.initialize(null);

		// then:
		assertSame(accountState, subject.getChild(MerkleAccount.ChildIndices.STATE));
		assertSame(IMMUTABLE_EMPTY_FCQ, subject.getChild(MerkleAccount.ChildIndices.RELEASE_090_RECORDS));
		assertThat(subject.getChild(RELEASE_090_ASSOCIATED_TOKENS), instanceOf(MerkleAccountTokens.class));
	}

	@Test
	public void isMutableAfterCopy() {
		subject.copy();

		assertTrue(subject.isImmutable());
	}

	@Test
	public void originalIsMutable() {
		assertFalse(subject.isImmutable());
	}

	@Test
	public void delegatesDelete() {
		// when:
		subject.release();

		// then:
		verify(payerRecords).decrementReferenceCount();
	}
}