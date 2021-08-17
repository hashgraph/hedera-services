package com.hedera.services.state.submerkle;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.internals.IdentityCodeUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityIdTest {
  long shard = 1L, realm = 2L, num = 3L;

  SerializableDataInputStream in;
  SerializableDataOutputStream out;

  MerkleEntityId merkleId = new MerkleEntityId(shard, realm, num);

  FileID fileId = FileID.newBuilder().setShardNum(shard).setRealmNum(realm).setFileNum(num).build();
  AccountID accountId =
      AccountID.newBuilder().setShardNum(shard).setRealmNum(realm).setAccountNum(num).build();
  ContractID contractId =
      ContractID.newBuilder().setShardNum(shard).setRealmNum(realm).setContractNum(num).build();
  TopicID topicId =
      TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
  TokenID tokenId =
      TokenID.newBuilder().setShardNum(shard).setRealmNum(realm).setTokenNum(num).build();
  ScheduleID scheduleId =
      ScheduleID.newBuilder().setShardNum(shard).setRealmNum(realm).setScheduleNum(num).build();

  EntityId subject;

  @BeforeEach
  void setup() {
    in = mock(SerializableDataInputStream.class);

    subject = new EntityId(shard, realm, num);
  }

  @Test
  void fromIdentityCodeWorks() {
    // setup:
    final var expected = new EntityId(0, 0, IdentityCodeUtils.MAX_NUM_ALLOWED);

    // given:
    final var actual = EntityId.fromIdentityCode((int) IdentityCodeUtils.MAX_NUM_ALLOWED);

    // then:
    assertEquals(actual, expected);
  }

  @Test
  void objectContractWorks() {
    // given:
    var one = subject;
    var two = EntityId.MISSING_ENTITY_ID;
    var three = subject.copy();

    // expect:
    assertNotEquals(one, null);
    assertNotEquals(one, new Object());
    assertNotEquals(two, one);
    assertEquals(one, three);
    // and:
    assertEquals(one.hashCode(), three.hashCode());
    assertNotEquals(one.hashCode(), two.hashCode());
  }

  @Test
  void toStringWorks() {
    // expect;
    assertEquals(
        "EntityId{shard=" + shard + ", realm=" + realm + ", num=" + num + "}", subject.toString());
  }

  @Test
  void copyWorks() {
    // given:
    var copySubject = subject.copy();

    // then:
    assertFalse(subject == copySubject);
    assertEquals(subject, copySubject);
  }

  @Test
  void gettersWork() {
    // expect:
    assertEquals(shard, subject.shard());
    assertEquals(realm, subject.realm());
    assertEquals(num, subject.num());
  }

  @Test
  void identityCodeWorks() {
    // expect:
    assertEquals(Integer.valueOf(3), subject.identityCode());
  }

  @Test
  void factoriesWork() {
    // expect:
    assertThrows(IllegalArgumentException.class, () -> EntityId.fromGrpcAccountId(null));
    assertThrows(IllegalArgumentException.class, () -> EntityId.fromGrpcFileId(null));
    assertThrows(IllegalArgumentException.class, () -> EntityId.fromGrpcTopicId(null));
    assertThrows(IllegalArgumentException.class, () -> EntityId.fromGrpcTokenId(null));
    assertThrows(IllegalArgumentException.class, () -> EntityId.fromGrpcScheduleId(null));
    assertThrows(IllegalArgumentException.class, () -> EntityId.fromGrpcContractId(null));
    // and:
    assertEquals(subject, EntityId.fromGrpcAccountId(accountId));
    assertEquals(subject, EntityId.fromGrpcContractId(contractId));
    assertEquals(subject, EntityId.fromGrpcTopicId(topicId));
    assertEquals(subject, EntityId.fromGrpcFileId(fileId));
    assertEquals(subject, EntityId.fromGrpcTokenId(tokenId));
    assertEquals(subject, EntityId.fromGrpcScheduleId(scheduleId));
  }

  @Test
  void serializableDetWorks() {
    // expect;
    assertEquals(EntityId.MERKLE_VERSION, subject.getVersion());
    assertEquals(EntityId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
  }

  @Test
  void deserializeWorks() throws IOException {
    // setup:
    var newSubject = new EntityId();

    given(in.readLong()).willReturn(shard).willReturn(realm).willReturn(num);

    // when:
    newSubject.deserialize(in, EntityId.MERKLE_VERSION);

    // then:
    assertEquals(subject, newSubject);
  }

  @Test
  void serializeWorks() throws IOException {
    // setup:
    out = mock(SerializableDataOutputStream.class);

    // when:
    subject.serialize(out);

    // then:
    verify(out).writeLong(shard);
    verify(out).writeLong(realm);
    verify(out).writeLong(num);
  }

  @Test
  void viewsWork() {
    // expect:
    assertEquals(accountId, subject.toGrpcAccountId());
    assertEquals(contractId, subject.toGrpcContractId());
    assertEquals(tokenId, subject.toGrpcTokenId());
    assertEquals(scheduleId, subject.toGrpcScheduleId());
    assertEquals(merkleId, subject.asMerkle());
  }

  @Test
  void matcherWorks() {
    // setup:
    final var diffShard = IdUtils.asAccount("2.2.3");
    final var diffRealm = IdUtils.asAccount("1.3.3");
    final var diffNum = IdUtils.asAccount("1.2.4");

    assertTrue(subject.matches(subject.toGrpcAccountId()));
    assertFalse(subject.matches(diffShard));
    assertFalse(subject.matches(diffRealm));
    assertFalse(subject.matches(diffNum));
  }
}
