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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MerkleTokenRelStatusTest {
  long balance = 666;
  boolean frozen = true;
  boolean kycGranted = true;

  MerkleTokenRelStatus subject;

  @BeforeEach
  private void setup() {
    subject = new MerkleTokenRelStatus(balance, frozen, kycGranted);
  }

  @Test
  void objectContractMet() {
    // given:
    var one = new MerkleTokenRelStatus();
    var two = new MerkleTokenRelStatus(balance - 1, frozen, kycGranted);
    var three = new MerkleTokenRelStatus(balance, !frozen, kycGranted);
    var four = new MerkleTokenRelStatus(balance, frozen, !kycGranted);
    var five = new MerkleTokenRelStatus(balance, frozen, kycGranted);

    // then:
    assertNotEquals(one, null);
    assertNotEquals(one, new Object());
    assertNotEquals(subject, two);
    assertNotEquals(subject, three);
    assertNotEquals(subject, four);
    assertEquals(subject, five);
    // and:
    assertNotEquals(one.hashCode(), two.hashCode());
    assertEquals(subject.hashCode(), five.hashCode());
  }

  @Test
  void merkleMethodsWork() {
    // expect;
    assertEquals(MerkleTokenRelStatus.MERKLE_VERSION, subject.getVersion());
    assertEquals(MerkleTokenRelStatus.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    assertTrue(subject.isLeaf());
  }

  @Test
  void serializeWorks() throws IOException {
    // setup:
    var out = mock(SerializableDataOutputStream.class);
    // and:
    InOrder inOrder = inOrder(out);

    // when:
    subject.serialize(out);

    // then:
    inOrder.verify(out).writeLong(balance);
    inOrder.verify(out, times(2)).writeBoolean(true);
  }

  @Test
  void deserializeWorks() throws IOException {
    // setup:
    var in = mock(SerializableDataInputStream.class);
    // and:
    var defaultSubject = new MerkleTokenRelStatus();

    given(in.readLong()).willReturn(balance);
    given(in.readBoolean()).willReturn(frozen).willReturn(kycGranted);

    // when:
    defaultSubject.deserialize(in, MerkleTokenRelStatus.MERKLE_VERSION);

    // then:
    assertEquals(subject, defaultSubject);
  }

  @Test
  void toStringWorks() {
    // expect:
    assertEquals(
        "MerkleTokenRelStatus{balance="
            + balance
            + ", isFrozen="
            + frozen
            + ", hasKycGranted="
            + kycGranted
            + "}",
        subject.toString());
  }

  @Test
  void copyWorks() {
    // when:
    var subjectCopy = subject.copy();

    // then:
    assertNotSame(subjectCopy, subject);
    assertEquals(subject, subjectCopy);
  }

  @Test
  void deleteIsNoop() {
    // expect:
    assertDoesNotThrow(subject::release);
  }

  @Test
  void throwsOnNegativeBalance() {
    // expect:
    assertThrows(IllegalArgumentException.class, () -> subject.setBalance(-1));
  }
}
