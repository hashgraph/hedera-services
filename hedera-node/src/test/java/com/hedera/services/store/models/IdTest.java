package com.hedera.services.store.models;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.utils.IdUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdTest {
  @Test
  void hashCodeDiscriminates() {
    final var aId = new Id(1, 2, 3);
    final var bId = new Id(0, 2, 3);
    final var cId = new Id(1, 0, 3);
    final var dId = new Id(1, 2, 0);
    final var eId = new Id(1, 2, 3);

    assertNotEquals(bId.hashCode(), aId.hashCode());
    assertNotEquals(cId.hashCode(), aId.hashCode());
    assertNotEquals(dId.hashCode(), aId.hashCode());
    assertEquals(eId.hashCode(), aId.hashCode());
  }

  @Test
  void equalsDiscriminates() {
    final var aId = new Id(1, 2, 3);
    final var bId = new Id(0, 2, 3);
    final var cId = new Id(1, 0, 3);
    final var dId = new Id(1, 2, 0);
    final var eId = new Id(1, 2, 3);

    assertNotEquals(bId, aId);
    assertNotEquals(cId, aId);
    assertNotEquals(dId, aId);
    assertEquals(eId, aId);
    assertNotEquals(aId, null);
    assertNotEquals(aId, new Object());
    assertEquals(aId, aId);
  }

  @Test
  void conversionsWork() {
    final var id = new Id(1, 2, 3);
    final var entityId = new EntityId(1, 2, 3);
    final var merkleEntityId = new MerkleEntityId(1, 2, 3);
    final var grpcAccount = IdUtils.asAccount("1.2.3");
    final var grpcToken = IdUtils.asToken("1.2.3");

    assertEquals(entityId, id.asEntityId());
    assertEquals(merkleEntityId, id.asMerkle());
    assertEquals(grpcAccount, id.asGrpcAccount());
    assertEquals(grpcToken, id.asGrpcToken());
    assertEquals(id, Id.fromGrpcAccount(grpcAccount));
    assertEquals(id, Id.fromGrpcToken(grpcToken));
  }

  @Test
  void gettersWork() {
    final var id = new Id(11, 22, 33);

    assertEquals(11, id.getShard());
    assertEquals(22, id.getRealm());
    assertEquals(33, id.getNum());
  }

  @Test
  void toStringWorks() {
    final var id = new Id(4, 5, 6);

    assertEquals("Id{shard=4, realm=5, num=6}", id.toString());
  }

  @Test
  void comparatorWorks() {
    // given:
    final var a = new Id(0, 0, 1);
    final var b = new Id(1, 0, 0);
    final var c = new Id(0, 1, 0);
    // and:
    final var l = new ArrayList<Id>();

    // when:
    l.add(a);
    l.add(b);
    l.add(c);
    // and:
    l.sort(Id.ID_COMPARATOR);

    // then:
    assertEquals(List.of(c, b, a), l);
  }
}
