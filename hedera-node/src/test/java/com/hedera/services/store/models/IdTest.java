/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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
        assertNotEquals(aId, new Object());
        assertEquals(aId, aId);
    }

    @Test
    void conversionsWork() {
        final var id = new Id(1, 2, 3);
        final var entityId = new EntityId(1, 2, 3);
        final var grpcAccount = IdUtils.asAccount("1.2.3");
        final var grpcToken = IdUtils.asToken("1.2.3");
        final var contractId =
                ContractID.newBuilder().setShardNum(1).setRealmNum(2).setContractNum(3).build();
        final var address = Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(contractId)));
        final var grpcTopic = IdUtils.asTopic("1.2.3");

        assertEquals(entityId, id.asEntityId());
        assertEquals(grpcAccount, id.asGrpcAccount());
        assertEquals(grpcToken, id.asGrpcToken());
        assertEquals(contractId, id.asGrpcContract());
        assertEquals(address, id.asEvmAddress());
        assertEquals(id, Id.fromGrpcAccount(grpcAccount));
        assertEquals(id, Id.fromGrpcToken(grpcToken));
        assertEquals(id, Id.fromGrpcTopic(grpcTopic));
        assertEquals(id, Id.fromGrpcContract(contractId));
        assertEquals(grpcTopic, id.asGrpcTopic());
    }

    @Test
    void gettersWork() {
        final var id = new Id(11, 22, 33);

        assertEquals(11, id.shard());
        assertEquals(22, id.realm());
        assertEquals(33, id.num());
    }

    @Test
    void toStringWorks() {
        final var id = new Id(4, 5, 6);

        assertEquals("4.5.6", id.toString());
    }

    @Test
    void comparatorWorks() {
        final var a = new Id(0, 0, 1);
        final var b = new Id(1, 0, 0);
        final var c = new Id(0, 1, 0);
        final var l = new ArrayList<Id>();

        l.add(a);
        l.add(b);
        l.add(c);
        l.sort(Id.ID_COMPARATOR);

        assertEquals(List.of(c, b, a), l);
    }
}
