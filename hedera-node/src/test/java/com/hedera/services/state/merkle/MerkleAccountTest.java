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

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcqueue.FCQueue;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleAccountTest {
  private JKey key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
  private long expiry = 1_234_567L;
  private long balance = 555_555L;
  private long autoRenewSecs = 234_567L;
  private String memo = "A memo";
  private boolean deleted = true;
  private boolean smartContract = true;
  private boolean receiverSigRequired = true;
  private EntityId proxy = new EntityId(1L, 2L, 3L);

  private JKey otherKey = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
  private long otherExpiry = 7_234_567L;
  private long otherBalance = 666_666L;
  private long otherAutoRenewSecs = 432_765L;
  private String otherMemo = "Another memo";
  private boolean otherDeleted = false;
  private boolean otherSmartContract = false;
  private boolean otherReceiverSigRequired = false;
  private EntityId otherProxy = new EntityId(3L, 2L, 1L);

  private MerkleAccountState state;
  private FCQueue<ExpirableTxnRecord> payerRecords;
  private MerkleAccountTokens tokens;

  private MerkleAccountState delegate;

  private MerkleAccount subject;

  @BeforeEach
  void setup() {
    DomainSerdes serdes = mock(DomainSerdes.class);
    MerkleAccount.serdes = serdes;

    payerRecords = mock(FCQueue.class);
    given(payerRecords.copy()).willReturn(payerRecords);
    given(payerRecords.isImmutable()).willReturn(false);

    tokens = mock(MerkleAccountTokens.class);
    given(tokens.copy()).willReturn(tokens);

    delegate = mock(MerkleAccountState.class);

    state =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            autoRenewSecs,
            memo,
            deleted,
            smartContract,
            receiverSigRequired,
            proxy);

    subject = new MerkleAccount(List.of(state, payerRecords, tokens));
  }

  @AfterEach
  void cleanup() {
    MerkleAccount.serdes = new DomainSerdes();
  }

  @Test
  void immutableAccountThrowsIse() {
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
  void merkleMethodsWork() {
    // expect;
    assertEquals(
        MerkleAccount.ChildIndices.NUM_090_CHILDREN,
        subject.getMinimumChildCount(MerkleAccount.MERKLE_VERSION));
    assertEquals(MerkleAccount.MERKLE_VERSION, subject.getVersion());
    assertEquals(MerkleAccount.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    assertFalse(subject.isLeaf());
  }

  @Test
  void toStringWorks() {
    given(payerRecords.size()).willReturn(3);
    given(tokens.readableTokenIds()).willReturn("[1.2.3, 2.3.4]");

    // expect:
    assertEquals(
        "MerkleAccount{state="
            + state.toString()
            + ", # records="
            + 3
            + ", tokens="
            + "[1.2.3, 2.3.4]"
            + "}",
        subject.toString());
  }

  @Test
  void gettersDelegate() {
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
  void uncheckedSetterDelegates() {
    // given:
    subject = new MerkleAccount(List.of(delegate, new FCQueue<>(), new FCQueue<>()));
    // and:
    assertThrows(IllegalArgumentException.class, () -> subject.setBalanceUnchecked(-1L));

    // when:
    subject.setBalanceUnchecked(otherBalance);

    // then:
    verify(delegate).setHbarBalance(otherBalance);
  }

  @Test
  void settersDelegate() throws NegativeAccountBalanceException {
    // given:
    subject = new MerkleAccount(List.of(delegate, new FCQueue<>(), new FCQueue<>()));

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
  void objectContractMet() {
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
    assertEquals(two, three);
    // and:
    assertNotEquals(one.hashCode(), two.hashCode());
    assertEquals(two.hashCode(), three.hashCode());
  }

  @Test
  void copyConstructorFastCopiesMutableFcqs() {
    given(payerRecords.isImmutable()).willReturn(false);

    // when:
    var copy = subject.copy();

    // then:
    verify(payerRecords).copy();
    // and:
    assertEquals(payerRecords, copy.records());
  }

  @Test
  void throwsOnNegativeBalance() {
    // expect:
    assertThrows(NegativeAccountBalanceException.class, () -> subject.setBalance(-1L));
  }

  @Test
  void isMutableAfterCopy() {
    subject.copy();

    assertTrue(subject.isImmutable());
  }

  @Test
  void originalIsMutable() {
    assertFalse(subject.isImmutable());
  }

  @Test
  void delegatesDelete() {
    // when:
    subject.release();

    // then:
    verify(payerRecords).decrementReferenceCount();
  }
}
