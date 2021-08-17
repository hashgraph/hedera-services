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

import static com.hedera.services.state.merkle.MerkleAccountState.MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MerkleAccountStateTest {
  private JKey key;
  private long expiry = 1_234_567L;
  private long balance = 555_555L;
  private long autoRenewSecs = 234_567L;
  private long nftsOwned = 150L;
  private String memo = "A memo";
  private boolean deleted = true;
  private boolean smartContract = true;
  private boolean receiverSigRequired = true;
  private EntityId proxy;

  private JKey otherKey;
  private long otherExpiry = 7_234_567L;
  private long otherBalance = 666_666L;
  private long otherAutoRenewSecs = 432_765L;
  private String otherMemo = "Another memo";
  private boolean otherDeleted = false;
  private boolean otherSmartContract = false;
  private boolean otherReceiverSigRequired = false;
  private EntityId otherProxy;

  private DomainSerdes serdes;

  private MerkleAccountState otherSubject;

  private MerkleAccountState subject;

  @BeforeEach
  void setup() {
    key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
    proxy = new EntityId(1L, 2L, 3L);
    // and:
    otherKey = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
    otherProxy = new EntityId(3L, 2L, 1L);

    subject =
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

    serdes = mock(DomainSerdes.class);
    MerkleAccountState.serdes = serdes;
  }

  @AfterEach
  void cleanup() {
    MerkleAccountState.serdes = new DomainSerdes();
  }

  @Test
  void toStringWorks() {
    // expect:
    assertEquals(
        "MerkleAccountState{"
            + "key="
            + MiscUtils.describe(key)
            + ", "
            + "expiry="
            + expiry
            + ", "
            + "balance="
            + balance
            + ", "
            + "autoRenewSecs="
            + autoRenewSecs
            + ", "
            + "memo="
            + memo
            + ", "
            + "deleted="
            + deleted
            + ", "
            + "smartContract="
            + smartContract
            + ", "
            + "receiverSigRequired="
            + receiverSigRequired
            + ", "
            + "proxy="
            + proxy
            + ", nftsOwned=0}",
        subject.toString());
  }

  @Test
  void copyIsImmutable() {
    // given:
    final var key = new JKeyList();
    final var proxy = new EntityId(0, 0, 2);

    // when:
    subject.copy();

    // then:
    assertThrows(MutabilityException.class, () -> subject.setHbarBalance(1L));
    assertThrows(MutabilityException.class, () -> subject.setAutoRenewSecs(1_234_567L));
    assertThrows(MutabilityException.class, () -> subject.setDeleted(true));
    assertThrows(MutabilityException.class, () -> subject.setKey(key));
    assertThrows(MutabilityException.class, () -> subject.setMemo("NOPE"));
    assertThrows(MutabilityException.class, () -> subject.setSmartContract(false));
    assertThrows(MutabilityException.class, () -> subject.setReceiverSigRequired(true));
    assertThrows(MutabilityException.class, () -> subject.setExpiry(1_234_567L));
    assertThrows(MutabilityException.class, () -> subject.setProxy(proxy));
  }

  @Test
  void deserializeWorks() throws IOException {
    // setup:
    var in = mock(SerializableDataInputStream.class);
    // and:
    var newSubject = new MerkleAccountState();

    given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
    given(in.readLong()).willReturn(expiry).willReturn(balance).willReturn(autoRenewSecs);
    given(in.readNormalisedString(anyInt())).willReturn(memo);
    given(in.readBoolean())
        .willReturn(deleted)
        .willReturn(smartContract)
        .willReturn(receiverSigRequired);
    given(serdes.readNullableSerializable(in)).willReturn(proxy);

    // when:
    newSubject.deserialize(in, MerkleAccountState.RELEASE_090_VERSION);

    // then:
    assertEquals(subject, newSubject);
    // and:
    verify(in, never()).readLongArray(MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE);
    verify(in, times(3)).readLong();
  }

  @Test
  void deserializeV0160Works() throws IOException {
    // setup:
    var in = mock(SerializableDataInputStream.class);
    subject.setNftsOwned(nftsOwned);
    // and:
    var newSubject = new MerkleAccountState();

    given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
    given(in.readLong())
        .willReturn(expiry)
        .willReturn(balance)
        .willReturn(autoRenewSecs)
        .willReturn(nftsOwned);
    given(in.readNormalisedString(anyInt())).willReturn(memo);
    given(in.readBoolean())
        .willReturn(deleted)
        .willReturn(smartContract)
        .willReturn(receiverSigRequired);
    given(serdes.readNullableSerializable(in)).willReturn(proxy);

    // when:
    newSubject.deserialize(in, MerkleAccountState.RELEASE_0160_VERSION);

    // then:
    assertEquals(subject, newSubject);
    // and:
    verify(in, never()).readLongArray(MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE);
    verify(in, times(4)).readLong();
  }

  @Test
  void serializeWorks() throws IOException {
    // setup:
    var out = mock(SerializableDataOutputStream.class);
    // and:
    InOrder inOrder = inOrder(serdes, out);

    // when:
    subject.serialize(out);

    // then:
    inOrder
        .verify(serdes)
        .writeNullable(argThat(key::equals), argThat(out::equals), any(IoWritingConsumer.class));
    inOrder.verify(out).writeLong(expiry);
    inOrder.verify(out).writeLong(balance);
    inOrder.verify(out).writeLong(autoRenewSecs);
    inOrder.verify(out).writeNormalisedString(memo);
    inOrder.verify(out, times(3)).writeBoolean(true);
    inOrder.verify(serdes).writeNullableSerializable(proxy, out);
    // and:
    verify(out, never()).writeLongArray(any());
  }

  @Test
  void copyWorks() {
    // given:
    var copySubject = subject.copy();

    // expect:
    assertNotSame(copySubject, subject);
    assertEquals(subject, copySubject);
  }

  @Test
  void equalsWorksWithRadicalDifferences() {
    // expect:
    assertEquals(subject, subject);
    assertNotEquals(subject, null);
    assertNotEquals(subject, new Object());
  }

  @Test
  void equalsWorksForKey() {
    // given:
    otherSubject =
        new MerkleAccountState(
            otherKey,
            expiry,
            balance,
            autoRenewSecs,
            memo,
            deleted,
            smartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForExpiry() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            otherExpiry,
            balance,
            autoRenewSecs,
            memo,
            deleted,
            smartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForBalance() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            otherBalance,
            autoRenewSecs,
            memo,
            deleted,
            smartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForAutoRenewSecs() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            otherAutoRenewSecs,
            memo,
            deleted,
            smartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForMemo() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            autoRenewSecs,
            otherMemo,
            deleted,
            smartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForDeleted() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            autoRenewSecs,
            memo,
            otherDeleted,
            smartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForSmartContract() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            autoRenewSecs,
            memo,
            deleted,
            otherSmartContract,
            receiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForReceiverSigRequired() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            autoRenewSecs,
            memo,
            deleted,
            smartContract,
            otherReceiverSigRequired,
            proxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void equalsWorksForProxy() {
    // given:
    otherSubject =
        new MerkleAccountState(
            key,
            expiry,
            balance,
            autoRenewSecs,
            memo,
            deleted,
            smartContract,
            receiverSigRequired,
            otherProxy);

    // expect:
    assertNotEquals(subject, otherSubject);
  }

  @Test
  void merkleMethodsWork() {
    // expect;
    assertEquals(MerkleAccountState.RELEASE_0160_VERSION, subject.getVersion());
    assertEquals(MerkleAccountState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    assertTrue(subject.isLeaf());
  }

  @Test
  void objectContractMet() {
    // given:
    var defaultSubject = new MerkleAccountState();
    // and:
    var identicalSubject =
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
    // and:
    otherSubject =
        new MerkleAccountState(
            otherKey,
            otherExpiry,
            otherBalance,
            otherAutoRenewSecs,
            otherMemo,
            otherDeleted,
            otherSmartContract,
            otherReceiverSigRequired,
            otherProxy);

    // expect:
    assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
    assertNotEquals(subject.hashCode(), otherSubject.hashCode());
    assertEquals(subject.hashCode(), identicalSubject.hashCode());
  }
}
